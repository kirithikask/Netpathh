# NETPATH

**Intelligent network reliability & route intelligence platform.**

NETPATH monitors network paths, ingests per-path telemetry, classifies path health against
configurable thresholds, keeps current state in Redis, retains history in PostgreSQL, and recommends
a healthier alternative route when a path degrades.

![status](https://img.shields.io/badge/tests-38%20passing-brightgreen)

---

## Problem

Network teams usually find out about a bad path from a customer. Raw telemetry exists on routers,
but it is not turned into a simple question: *which of my paths is unhealthy right now, how bad is
it, and is there a better route I could use?*

## Solution

NETPATH is a modular monolith that answers that question for a defined set of **network paths**
(each path being a monitored flow between two endpoints):

1. A monitoring agent posts a telemetry sample to a path (`latency`, `packetLoss`, `throughput`).
2. The sample is persisted, and the path is re-classified as `HEALTHY`, `DEGRADED`, `DOWN` or
   `UNKNOWN` from configurable thresholds.
3. The new state is written to Redis (hot state) and to the `network_paths.status` column.
4. Operators see the estate on a console: fleet state, per-path telemetry, and history.
5. When a path is degraded, the backend finds alternative paths between the same two endpoints and
   ranks them, returning a recommendation with the reason it is better.
6. A recommendation can be recorded as a **simulated** traffic shift for audit. NETPATH does not
   control routers (see [Known limitations](#known-limitations)).

## Architecture

```
                    React console (Vite + TypeScript)
                              │  REST + JWT
                              ▼
                 Spring Boot 3 · Java 21 (modular monolith)
        controllers → services → repositories
                │                        │
                │ hot state              │ history
                ▼                        ▼
             Redis                    PostgreSQL
      (current path health)      (telemetry, paths, audits)
```

Read path for path health:

```
GET /api/paths/{id}/health
      │
      ▼
   Redis ──── hit ──► return cached classification
      │
      └── miss / unavailable ──► recompute from PostgreSQL telemetry ──► repopulate Redis ──► return
```

## Features

| Area | What exists |
| --- | --- |
| Estate modelling | Applications, endpoints (with region), network paths with primary/backup semantics |
| Telemetry ingestion | `POST /api/paths/{id}/metrics` with Bean Validation; persists and re-evaluates health |
| Health classification | Configurable latency and packet-loss thresholds producing `HEALTHY` / `DEGRADED` / `DOWN` / `UNKNOWN` |
| Historical metrics | Paginated metric history, filterable by `from` / `to` |
| Redis hot state | Current path health cached with TTL, cache-miss rebuild from PostgreSQL, graceful degradation when Redis is unavailable |
| Route intelligence | Alternative-path discovery and ranking by status, then latency, then packet loss |
| Simulated shifts | Persisted audit trail of `SIMULATED` shifts; no device control |
| Dashboard | Fleet counters, path status board, attention queue with live recommendations, SVG topology, telemetry timelines, recent shift events |
| Security | JWT authentication, stateless sessions, public health and OpenAPI endpoints only |
| Observability | Spring Boot Actuator (`health`, `info`, `metrics`) and an API root health endpoint |
| API docs | OpenAPI 3 via springdoc: `/swagger-ui.html`, `/v3/api-docs` |
| Tests | 38 tests: pure health classification, cache behaviour, recommendation ranking, shift validation, and an end-to-end REST integration suite |

## Tech stack

**Backend** Java 21 · Spring Boot 3.3 (Web, Data JPA, Security, Validation, Actuator) · Flyway ·
PostgreSQL · Redis · springdoc-openapi · JUnit 5 + Mockito + MockMvc
**Frontend** React 18 · TypeScript · Vite · React Router · TanStack Query · Axios (no UI or charting
library — the timeline and topology visualisations are hand-built SVG)
**Infrastructure** Docker · Docker Compose · GitHub Actions

---

## Running locally

### Option A — no external services (fastest)

The `dev` profile uses a file-backed H2 database and does not require Redis. Schema is created by
Hibernate; the demo network is seeded on first start.

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173> and sign in with:

```
operator@netpath.io / netpath123
```

Without Redis, path health is recomputed from PostgreSQL on every read and a single warning is
logged. Everything else behaves identically.

### Option B — PostgreSQL and Redis (production-shaped)

```bash
docker compose -f infrastructure/docker-compose.yml up -d     # PostgreSQL 16 + Redis 7
```

```bash
cd backend && mvn spring-boot:run        # default profile: Flyway migrates the schema
cd frontend && npm install && npm run dev
```

### Option C — everything in containers

```bash
docker compose -f infrastructure/docker-compose.full.yml up --build
```

Open <http://localhost:8081>. nginx serves the built console and reverse-proxies `/api` to the
backend.

---

## Demo walkthrough

This is the story the product tells, and each step hits a real endpoint.

1. **Dashboard** — fleet counters (`2 applications · 6 endpoints · 8 paths`), a status board, and a
   topology drawn from the seeded network: `pay-eu-west-1a`, `pay-us-east-1a`, `pay-ap-south-1a`,
   `core-router-eu-1`, plus a second application.
2. **Network paths** — filter by status. The seeded estate intentionally contains all four states.
3. **Open `eu-west-to-ap-south-primary`** — the centrepiece. It is `DEGRADED`: average latency
   `253 ms` and packet loss `2.71 %`. The *Health analysis* panel explains exactly which threshold
   was crossed, using the thresholds fetched from `GET /api/config/path-health`.
4. **Submit telemetry** — with the *Degradation preset* (`latency 250 ms`, `packet loss 8 %`) or with
   your own numbers, then press **Submit sample**. The response reports the new state and sample
   count, the metric grid and timelines update, and the recommendation is refetched.
5. **Read the recommendation** — *Current route* → *Recommended route*
   (`eu-west-to-ap-south-alternative`, `HEALTHY`, `78 ms`, `0.29 %`) with a generated reason such as
   *"Status improvement: DEGRADED → HEALTHY. Latency 174ms lower (78ms vs 253ms)."*
6. **Record the simulated shift** — writes a `SIMULATED` row to `traffic_shift_logs` and shows it in
   the shift history and on the dashboard event feed. The UI states plainly that no device was
   contacted.

> Classification uses the aggregate of **all** telemetry retained for a path. A single bad sample on
> a path with a long healthy history will not flip it — submit a few samples (a real agent reports
> every 30–60 s) or use a path with little history. See
> [Known limitations](#known-limitations).

---

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Exchange credentials for a JWT |
| `POST` | `/api/auth/register` | Create an operator account |
| `GET` | `/api/health` | Public service health |
| `GET` | `/api/config/path-health` | Active thresholds and cache TTL |
| `GET` | `/api/dashboard/stats` | Estate counters and per-status path counts |
| `GET` | `/api/applications` | List applications (with endpoint and path counts) |
| `GET/POST/PUT/DELETE` | `/api/applications`, `/api/applications/{id}` | Application CRUD |
| `GET` | `/api/endpoints` | Paginated endpoints; `?region=` and `/api/endpoints/region/{region}` |
| `GET/POST/PUT/DELETE` | `/api/endpoints`, `/api/endpoints/{id}` | Endpoint CRUD |
| `GET` | `/api/paths` | Paginated paths with telemetry summary; `?status=HEALTHY|DEGRADED|DOWN|UNKNOWN` |
| `GET` | `/api/paths/{id}` | Path detail resolved through the Redis hot state |
| `POST/PUT/DELETE` | `/api/paths`, `/api/paths/{id}` | Path management |
| `POST` | `/api/paths/{id}/metrics` | Ingest a telemetry sample, returns the resulting health |
| `GET` | `/api/paths/{id}/metrics` | Paginated history, `?from=&to=` ISO-8601 instants |
| `GET` | `/api/paths/{id}/metrics/recent` | Most recent samples, `?limit=` |
| `GET` | `/api/paths/{id}/health` | Current classification (Redis-first) |
| `GET` | `/api/paths/{id}/recommendation` | Ranked alternative route with reason |
| `GET` | `/api/paths/{id}/shifts` | Simulated shift audit trail for a path |
| `POST` | `/api/paths/{id}/shift` | Record a simulated shift |
| `GET` | `/api/shifts/recent` | Recent simulated shifts, `?limit=` |

Full request/response shapes and examples: [docs/api.md](docs/api.md).

---

## Testing

```bash
cd backend && mvn test
```

38 tests, runnable with no external services. Classification is pure, so its tests need no mocks;
the integration suite boots the real Spring context against H2 and stubs Redis with an in-memory
stand-in, then walks the demo flow through `MockMvc`:

```
PathHealthServiceTest         10  every classification branch and boundary, no mocks at all
PathHealthCacheServiceTest     6  hit, miss rebuild, Redis-down fallback, invalidation, no domain writes
RouteRecommendationServiceTest 5  status/latency ranking, no-alternative, unknown path
TrafficShiftServiceTest        5  same-endpoint validation, self-shift rejection, persistence
NetpathApiIntegrationTest     12  auth, telemetry, history filters, recommendation, shift, errors
```

```bash
cd frontend && npm run build     # tsc --noEmit equivalent, then vite build
```

---

## Architecture decisions

**Why PostgreSQL** — telemetry is append-only, relational, and queried by time range and path. An
index on `path_metrics(path_id, timestamp DESC)` makes the history endpoint cheap, and Flyway keeps
schema changes reviewable.

**Why Redis** — path health is read far more often than it is written, and it must be identical
across every API instance. It is a cache, never a source of truth: a miss or an outage falls back to
recomputing from PostgreSQL, and repeated failures are logged once per 30 s instead of per request.

**Why a modular monolith** — one deployable with clear package boundaries (`entity`, `repository`,
`service`, `controller`) is the right size for this problem. The telemetry → health → recommendation
flow is a single transaction boundary; splitting it into services would add network hops and
distributed failure modes without removing any real coupling.

**Why REST** — the domain is request/response: read a path, post a sample, ask for a
recommendation. There is no event fan-out to justify a broker, so there is no Kafka here.

**Why JWT with stateless sessions** — the console is a separate origin, and no session state is
needed on the server. Only authentication is solved; authorisation beyond authenticated-versus-public
is deliberately out of scope.

**Why Docker Compose and CI** — Compose gives every developer the same PostgreSQL and Redis without
installing either; CI runs backend tests, the frontend type-check and build, and validates the images,
so a broken build is caught before review.

More detail: [docs/architecture.md](docs/architecture.md) ·
[route recommendation algorithm](docs/route-recommendation.md).

---

## Repository layout

```
netpath/
├── backend/                    Spring Boot application
│   ├── src/main/java/com/netpath/
│   │   ├── config/             security, Redis, OpenAPI, path-health properties, demo seed
│   │   ├── controller/         REST resources
│   │   ├── dto/                request/response models
│   │   ├── entity/             JPA entities and enums
│   │   ├── exception/          typed exceptions + @RestControllerAdvice
│   │   ├── repository/         Spring Data repositories
│   │   ├── security/           JWT filter, token utility, user details
│   │   └── service/            path health, cache, telemetry, recommendation, shifts
│   ├── src/main/resources/
│   │   ├── application.yml     default profile (PostgreSQL + Redis)
│   │   ├── application-dev.yml demo profile (H2, Redis optional)
│   │   └── db/migration/       Flyway migrations
│   └── src/test/java/com/netpath/
├── frontend/                   React console
│   └── src/{api,auth,components,lib,pages}/
├── infrastructure/             Compose files, Dockerfiles, nginx config
├── docs/                       architecture, API, algorithm
└── .github/workflows/ci.yml
```

---

## Configuration

| Property | Default | Meaning |
| --- | --- | --- |
| `app.path-health.thresholds.degraded-latency-ms` | `100` | Average latency at or above which a path is `DEGRADED` |
| `app.path-health.thresholds.down-latency-ms` | `500` | Worst sample at or above which a path is `DOWN` |
| `app.path-health.thresholds.degraded-packet-loss-pct` | `0.5` | Average loss at or above which a path is `DEGRADED` |
| `app.path-health.thresholds.down-packet-loss-pct` | `10.0` | Worst loss at or above which a path is `DOWN` |
| `app.path-health.thresholds.min-metrics-for-evaluation` | `3` | Samples required before classifying (`UNKNOWN` below this) |
| `app.path-health.cache.ttl-seconds` | `120` | Redis hot-state TTL |
| `app.security.jwt.secret` | dev value | HS256 signing key — override per environment |
| `app.demo-data.enabled` | `true` | Seed the demo network when the database has no users |

---

## Known limitations

Stated plainly so nothing here is mistaken for functionality that does not exist:

- **Classification is over all retained telemetry**, not a rolling window. A path with a long healthy
  history resists a single bad sample, which is realistic but means operators should submit a few
  samples to see a transition. A windowed evaluation is the next improvement (see below).
- **There is no metric retention or downsampling job.** `path_metrics` grows without bound; a
  scheduled rollup into hourly buckets belongs here before any long-running deployment.
- **Traffic shifts are recorded, not applied.** No router, SDN controller or cloud API is contacted,
  and the API and UI say so.
- **Redis is not required for correctness.** If it is unavailable, hot state is not shared between
  instances and every read recomputes from PostgreSQL.
- **Authorisation is binary.** Any authenticated operator can mutate any application, endpoint or
  path; there is no per-application permission model.
- **No websockets.** The console polls (15–30 s) rather than pushing telemetry updates.
- **The container images have not been built in this environment** (Docker was unavailable); Compose
  files are schema-validated and CI builds them.
