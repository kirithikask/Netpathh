# NETPATH interview cheat sheet

Short, honest answers about how this system is built and why. Every claim here is implemented in
this repository; nothing is aspirational.

## In one sentence

NETPATH ingests per-path network telemetry, classifies each path's current health from configurable
thresholds, keeps that hot state in Redis over a PostgreSQL source of truth, and recommends a
healthier route when a path degrades - recording the operator's decision as a simulated action.

## Architecture

A **modular monolith**: one Spring Boot application with a React frontend, not microservices. The
load is small, the domain is cohesive, and splitting path health, telemetry and recommendations
across services would have added network hops, distributed transactions and deployment surface for
no benefit. Package boundaries (`controller → service → repository`) keep the concerns separable
without the operational cost.

## Why each technology is here

| Choice | Reason |
| --- | --- |
| Spring Boot | Transactions, validation, security and JPA integration out of the box; the boring, well-understood choice for a REST API with relational data |
| PostgreSQL | Telemetry is relational and time-ordered, and the delete/foreign-key rules are real constraints. Also the source of truth, so correctness cannot depend on a cache |
| Redis | Health is read far more often than telemetry is written. A 120s TTL cache removes repeated aggregation from the read path |
| Flyway | The schema is versioned and reviewed like code. `ddl-auto=validate` makes drift a startup failure instead of a silent production mutation |
| JWT | A stateless API with a single-page client: no server-side session store, and the same token works across instances |
| TanStack Query | Caching, polling, and invalidation-after-mutation without hand-rolling loading and error state per component |
| Docker Compose | Reproducible local PostgreSQL and Redis; not an orchestration story and not pretending to be one |

## Health calculation

One owner, `PathHealthService.classify`, and it is a pure function of an aggregate: no database
access and no clock, so it is unit-tested directly.

- **Window**: only samples inside `app.path-health.window-minutes` (default 60) count toward
  *current* health. History stays in the table for charts. Without this, one bad hour would hold a
  recovered path `DEGRADED` forever.
- **Two outcomes per metric**: the average catches a generally poor path, the maximum catches the
  one sample that made it unreachable.
- **Order**: `UNKNOWN` (too few samples) → `DOWN` (a worst sample crossed the severe boundary) →
  `DEGRADED` (an average crossed the warning boundary) → `HEALTHY`.
- The boundaries are configuration, not constants, and are published by `GET /api/config/path-health`
  so the UI explains a status with the numbers actually in force.

## Recommendation logic

Candidates are the other paths between the same endpoint pair. Ranking is status first, then average
latency, then average packet loss; `UNKNOWN` ranks last because absent data is not evidence of a
better path. A recommendation is produced **only when strictly better**, so the UI never suggests a
pointless change. `GET /api/paths/{id}/recommendation` is a pure read - it writes nothing, which an
integration test pins by asserting row counts are unchanged across repeated calls.

## Caching

Read-through: serve from Redis on a hit, otherwise recompute from PostgreSQL and populate the key.
One key per path (`path:health:{id}`), TTL from configuration. Written on ingestion, invalidated on
delete. Redis never writes domain state - only `PathHealthService` persists a status - so the cache
cannot become a second source of truth. Redis being down degrades to recomputation with one
rate-limited warning per 30 seconds; requests still succeed.

## Transactions

- Telemetry ingestion writes the sample and the recomputed status in **one** transaction, so a
  stored sample can never leave a stale status behind.
- Reads are `@Transactional(readOnly = true)`, and `open-in-view` is disabled: the session does not
  leak past the service layer, which turns lazy-loading mistakes into visible errors instead of N+1
  queries hidden during rendering.
- Deleting an application sequences its own teardown (paths → endpoints → application). Relying on
  ORM cascade ordering failed here, because Hibernate nulls a `NOT NULL` foreign key on already
  managed entities and flushes those updates before any delete.

## The bug worth talking about

Adding an endpoint that paths ran through returned `500` on PostgreSQL. The H2 development profile
built its schema from entity mappings while the Flyway migration declared `ON DELETE CASCADE`; the
two drifted. The fix was to give the mapping the same rule - and the lesson was that a second schema
source is a defect generator. Production and development now both run the real migrations with
`ddl-auto=validate`.

## Docker and CI

`docker-compose.yml` runs PostgreSQL and Redis for local development; `docker-compose.full.yml` runs
the whole stack (nginx + SPA, backend, PostgreSQL, Redis). The backend image is a multi-stage build
(Maven build, JRE runtime, non-root user) that reads all configuration from the environment.

GitHub Actions runs three jobs on every push: backend `mvn verify` (the suite uses an embedded H2
database and a stubbed Redis, so no services are needed), frontend type-check and production build,
and container validation (compose config plus application image builds).

## Deployment

Render blueprint in `render.yaml`: the Spring service from the backend Dockerfile, the frontend as a
static site, Render Postgres, and a Render Key Value instance on the private network. Flyway migrates
the database on every boot. The static site is built with `VITE_API_BASE_URL` pointing at the API,
CORS origins come from `CORS_ALLOWED_ORIGINS`, and `JWT_SECRET` has no default in code, so a deploy
cannot silently ship a known signing key.

## Trade-offs and limits, stated plainly

- **Traffic shifting is simulated.** It records a decision; it does not touch routers, BGP or live
  traffic. Real control plane integration would need device credentials, vendor APIs and a rollback
  policy - a different project.
- **Classification aggregates a fixed window.** A more sophisticated approach would use percentiles
  (p95/p99) or per-hop attribution; a windowed average and max were chosen as the simplest rule that
  is explainable to an operator.
- **The cache is per-instance-tolerant, not distributed-locked.** Two instances may both recompute
  on a miss; that is a wasted query, not a correctness problem, because the value is derived.
- **No alerting or notification pipeline.** Health changes are visible in the console, not pushed.
- **Single role model** (`ADMIN`/`USER`) rather than fine-grained authorisation, which a real
  multi-team deployment would need.
