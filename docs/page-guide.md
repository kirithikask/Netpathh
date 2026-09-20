# NETPATH page guide

Every page in the console, what it is for, where its data comes from, and what a user can do on it.
All data is fetched from the backend API; nothing is hardcoded and no page fabricates numbers.

## Login — `/login`

| | |
| --- | --- |
| Purpose | Authenticate an operator and store the JWT for later requests |
| API | `POST /api/auth/login` |
| Data source | `users` in PostgreSQL (bcrypt password hash) |
| Actions | Sign in; a failed attempt shows the message the API returned |

On success the token is kept in `localStorage` and attached to every later request by the axios
interceptor. A `401` from any endpoint clears the token and returns the operator to this page.
The demo credentials are prefilled **in development builds only**; a production bundle ships an
empty form.

## Dashboard — `/`

| | |
| --- | --- |
| Purpose | Fleet-wide view: how many paths sit in each state, and what needs attention |
| API | `GET /api/dashboard/stats`, `GET /api/paths`, `GET /api/config/path-health`, `GET /api/shifts/recent`, `GET /api/endpoints`, `GET /api/paths/{id}/metrics` |
| Data source | `network_paths.status` and counts for the KPI row; `path_metrics` for the telemetry sparkline; `traffic_shift_logs` for recent shifts |
| Actions | Follow a path from the status board or the attention list; open the topology; navigate to other pages |

Status counters are computed by the backend, not derived in the UI from a partial page of results,
so they stay correct beyond the first page. The page polls every 15 seconds. With no data it shows
"Nothing registered yet" and `—` placeholders rather than zeros presented as measurements; if the
API is unreachable it shows the error instead of stale or invented numbers.

## Network paths — `/paths`

| | |
| --- | --- |
| Purpose | The full path inventory with current health, latency, packet loss and sample count |
| API | `GET /api/paths?page&size&status` |
| Data source | `network_paths` joined to its telemetry summary; one aggregate query per page (no N+1) |
| Actions | Filter by status (`ALL`/`HEALTHY`/`DEGRADED`/`DOWN`/`UNKNOWN`), page through the list, open a path |

Latency, loss and sample counts are the same windowed aggregate the classifier used, so the numbers
beside a status explain it.

## Path detail — `/paths/{id}`

| | |
| --- | --- |
| Purpose | The centrepiece: why this path has this health, and what to do about it |
| API | `GET /api/paths/{id}`, `/health`, `/metrics`, `/recommendation`, `/shifts`, `GET /api/config/path-health` |
| Data source | `network_paths`, `path_metrics` (history), `traffic_shift_logs` (shift history) |
| Actions | Read the flow and current state; inspect latency and packet-loss timelines; read the classification reasoning; submit a telemetry sample; record a simulated shift |

Sections, in order: **flow** (source → hops → destination), **current state** (status, average and
worst latency, average and worst loss, last throughput, in-window samples, age of the newest
sample), **latency and packet-loss history** with the active thresholds drawn on the chart,
**health analysis** (the rule that fired and the boundaries in force, including the telemetry window),
**route recommendation** (current route vs recommended route and the measured reason), **shift
history**, and the **telemetry submission** form.

The recommendation is shown with a reason field prefilled, and **Record simulated shift** writes that
reason to `traffic_shift_logs` with status `SIMULATED`. The page states plainly that NETPATH does not
reconfigure network devices and moves no live traffic. Submitting a telemetry sample calls
`POST /api/paths/{id}/metrics`, which persists the sample, re-classifies the path and refreshes the
Redis hot state, then invalidates the affected queries so the page reflects the new status.

## Endpoints — `/endpoints`

| | |
| --- | --- |
| Purpose | The addressable points a path runs between, grouped by region |
| API | `GET /api/endpoints?page&size`, `GET /api/endpoints/region/{region}`, `GET /api/endpoints/regions` |
| Data source | `endpoints`, scoped to the owning `applications` row |
| Actions | Filter by region, page through the list, see each endpoint's paths and current status |

Region filtering is done by the API, so it applies to the whole estate rather than only the rows
already loaded. `GET /api/endpoints/regions` supplies the filter options.

## Applications — `/applications`

| | |
| --- | --- |
| Purpose | The top of the domain: each application with its endpoints, paths and current health |
| API | `GET /api/applications`, `GET /api/paths/application/{applicationId}` |
| Data source | `applications` with their endpoints and paths from PostgreSQL |
| Actions | Expand an application to inspect its estate and open any path from it |

## Shared layout

Every authenticated page shares a sidebar (navigation with counts, and the fleet-state summary
`healthy · degraded · down · unknown`), a topbar showing the signed-in operator, their role and how
many paths are tracked, and a sign-out control. The fleet-state figures come from
`GET /api/dashboard/stats` on the same 15-second poll, so the summary can never disagree with the
pages it links to.
