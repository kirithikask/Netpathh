# NETPATH

**Intelligent network reliability & route intelligence platform.**

NETPATH monitors network paths, ingests per-path telemetry, classifies path health against
configurable thresholds, keeps current state in Redis, retains history in PostgreSQL, and recommends
a healthier alternative route when a path degrades.

[![CI](https://github.com/kirithikask/Netpathh/actions/workflows/ci.yml/badge.svg)](https://github.com/kirithikask/Netpathh/actions/workflows/ci.yml)

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
| Tests | Unit, HTTP-integration, container-backed (real PostgreSQL + Redis via Testcontainers) and cache-outage suites |

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

The `dev` profile supplies its own development signing key, so no environment variables are needed.

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
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"   # required: there is no built-in default
mvn spring-boot:run                              # Flyway migrates the schema, ddl-auto=validate
```

```bash
cd frontend && npm install && npm run dev
```

The database starts empty and the app refuses to invent an operator for it. To get a usable account,
pick one of:

```bash
# Development fixtures: a fabricated estate you can click through
DEMO_DATA_ENABLED=true mvn spring-boot:run

# Or a single admin account and nothing else (the production path)
BOOTSTRAP_ADMIN_ENABLED=true BOOTSTRAP_ADMIN_EMAIL=you@example.com \
  BOOTSTRAP_ADMIN_PASSWORD='at-least-8-chars' mvn spring-boot:run
```

See [.env.example](.env.example) for the full list of variables.

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
   `196 ms` and packet loss `2.13 %`. The *Health analysis* panel explains exactly which threshold
   was crossed, using the thresholds fetched from `GET /api/config/path-health` — including the
   telemetry window, and the fact that the 18 retained samples narrow to 9 inside that window.
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

Seven suites. Classification is pure, so its tests need no mocks; the HTTP-integration suite boots
the real Spring context against H2 and stubs Redis with an in-memory stand-in, then walks the demo
flow through `MockMvc`:

```
PathHealthServiceTest             every classification branch, threshold boundary and window rule, no mocks at all
PathHealthCacheServiceTest        hit, miss rebuild, Redis-down fallback, invalidation, no domain writes
RouteRecommendationServiceTest    status/latency/loss ranking, no-alternative cases, read-only proof
TrafficShiftServiceTest           same-endpoint validation, self-shift rejection, persistence
NetpathApiIntegrationTest         auth, telemetry, window edges, recommendation, shift, errors, cascades
RedisOutageIntegrationTest        the whole read/write surface with Redis unreachable
NetpathContainerIntegrationTest   the same flows against real PostgreSQL and Redis containers
```

The container suite needs Docker and is skipped automatically where Docker is absent (CI runners
have it, so it executes on every push there). `mvn test` prints the current totals in its surefire
summary.

On Windows with Docker Desktop, Testcontainers is run from a shell that may not carry the engine
endpoint Docker Desktop configures for the CLI. Point it at the engine explicitly if the suite
skips despite a running engine:

```bash
export DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
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
├── docs/                       workflow, page guide, cheat sheet, architecture, API, algorithm
├── render.yaml                 Render Blueprint (not applied)
├── .env.example                Environment variable template
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
| `app.path-health.window-minutes` | `60` | How far back *current* health looks; older telemetry is retained for history but does not classify |
| `app.path-health.thresholds.min-metrics-for-evaluation` | `3` | Samples required in the window before classifying (`UNKNOWN` below this) |
| `app.path-health.cache.ttl-seconds` | `120` | Redis hot-state TTL |
| `app.security.jwt.secret` | *none* | HS256 signing key, at least 32 characters. The application refuses to start without it; only the `dev` profile has a development-only value |
| `app.security.cors.allowed-origins` | `http://localhost:5173` | Comma-separated browser origins allowed to call the API |
| `app.demo-data.enabled` | `false` | Seed a fabricated estate when the database has no users. `dev` profile opt-in only |
| `app.bootstrap-admin.enabled` | `false` | Create the first operator on an empty database, then set this back to `false` |

### Environment variables

Every environment-specific value is read from the environment; nothing secret is committed. Full
template in [.env.example](.env.example).

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | `localhost` `5432` `netpath` `netpath` `netpath` | PostgreSQL connection |
| `REDIS_HOST` `REDIS_PORT` | `localhost` `6379` | Redis connection; absent Redis degrades to recomputation |
| `JWT_SECRET` | *none* | Signing key. No fallback outside the `dev` profile |
| `JWT_EXPIRATION_MS` | `86400000` | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated allowed browser origins |
| `SERVER_PORT` / `PORT` | `8080` | Bind port. `SERVER_PORT` wins when set, then the `PORT` some hosts inject |
| `BOOTSTRAP_ADMIN_ENABLED` `BOOTSTRAP_ADMIN_EMAIL` `BOOTSTRAP_ADMIN_PASSWORD` | `false` | First-operator provisioning |
| `DEMO_DATA_ENABLED` | `false` | Development fixtures. Never enable in production |
| `VITE_API_BASE_URL` | `/api` | Frontend build-time API root, *including* the `/api` segment |

---

## Deployment

`render.yaml` describes the whole stack as a Render Blueprint. **It has not been applied**: no
deployment has been performed or verified from this repository, and the steps below are the intended
path rather than a record of one.

```text
GitHub (main)
      ↓
Render
      ├── netpath-console   React static site   →  VITE_API_BASE_URL baked in at build time
      ├── netpath-api       Spring Boot (Docker) →  Flyway migrates on boot, health check /api/health
      ├── netpath-db        PostgreSQL           source of truth
      └── netpath-cache     Render Key Value     private network, ipAllowList: []
```

Before the first deploy:

1. Set `VITE_API_BASE_URL` on the static site to the API root **including** `/api`, for example
   `https://netpath-api.onrender.com/api`. Never a `localhost` value.
2. Let Render generate `JWT_SECRET` (the app will not start without it).
3. Set `CORS_ALLOWED_ORIGINS` to the static site's origin, for example
   `https://netpath-console.onrender.com`.
4. For the first boot only, set `BOOTSTRAP_ADMIN_ENABLED=true` with `BOOTSTRAP_ADMIN_EMAIL` and
   `BOOTSTRAP_ADMIN_PASSWORD`, confirm you can sign in, then set the flag back to `false`.

Leave `DEMO_DATA_ENABLED=false`: the production database should receive real data through the API.

---

## Documentation

| Document | Contents |
| --- | --- |
| [docs/system-workflow.md](docs/system-workflow.md) | The full request path, telemetry ingestion, health classification, caching, recommendation, simulated shifting, failure behaviour and boot order |
| [docs/page-guide.md](docs/page-guide.md) | Every console page: purpose, API used, data source, user actions |
| [docs/interview-cheat-sheet.md](docs/interview-cheat-sheet.md) | Architecture and design decisions, each technology's reason, the schema-drift bug, trade-offs and limits |
| [docs/architecture.md](docs/architecture.md) | Components, ownership rules, request flows |
| [docs/api.md](docs/api.md) | Endpoint reference and error contract |
| [docs/route-recommendation.md](docs/route-recommendation.md) | Candidate selection and ranking |

---

## Known limitations

Stated plainly so nothing here is mistaken for functionality that does not exist:

- **Classification uses a fixed window, not percentiles.** Current health aggregates the last
  `app.path-health.window-minutes` (default 60) by average and maximum. Percentiles (p95/p99) or
  per-hop attribution would describe tail latency better; a windowed average and max were chosen as
  the simplest rule an operator can read off the screen.
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
