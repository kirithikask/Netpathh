# Route recommendation

## What NETPATH recommends, and what it does not

NETPATH answers one question:

> Given a path between endpoint A and endpoint B, is there another configured path on the same
> endpoint pair that is measurably healthier?

It does **not** compute forwarding tables, speak BGP/OSPF, or reconfigure devices. The output is a
recommendation an engineer can act on, not a control-plane action. Traffic movement is only ever
recorded as a `SIMULATED` audit entry.

## Candidates

```
GET /api/paths/{id}/recommendation
```

1. Load the current path with its endpoints; `404` if it does not exist.
2. Compute the current path's health with `PathHealthService`.
3. Ask `NetworkPathRepository.findAlternativePaths(sourceId, destinationId, excludeId)` for every
   other path that connects the **same two endpoints**. Direction matters: a path from B to A is not
   a candidate for A to B.
4. Compute each candidate's health.
5. Pick the best candidate, then recommend it only if it beats the path currently in use.

## Ranking

Health is compared with `isHealthier(candidate, current)`:

1. **Status first.** Ordering is `HEALTHY < DEGRADED < DOWN < UNKNOWN`. A `HEALTHY` candidate always
   beats a `DEGRADED` one, regardless of numbers.
2. **Then latency.** If both share a status, the lower average latency wins.
3. **Then packet loss.** If latencies tie, the lower average packet loss wins.

`UNKNOWN` ranks last because it is not evidence of a good path — it is absence of evidence. A path
with two samples is not a better place to send production traffic than a measured `DEGRADED` path;
it is simply unmeasured.

Two guards matter in practice:

- Among several candidates, the best one is chosen first — the ranking is not "first candidate wins".
- **A candidate is only recommended if it is strictly better than the current path.** If every
  alternative is worse, the endpoint returns `hasAlternative: false` with
  *"No alternative path is currently healthier than the current path"* instead of suggesting a
  lateral move. If there are no alternatives at all, it says so explicitly.

## Response

```json
{
  "currentPathId": 4,
  "currentPathName": "eu-west-to-ap-south-primary",
  "currentStatus": "DEGRADED",
  "currentAvgLatencyMs": 252.5,
  "currentAvgPacketLossPct": 2.71,
  "sourceEndpoint": "pay-eu-west-1a (10.10.1.11)",
  "destinationEndpoint": "pay-ap-south-1a (10.30.1.11)",
  "recommendedPathId": 5,
  "recommendedPathName": "eu-west-to-ap-south-alternative",
  "recommendedStatus": "HEALTHY",
  "recommendedAvgLatencyMs": 78.11,
  "recommendedAvgPacketLossPct": 0.29,
  "reason": "Status improvement: DEGRADED → HEALTHY. Latency 174ms lower (78ms vs 253ms). Packet loss 2.41% lower (0.29% vs 2.71%)",
  "hasAlternative": true,
  "recommendationTimestamp": "2026-09-19T12:48:12.940Z"
}
```

`reason` is generated, not templated per case: it states a status transition when there is one, then
each metric that actually improved. If nothing improved measurably it says the metrics are
comparable. The console renders this string verbatim.

**Evaluating a recommendation writes nothing.** It is a `GET`, so a dashboard poll, an operator
refresh or a crawler cannot mutate the database, and the same call always returns the same answer
for the same telemetry. Earlier revisions persisted every evaluation to a `route_recommendations`
table that nothing ever read back; that table and its write path were removed in `V3`.

The history worth keeping is what the operator decided, not what the platform suggested: that is
recorded by `POST /api/paths/{id}/shift` in `traffic_shift_logs`, including the reason the operator
confirmed. An integration test pins the read-only contract by comparing row counts across every
table before and after repeated calls.

## Complexity and cost

For `n` alternative paths, the algorithm issues one health computation per candidate. A health
computation is a small aggregate query over `path_metrics` for that path. That is acceptable at the
scale this MVP targets (a handful of paths per endpoint pair).

If the landscape grew, the obvious next step is to reuse the grouped aggregate query already used by
the path list (`summariseForPaths`) so all candidates are evaluated in a single round trip, then
cache recommendations per endpoint pair with a short TTL, invalidated whenever telemetry changes
either path.

## Simulated traffic shifting

`POST /api/paths/{id}/shift` is intentionally separate from the recommendation:

- It requires an explicit `recommendedPathId` and a `reason`.
- The target must connect the **same source and destination endpoints** as the current path, and
  cannot be the path itself.
- It writes a `traffic_shift_logs` row with status `SIMULATED`, an `old_path_id` and a `new_path_id`,
  and returns what it recorded.

The name, the stored status, and the console copy all say the same thing: a shift was *recorded*, no
network device was contacted.
