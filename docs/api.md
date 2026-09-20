# NETPATH API reference

Base path: `/api`. All responses are JSON. Except for the endpoints marked *public*, every request
requires `Authorization: Bearer <jwt>`.

Interactive documentation is generated from the code at `/swagger-ui.html`; the raw document is at
`/v3/api-docs`. This file is the quick, hand-written reference.

## Errors

Every failure returns the same shape:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "NetworkPath not found with id: 999",
  "path": "/api/paths/999"
}
```

| Status | When |
| --- | --- |
| `400` | Bad request: a field failed validation (the message lists every violated field), a parameter cannot be parsed (`?status=BOGUS` names the accepted values, `?size=0` the allowed range), an unreadable body, an inverted `from`/`to` window, or a request that is invalid on its merits such as shifting a path to itself |
| `401` | Missing, malformed or expired token; bad credentials on login |
| `404` | The requested resource does not exist |
| `405` | Wrong HTTP method for a known route |
| `415` | Body sent with a media type the endpoint does not consume |
| `500` | Unexpected server error (generic message; no stack traces) |

`400`-class conditions are decided by Spring's own exception hierarchy (`ResponseEntityExceptionHandler`)
and only the body shape is NETPATH's, so a mistyped parameter can never be reported as a `500`.

### Pagination and limits

List endpoints take `page` (default `0`) and `size` (default `20`/`25`/`50` depending on the endpoint);
`size` must be between 1 and 100. `GET /api/paths/{id}/metrics/recent` takes `limit` (1–100) and
`GET /api/shifts/recent` takes `limit` (1–50). Out-of-range values return `400` rather than being
silently clamped, so a client cannot believe it received a full page it did not.

`GET /api/endpoints` also takes `sortBy` and `sortDir`. `sortBy` is checked against the endpoint's own
properties (`id`, `name`, `ipAddress`, `region`, `status`, `createdAt`, `updatedAt`); anything else
returns `400` instead of reaching the query.

## Authentication

### `POST /api/auth/login` — *public*

```json
{ "email": "operator@netpath.io", "password": "netpath123" }
```

```json
{ "token": "eyJhbGc...", "email": "operator@netpath.io", "name": "NETPATH Operator", "role": "ADMIN" }
```

### `POST /api/auth/register` — *public*

```json
{ "email": "new@example.com", "password": "secret123", "name": "New Operator" }
```

Returns the same payload as login, and already authenticated.

## Service

### `GET /api/health` — *public*

```json
{ "status": "UP", "service": "NETPATH", "timestamp": "2026-09-19T12:46:18.287Z" }
```

### `GET /api/config/path-health`

Publishes the thresholds in force so clients can explain classifications without hard-coding them.

```json
{
  "degradedLatencyMs": 100,
  "downLatencyMs": 500,
  "degradedPacketLossPct": 0.5,
  "downPacketLossPct": 10.0,
  "minMetricsForEvaluation": 3,
  "cacheTtlSeconds": 120
}
```

A path is `DEGRADED` at or above a `degraded*` boundary and `DOWN` at or above a `down*` boundary;
between them the classification is the more severe of the two metrics.

### `GET /api/dashboard/stats`

```json
{
  "totalApplications": 2,
  "totalEndpoints": 6,
  "totalPaths": 8,
  "healthyPaths": 3,
  "degradedPaths": 3,
  "downPaths": 1,
  "unknownPaths": 1
}
```

Counts come from stored status, not a live recalculation, so this stays a handful of indexed
aggregates.

## Applications

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/applications` | Returns a plain array with `endpointCount` and `pathCount` per application |
| `GET` | `/api/applications/{id}` | Single application |
| `GET` | `/api/applications/{id}/details` | Same, loaded with its paths |
| `POST` | `/api/applications` | Owner is taken from the authenticated principal |
| `PUT` | `/api/applications/{id}` | Partial update |
| `DELETE` | `/api/applications/{id}` | Cascades to endpoints and paths |

```json
{ "name": "Payments Platform", "description": "Card authorization traffic", "status": "ACTIVE" }
```

## Endpoints

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/endpoints?page=0&size=25&sortBy=createdAt&sortDir=desc` | Paginated |
| `GET` | `/api/endpoints/{id}` | Single endpoint |
| `GET` | `/api/endpoints/application/{applicationId}` | Scoped to an application |
| `GET` | `/api/endpoints/region/{region}` | Server-side region filter |
| `GET` | `/api/endpoints/application/{applicationId}/region/{region}` | Both filters |
| `GET` | `/api/endpoints/regions` | Distinct regions present in the estate |
| `POST` | `/api/endpoints?applicationId={id}` | Create |
| `PUT` | `/api/endpoints/{id}` | Update |
| `DELETE` | `/api/endpoints/{id}` | Delete; paths that ran through it go with it |

```json
{ "name": "pay-eu-west-1a", "ipAddress": "10.10.1.11", "region": "eu-west-1" }
```

`ipAddress` and `region` are required. Latency, packet loss and throughput are attributes of a
**path**, so they are not part of the endpoint contract or the endpoint response.

## Network paths

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/paths?page=0&size=20&status=DEGRADED` | Paginated, newest first, with telemetry summary |
| `GET` | `/api/paths/{id}` | Detail resolved through the Redis hot state |
| `GET` | `/api/paths/application/{applicationId}` | Scoped to an application |
| `POST` | `/api/paths?applicationId={id}` | Create; one primary path per endpoint pair |
| `PUT` | `/api/paths/{id}` | Update |
| `DELETE` | `/api/paths/{id}` | Delete, then invalidate the cache entry |

```json
{
  "pathName": "eu-west-to-us-east-primary",
  "sourceEndpointId": 1,
  "destinationEndpointId": 2,
  "hops": 2,
  "isPrimary": true,
  "description": "Direct fiber backbone"
}
```

Deleting a path also removes its telemetry history and its Redis entry, and does not fail because
recommendations or shift records mention it: those references are cleared, and the shift records
themselves survive (a shift log outlives the paths it names).

A path response carries the status plus the telemetry summary, so a list needs one page query and one
grouped aggregate rather than a query per row:

```json
{
  "id": 4,
  "pathName": "eu-west-to-ap-south-primary",
  "status": "DEGRADED",
  "hops": 3,
  "isPrimary": true,
  "sourceEndpointName": "pay-eu-west-1a",
  "sourceEndpointIp": "10.10.1.11",
  "sourceEndpointRegion": "eu-west-1",
  "destinationEndpointName": "pay-ap-south-1a",
  "destinationEndpointIp": "10.30.1.11",
  "destinationEndpointRegion": "ap-south-1",
  "averageLatencyMs": 253.0,
  "averagePacketLossPct": 2.71,
  "metricsCount": 18,
  "lastUpdated": "2026-09-19T12:44:52Z"
}
```

## Telemetry

### `POST /api/paths/{id}/metrics`

```json
{ "latencyMs": 250, "packetLossPct": 8.0, "throughputMbps": 450.0 }
```

`latencyMs` and `packetLossPct` are required; `latencyMs` is `0..100000`, `packetLossPct` is
`0..100`, `throughputMbps` is optional and non-negative. The sample is persisted, the path is
re-evaluated, and the resulting health is returned:

```json
{
  "pathId": 4,
  "status": "DEGRADED",
  "averageLatencyMs": 252.0,
  "averagePacketLossPct": 2.98,
  "maxLatencyMs": 342.0,
  "maxPacketLossPct": 8.0,
  "metricsCount": 19,
  "lastUpdated": "2026-09-19T12:55:01.204Z"
}
```

### `GET /api/paths/{id}/metrics?page=0&size=50&from=2026-09-19T00:00:00Z&to=2026-09-19T12:00:00Z`

Paginated history, newest first. `from` and `to` are optional ISO-8601 instants; supplying only one
defaults the other. `400` if `from` is after `to`.

```json
{
  "content": [
    {
      "id": 152,
      "pathId": 4,
      "latencyMs": 201,
      "packetLossPct": 2.14,
      "throughputMbps": 437.42,
      "timestamp": "2026-09-19T12:40:52Z"
    }
  ],
  "totalElements": 18,
  "totalPages": 1,
  "number": 0,
  "size": 50,
  "first": true,
  "last": true
}
```

### `GET /api/paths/{id}/metrics/recent?limit=20`

A plain array of the most recent samples (`limit` between 1 and 100). Used for the console's timelines.

### `GET /api/paths/{id}/health`

Current classification, served from Redis when possible. Below
`min-metrics-for-evaluation` samples the status is `UNKNOWN` and the averages are `null`:

```json
{
  "pathId": 7,
  "status": "UNKNOWN",
  "averageLatencyMs": null,
  "averagePacketLossPct": null,
  "maxLatencyMs": null,
  "maxPacketLossPct": null,
  "metricsCount": 0,
  "lastUpdated": "2026-09-19T12:52:31.881Z"
}
```

## Route intelligence

### `GET /api/paths/{id}/recommendation`

Returns the ranked alternative, or `hasAlternative: false` with an explanation. See
[route-recommendation.md](route-recommendation.md) for the ranking rules.

### `POST /api/paths/{id}/shift`

```json
{ "reason": "Degraded primary link", "recommendedPathId": 5 }
```

```json
{
  "id": 1,
  "pathId": 4,
  "oldPathId": 4,
  "oldPathName": "eu-west-to-ap-south-primary",
  "newPathId": 5,
  "newPathName": "eu-west-to-ap-south-alternative",
  "reason": "Degraded primary link",
  "status": "SIMULATED",
  "shiftedAt": "2026-09-19T12:48:28.256Z"
}
```

Requires a target path on the same endpoint pair. Records an audit row only; no network device is
contacted.

### `GET /api/paths/{id}/shifts` and `GET /api/shifts/recent?limit=10`

Audit trail, newest first, as an array of the shape above.
