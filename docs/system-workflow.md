# NETPATH system workflow

How a request travels through NETPATH, and where each piece of state lives.

## The stack

```text
Browser (React + TypeScript, Vite)
    │  axios, JWT in the Authorization header
    ▼
Spring Boot REST API (Java 21)
    │  controller → service → repository
    ├─────────────► PostgreSQL   source of truth: applications, endpoints, paths, telemetry, shifts
    └─────────────► Redis        cache only: current path health, TTL 120s
```

PostgreSQL is authoritative. Redis holds a copy of a classification that can always be recomputed
from PostgreSQL, which is why losing Redis degrades performance and never correctness.

## Request path in detail

```text
UI action
  → TanStack Query hook (frontend/src/api/queries.ts)
  → axios instance (frontend/src/api/client.ts) attaches the bearer token
  → Spring Security filter chain verifies the JWT
  → Controller validates and shapes input
  → Service applies the business rule inside one transaction
  → Repository reads or writes PostgreSQL
  → DTO is serialized (or an ApiError envelope for any failure)
  → TanStack Query caches the result under a named key and the component renders it
```

Every endpoint follows the same four layers, so a change to one rule lands in one service. The
packages are described in [architecture.md](architecture.md).

## Telemetry ingestion

```text
POST /api/paths/{id}/metrics            { latencyMs, packetLossPct, throughputMbps }
```

1. `TelemetryService.ingest` loads the path; an unknown id is a `404`.
2. The sample is written to `path_metrics` with a `timestamp`.
3. `PathHealthService.evaluateAndPersistStatus` re-classifies the path and writes
   `network_paths.status`. This service is the **only** writer of that column.
4. The new classification is handed to `PathHealthCacheService.put`, which stores it in Redis.
5. The same classification is returned, so the caller sees the effect immediately.

The sample and the status change land in one transaction: telemetry cannot be stored while leaving
the path's status stale.

## Health classification

One rule, one owner (`PathHealthService.classify`). It is pure - no database, no clock - so it is
covered by direct unit tests.

**Window.** Current health is computed from samples inside `app.path-health.window-minutes`
(default 60). Telemetry is retained indefinitely for history and charts; only the aggregation that
produces *current* health is windowed. Without a window, one bad hour last month would hold a
recovered path `DEGRADED` forever, and a path that stopped reporting would keep its last verdict
instead of becoming `UNKNOWN`.

**Boundaries.** Configured under `app.path-health.thresholds` and published at
`GET /api/config/path-health`, so the console renders the numbers actually in force:

| Metric | DEGRADED at | DOWN at |
| --- | --- | --- |
| Average latency | ≥ 100 ms | - |
| Average packet loss | ≥ 0.5 % | - |
| Worst latency | - | ≥ 500 ms |
| Worst packet loss | - | ≥ 10 % |

**Rule.**

```text
fewer than min-metrics-for-evaluation samples in the window   → UNKNOWN
worst latency or worst loss at/above a "down" boundary        → DOWN
average latency or average loss at/above a "degraded" boundary → DEGRADED
otherwise                                                      → HEALTHY
```

Averages describe the path overall; worst values catch the single unreachable sample that an
average would hide. A path with no telemetry in the window produces no aggregate row at all, which
is what makes it `UNKNOWN` rather than silently `HEALTHY`.

## Redis caching

```text
GET /api/paths/{id}/health
      │
      ▼
PathHealthCacheService.get
      ├── hit  → return the cached PathHealthResponseDto
      └── miss → PathHealthService.evaluate  →  Redis.set(key, value, TTL 120s)  →  return
```

- One key per path: `path:health:{id}`, TTL `app.path-health.cache.ttl-seconds` (120 s).
- The cache service never writes domain state. Persisting a status belongs to `PathHealthService`.
- Invalidation is explicit: ingestion overwrites the key, and deleting a path removes it.
- If Redis is unreachable, the read falls back to recomputation from PostgreSQL and logs one warning
  per 30 seconds with a suppressed-failure count, so an outage cannot flood the log or fail a
  request. The API stays correct, only the shared hot state is lost.

## Route recommendation

```text
GET /api/paths/{id}/recommendation
```

1. Load the path and evaluate its current health.
2. Load every other path between the same source and destination endpoints.
3. Rank candidates by status first (`HEALTHY` < `DEGRADED` < `DOWN` < `UNKNOWN`), then by average
   latency, then by average packet loss. `UNKNOWN` ranks last: missing data is not evidence of a
   better path.
4. Recommend the best candidate **only if it is strictly better** than the current path, and explain
   the improvement ("Status improvement: DEGRADED → HEALTHY. Latency 135ms lower…").

This is a `GET`, so it is read-only: it computes a recommendation and writes nothing. Repeated calls
leave the database unchanged, which an integration test asserts by comparing row counts across
tables before and after. Ranking detail is in [route-recommendation.md](route-recommendation.md).

## Simulated traffic shifting

```text
POST /api/paths/{id}/shift   { reason, recommendedPathId }
```

Records an operator's decision in `traffic_shift_logs`: the path inspected, the current path, the
target path, the reason, the timestamp, and status `SIMULATED`. `GET /api/paths/{id}/shifts` and
`GET /api/shifts/recent` read that history back.

**NETPATH does not control network devices.** No router, BGP session or interface is touched and no
live traffic is moved. The endpoint exists to model the decision and give it an audit trail, which
the console states next to the button.

## Failure behaviour

| Failure | What happens |
| --- | --- |
| Redis down | Health is recomputed from PostgreSQL; one warning per 30s; requests still succeed |
| PostgreSQL down | Requests fail with a structured `500`; nothing is served from Redis alone |
| No telemetry in the window | `UNKNOWN`, and the UI explains that too few samples arrived |
| Nothing seeded | Every list returns an empty page and the UI shows an empty state, not zeros-as-data |
| Invalid input | `400`/`404`/`405`/`415` with the shared `ApiError` body, never a stack trace |

## Startup sequence

Order matters on boot:

```text
1. Flyway        applies db/migration/V*.sql
2. Hibernate     ddl-auto=validate - fails fast if entities and schema disagree
3. DemoDataInitializer      (order 1, off by default)  seeds a fabricated estate
4. BootstrapAdminInitializer (order 2, off by default) creates the first operator on an empty database
```

Steps 3 and 4 both require an empty `users` table, so they never touch a database that already has
operators. Demo data is for development only; the bootstrap admin creates an account and nothing
else, which is what makes a freshly deployed production database usable.
