# NETPATH architecture

NETPATH is a modular monolith: one deployable Spring Boot application, one React console, and two
data stores with clearly separated responsibilities.

```
React (Vite)  ──REST/JWT──►  Spring Boot  ──JPA──►  PostgreSQL   (history, source of truth)
                                  │
                                  └──RedisTemplate──► Redis       (current path health, hot state)
```

## Components

### Backend packages

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP surface. Validates input (`@Valid`), maps to services, returns DTOs. No business logic. |
| `service` | Business logic, one owner per concern — see the ownership table below. |
| `repository` | Spring Data JPA interfaces. Exactly one aggregate query exists (`PathMetricRepository.summariseForPaths`). |
| `entity` | JPA entities. `NetworkPath` owns `PathMetric`; statuses are enums stored as strings. |
| `dto` | Request and response models. Entities are never serialised directly. |
| `config` | Security, Redis, OpenAPI, path-health properties, demo seed. |
| `security` | Stateless JWT filter, token utility, `UserDetailsService`. |
| `exception` | Typed exceptions (`ResourceNotFound`, `BadRequest`, `Unauthorized`) and a `@RestControllerAdvice` that maps them to a consistent `ApiError`. |

### Data stores

**PostgreSQL** holds everything durable: `users`, `applications`, `endpoints`, `network_paths`,
`path_metrics`, `route_recommendations`, `traffic_shift_logs`. Flyway (`V1__initial_schema.sql`) owns
the schema; `ddl-auto` is `validate` outside the demo profile so drift fails fast at startup.

The demo profile instead lets Hibernate generate the schema, so any rule that lives only in a
migration does not exist there. Foreign-key behaviour is therefore declared on the entities too
(`@OnDelete` on the referencing associations): deleting a path cascades to its `path_metrics`,
recommendations and shift records, and the nullable `recommended_path_id` / `old_path_id` /
`new_path_id` references are cleared rather than blocking the delete. Adding a column or constraint
to `V1__initial_schema.sql` means mirroring it in the mapping, or the demo profile will diverge.

Deleting an endpoint takes the paths that ran through it, because a path cannot exist without both
ends; deleting an application takes its endpoints and paths. Only the first of those is database
cascade: `ApplicationService.deleteApplication` removes paths, then endpoints, then the application,
explicitly. Left to the ORM, Hibernate would load each path, blank the endpoint columns of the paths
it is about to delete, and flush those rows before issuing any delete — and both endpoint columns are
`NOT NULL`, so the transaction could only fail. Deletions that happen through a cascade do not
invalidate the affected Redis keys; those reads all require the path to exist and return `404` before
the cache is consulted, and the keys expire on their own TTL.

Notable indexes:

- `path_metrics(path_id, timestamp DESC)` — the history endpoint and cache-miss recomputation.
- `network_paths(status)` — dashboard counters and the status filter.
- `network_paths(source_endpoint_id, destination_endpoint_id)` — alternative-path lookup.

**Redis** holds one key per path, `path:health:{id}`, whose value is the serialised
`PathHealthResponseDto` with a 120 s TTL. It is a cache with a deliberately narrow job: serve the
"what is the state of this path right now" question without touching PostgreSQL.

## Ownership

Every piece of state has one owner. This is the map to read before adding behaviour:

| State / concern | Owner | Rule |
| --- | --- | --- |
| Classification rule and thresholds | `PathHealthService` | `classify(PathTelemetrySummary)` is pure: no database, no clock. |
| `network_paths.status` column | `PathHealthService` | Written only by `evaluateAndPersistStatus`, and only after telemetry arrives. |
| `path:health:{id}` in Redis | `PathHealthCacheService` | Adapter plus read-through policy. Never writes domain state. |
| Telemetry samples | `TelemetryService` | The only place that ingests or reads history. |
| Path resource and its DTO | `NetworkPathService` | CRUD and composition. Does not classify and does not ingest. |
| Telemetry numbers for any purpose | `PathTelemetrySummary` | One shape, built by one query, used by classification, lists and recommendations. |

Data flows one way: `TelemetryService` writes a sample → `PathHealthService` classifies and stores the
status → `PathHealthCacheService` refreshes the hot state. Reads never write.

### Where new code goes

- A new **classification input** (jitter, retransmits): add it to `PathTelemetrySummary`, to the one
  aggregate query, and to `PathHealthService.classify`. Nothing else needs to change.
- A new **telemetry operation**: `TelemetryService`.
- A new **path attribute or endpoint**: `NetworkPathService` / `EndpointService` plus the DTO.
- A new **cached view of health**: `PathHealthCacheService`, keeping the key prefix and TTL policy.
- Anything that needs *several* owners coordinated: put the sequence in a service that calls them,
  as `TelemetryService.ingest` does — do not let one owner reach into another's state.

## Request flows

### Reading path health (`GET /api/paths/{id}/health`)

```
controller → NetworkPathService.getHealth
                → PathHealthCacheService.get
                     ├─ Redis GET         hit  → return cached DTO
                     ├─ Redis GET         miss → PathHealthService.evaluate (one aggregate query)
                     │                          → Redis SET with TTL → return
                     └─ Redis unavailable     → evaluate from PostgreSQL → return
```

List and dashboard queries read the stored `network_paths.status` column instead, which keeps a
20-row page to two queries (one page query plus one grouped aggregate) rather than twenty health
recalculations.

### Ingesting telemetry (`POST /api/paths/{id}/metrics`)

```
controller (Bean Validation)
   → TelemetryService.ingest                       [@Transactional]
        1. resolve the path, 404 if unknown
        2. persist the PathMetric
        3. PathHealthService.evaluateAndPersistStatus
             → summarise the telemetry (one aggregate query)
             → classify, then write network_paths.status
        4. PathHealthCacheService.put                  (best effort)
        5. return the resulting PathHealthResponseDto
```

Steps 2–3 share a transaction, so the stored status and the persisted sample cannot diverge. The
cache refresh happens after; a Redis failure is logged (throttled) and does not fail the request.

### Recommending a route (`GET /api/paths/{id}/recommendation`)

```
resolve path → PathHealthService.evaluate → find alternative paths on the same endpoint pair
             → evaluate each alternative → rank → persist the recommendation
             → return current route, recommended route, and the reason
```

See [route-recommendation.md](route-recommendation.md).

## Failure modes

| Failure | Behaviour |
| --- | --- |
| Redis unavailable | Reads recompute from PostgreSQL; class updates still persist. One warning per 30 s, with a suppressed-failure count. |
| PostgreSQL unavailable | Requests fail with `500` and a generic message; no stack traces reach clients. |
| Path not found | `404` with `ApiError {status, error, message, path}`. |
| Invalid telemetry | `400` with the violated field messages joined. |
| Missing or expired JWT | `401` from `HttpStatusEntryPoint`; the console clears the token and returns to `/login`. |
| Unknown path id for recommendation | `404` (the service returns `null` and the controller maps it). |

## Security model

- `POST /api/auth/**`, `GET /api/health`, the OpenAPI paths and actuator `health`/`info` are public.
- Everything else requires a bearer token: a stateless `OncePerRequestFilter` validates the signature
  and expiration, loads the user, and populates the `SecurityContext`.
- Passwords are BCrypt hashes. CORS allows the Vite dev origins with credentials.
- There is no role-based authorisation beyond `ADMIN`/`USER` being recorded; any authenticated
  operator can mutate the estate. This is a deliberate line for an MVP.

## Frontend structure

- `api/` — axios instance (token injection, 401 handling, error normalisation), typed endpoint
  functions, and TypeScript types mirroring the backend DTOs.
- `auth/` — context holding the JWT and operator profile; `RequireAuth` guards every route but
  `/login`.
- `components/` — the shell (`Layout`), status primitives, the hand-built SVG `Timeline` and
  `TopologyView`, and reusable tables.
- `pages/` — Dashboard, Network paths, Path detail, Endpoints, Applications.
- `lib/format.ts` — one place for machine-data formatting (latency, loss, throughput, relative time)
  and the severity ordering used to rank attention.

State lives in TanStack Query: server data is cached, invalidated after mutations, and polled every
15–30 s where freshness matters.

## Why these boundaries

- The classification rule lives in exactly one class and is a pure function of a summary plus
  configuration, so it is tested with no mocks and no database.
- `PathHealthCacheService` deliberately knows nothing about entities. When it used to persist the
  status column, a cache failure could suppress a domain write; now a Redis outage costs only the
  cache.
- Monitoring ingestion and path CRUD are separate services because they have different failure
  profiles: CRUD must be transactional and strict, ingestion must be resilient to a cache outage.
- The console never invents data. Every figure on screen comes from an endpoint, and the
  classification explanation is derived from the aggregate values the backend returned, rendered
  against the boundaries fetched from `/api/config/path-health`.
