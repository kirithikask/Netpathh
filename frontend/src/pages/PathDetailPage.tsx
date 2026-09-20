import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Panel } from '../components/Panel';
import { PathFlow } from '../components/PathFlow';
import { StatusPill } from '../components/StatusPill';
import { Timeline } from '../components/Timeline';
import { HealthAnalysis } from '../components/HealthAnalysis';
import {
  fetchPath,
  fetchPathHealth,
  fetchPathHealthConfig,
  fetchPathMetrics,
  fetchRecommendation,
  fetchShiftHistory,
  simulateShift,
  submitTelemetry,
} from '../api/queries';
import { describeError } from '../api/client';
import { fmtLatency, fmtLoss, fmtNumber, fmtRelative, fmtThroughput, fmtTimestamp, shortId } from '../lib/format';

export function PathDetailPage() {
  const params = useParams();
  const pathId = Number(params.id);
  const queryClient = useQueryClient();

  const [latency, setLatency] = useState('250');
  const [loss, setLoss] = useState('8');
  const [throughput, setThroughput] = useState('450');
  const [sampleNotice, setSampleNotice] = useState<string | null>(null);

  const [shiftReason, setShiftReason] = useState('');
  const [shiftNotice, setShiftNotice] = useState<string | null>(null);

  const path = useQuery({ queryKey: ['path', pathId], queryFn: () => fetchPath(pathId) });
  const health = useQuery({ queryKey: ['path-health', pathId], queryFn: () => fetchPathHealth(pathId) });
  const metrics = useQuery({ queryKey: ['metrics', pathId], queryFn: () => fetchPathMetrics(pathId, 40) });
  const config = useQuery({
    queryKey: ['path-health-config'],
    queryFn: fetchPathHealthConfig,
    staleTime: Infinity,
  });
  const recommendation = useQuery({
    queryKey: ['recommendation', pathId],
    queryFn: () => fetchRecommendation(pathId),
  });
  const shifts = useQuery({
    queryKey: ['shifts', pathId],
    queryFn: () => fetchShiftHistory(pathId),
  });

  const sample = useMutation({
    mutationFn: () =>
      submitTelemetry(pathId, {
        latencyMs: Number(latency),
        packetLossPct: Number(loss),
        throughputMbps: throughput === '' ? null : Number(throughput),
      }),
    onSuccess: (result) => {
      setSampleNotice(
        `Accepted. Path re-evaluated to ${result.status} from ${result.metricsCount} samples; Redis hot state refreshed.`,
      );
      void queryClient.invalidateQueries({ queryKey: ['path', pathId] });
      void queryClient.invalidateQueries({ queryKey: ['path-health', pathId] });
      void queryClient.invalidateQueries({ queryKey: ['metrics', pathId] });
      void queryClient.invalidateQueries({ queryKey: ['recommendation', pathId] });
      void queryClient.invalidateQueries({ queryKey: ['paths'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard-stats'] });
    },
    onError: (error) => setSampleNotice(describeError(error)),
  });

  const shift = useMutation({
    mutationFn: () =>
      simulateShift(pathId, {
        reason: shiftReason.trim() || recommendation.data?.reason || 'Operator-initiated simulation',
        recommendedPathId: recommendation.data!.recommendedPathId!,
      }),
    onSuccess: (result) => {
      setShiftNotice(
        `Simulated shift recorded (#${result.id}): ${result.oldPathName} → ${result.newPathName}. No router was contacted.`,
      );
      void queryClient.invalidateQueries({ queryKey: ['shifts', pathId] });
      void queryClient.invalidateQueries({ queryKey: ['shifts', 'recent'] });
    },
    onError: (error) => setShiftNotice(describeError(error)),
  });

  if (Number.isNaN(pathId)) {
    return (
      <main className="page">
        <div className="notice" data-tone="error">Invalid path id in the URL.</div>
      </main>
    );
  }

  if (path.isLoading) {
    return (
      <main className="page">
        <div className="empty"><span className="spinner" /></div>
      </main>
    );
  }

  if (path.isError || !path.data) {
    return (
      <main className="page">
        <div className="notice" data-tone="error">{describeError(path.error)}</div>
        <Link to="/paths" className="mono tiny" style={{ color: 'var(--accent)' }}>← back to paths</Link>
      </main>
    );
  }

  const detail = path.data;
  const currentHealth = health.data;
  const samples = [...(metrics.data?.content ?? [])].reverse();

  const latencyPoints = samples.map((entry) => ({ timestamp: entry.timestamp, value: entry.latencyMs }));
  const lossPoints = samples.map((entry) => ({ timestamp: entry.timestamp, value: entry.packetLossPct }));

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <div className="row tiny faint mono">
            <Link to="/paths" style={{ color: 'var(--accent)' }}>paths</Link>
            <span>/</span>
            <span>{shortId(detail.id)}</span>
          </div>
          <h1 className="page-title" style={{ marginTop: 6 }}>{detail.pathName}</h1>
          <p className="page-sub">
            {detail.description ?? 'No description recorded'}
            {detail.applicationName ? ` · ${detail.applicationName}` : ''}
            {detail.isPrimary === false ? ' · non-primary (standby) path' : ''}
          </p>
        </div>
        <div className="row">
          <StatusPill status={detail.status} chip keyed={`detail-${detail.status}`} />
          <span className="mono tiny faint">
            {currentHealth ? `evaluated from ${currentHealth.metricsCount} samples` : 'evaluating…'}
          </span>
        </div>
      </div>

      <Panel title="Flow" flush>
        <PathFlow path={detail} linkStatus={detail.status} />
      </Panel>

      <div className="panel">
        <div className="metric-grid">
          <div className="metric">
            <div className="metric-value">
              {fmtLatency(currentHealth?.averageLatencyMs ?? detail.averageLatencyMs)}
            </div>
            <div className="metric-label">Avg latency</div>
          </div>
          <div className="metric">
            <div className="metric-value">
              {fmtLatency(currentHealth?.maxLatencyMs ?? null)}
            </div>
            <div className="metric-label">Worst latency</div>
          </div>
          <div className="metric">
            <div className="metric-value">
              {fmtLoss(currentHealth?.averagePacketLossPct ?? detail.averagePacketLossPct)}
            </div>
            <div className="metric-label">Avg packet loss</div>
          </div>
          <div className="metric">
            <div className="metric-value">{fmtLoss(currentHealth?.maxPacketLossPct ?? null)}</div>
            <div className="metric-label">Worst loss</div>
          </div>
          <div className="metric">
            <div className="metric-value">{fmtThroughput(samples.at(-1)?.throughputMbps ?? null)}</div>
            <div className="metric-label">Last throughput</div>
          </div>
          <div className="metric">
            <div className="metric-value">
              {fmtNumber(currentHealth?.metricsCount ?? detail.metricsCount ?? 0)}
            </div>
            <div className="metric-label">Samples</div>
          </div>
          <div className="metric">
            <div className="metric-value">{fmtNumber(detail.hops ?? 1)}</div>
            <div className="metric-label">Hops</div>
          </div>
          <div className="metric">
            <div className="metric-value" style={{ fontSize: 12 }}>
              {currentHealth ? fmtRelative(currentHealth.lastUpdated) : '—'}
            </div>
            <div className="metric-label">Last sample</div>
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="stack">
          <Timeline
            title="Latency history"
            unit="ms"
            color="var(--status-degraded)"
            points={latencyPoints}
            warning={config.data?.degradedLatencyMs}
            severe={config.data?.downLatencyMs}
          />
          <Timeline
            title="Packet loss history"
            unit="%"
            color="var(--status-down)"
            digits={2}
            points={lossPoints}
            warning={config.data?.degradedPacketLossPct}
            severe={config.data?.downPacketLossPct}
          />
        </div>

        <Panel title="Health analysis">
          {currentHealth ? (
            <HealthAnalysis health={currentHealth} config={config.data} />
          ) : (
            <div className="empty">Health is still being computed</div>
          )}
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title="Route recommendation">
          {recommendation.isLoading && <div className="empty"><span className="spinner" /></div>}
          {recommendation.isError && (
            <div className="notice" data-tone="error">{describeError(recommendation.error)}</div>
          )}
          {recommendation.data && (
            <div className="stack" style={{ gap: 12 }}>
              <div>
                <div className="label">Current route</div>
                <div className="spread" style={{ marginTop: 5 }}>
                  <span style={{ fontWeight: 500 }}>{recommendation.data.currentPathName}</span>
                  <StatusPill status={recommendation.data.currentStatus} chip />
                </div>
                <div className="mono tiny faint" style={{ marginTop: 3 }}>
                  {recommendation.data.sourceEndpoint} → {recommendation.data.destinationEndpoint} ·{' '}
                  {fmtLatency(recommendation.data.currentAvgLatencyMs)} ·{' '}
                  {fmtLoss(recommendation.data.currentAvgPacketLossPct)}
                </div>
              </div>

              <div className="mono tiny faint" style={{ paddingLeft: 2 }}>↓ evaluated against all paths on the same endpoint pair</div>

              {recommendation.data.hasAlternative ? (
                <>
                  <div>
                    <div className="label">Recommended route</div>
                    <div className="spread" style={{ marginTop: 5 }}>
                      <Link
                        to={`/paths/${recommendation.data.recommendedPathId}`}
                        style={{ fontWeight: 500, color: 'var(--accent)' }}
                      >
                        {recommendation.data.recommendedPathName}
                      </Link>
                      <StatusPill status={recommendation.data.recommendedStatus} chip />
                    </div>
                    <div className="mono tiny faint" style={{ marginTop: 3 }}>
                      {fmtLatency(recommendation.data.recommendedAvgLatencyMs)} ·{' '}
                      {fmtLoss(recommendation.data.recommendedAvgPacketLossPct)}
                    </div>
                  </div>

                  <div className="notice" data-tone="ok">
                    <div className="label" style={{ color: 'inherit', opacity: 0.7 }}>Reason</div>
                    <div style={{ marginTop: 4 }}>{recommendation.data.reason}</div>
                  </div>

                  <div className="field">
                    <span className="label">Shift reason (recorded with the simulation)</span>
                    <input
                      value={shiftReason}
                      placeholder={recommendation.data.reason}
                      onChange={(event) => setShiftReason(event.target.value)}
                    />
                  </div>

                  <button
                    className="btn btn-primary"
                    type="button"
                    disabled={shift.isPending}
                    onClick={() => shift.mutate()}
                  >
                    {shift.isPending ? 'Recording…' : 'Record simulated shift'}
                  </button>

                  <p className="tiny faint" style={{ margin: 0 }}>
                    This writes an audit entry to <span className="mono">traffic_shift_logs</span> with
                    status <span className="mono">SIMULATED</span>. NETPATH does not reconfigure network
                    devices, and no live traffic is moved.
                  </p>
                </>
              ) : (
                <div className="notice" data-tone="warn">{recommendation.data.reason}</div>
              )}

              {shiftNotice && <div className="notice" data-tone="ok">{shiftNotice}</div>}
            </div>
          )}
        </Panel>

        <Panel
          title="Shift history"
          flush
          meta={<span className="mono tiny faint">{shifts.data?.length ?? 0}</span>}
        >
          {(shifts.data ?? []).length === 0 ? (
            <div className="empty">No shifts simulated for this path</div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>From</th>
                  <th>To</th>
                  <th>Reason</th>
                  <th className="right">When</th>
                </tr>
              </thead>
              <tbody>
                {(shifts.data ?? []).map((entry) => (
                  <tr key={entry.id}>
                    <td className="mono tiny">{entry.oldPathName}</td>
                    <td className="mono tiny">{entry.newPathName}</td>
                    <td className="tiny muted truncate" style={{ maxWidth: 220 }}>{entry.reason}</td>
                    <td className="right mono tiny faint" title={fmtTimestamp(entry.shiftedAt)}>
                      {fmtRelative(entry.shiftedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title="Submit telemetry sample">
          <form
            className="stack"
            style={{ gap: 10 }}
            onSubmit={(event) => {
              event.preventDefault();
              setSampleNotice(null);
              sample.mutate();
            }}
          >
            <p className="tiny muted" style={{ margin: 0 }}>
              Simulates what a monitoring agent would report:{' '}
              <span className="mono">POST /api/paths/{pathId}/metrics</span>. The sample is persisted,
              the path is re-evaluated, and the Redis hot state is refreshed.
            </p>

            <div className="grid-3" style={{ gap: 10 }}>
              <label className="field">
                <span className="label">Latency (ms)</span>
                <input
                  type="number"
                  min={0}
                  value={latency}
                  onChange={(event) => setLatency(event.target.value)}
                  required
                />
              </label>
              <label className="field">
                <span className="label">Packet loss (%)</span>
                <input
                  type="number"
                  min={0}
                  max={100}
                  step="0.01"
                  value={loss}
                  onChange={(event) => setLoss(event.target.value)}
                  required
                />
              </label>
              <label className="field">
                <span className="label">Throughput (Mbps)</span>
                <input
                  type="number"
                  min={0}
                  value={throughput}
                  onChange={(event) => setThroughput(event.target.value)}
                />
              </label>
            </div>

            <div className="row">
              <button className="btn btn-primary" type="submit" disabled={sample.isPending}>
                {sample.isPending ? 'Submitting…' : 'Submit sample'}
              </button>
              <button
                className="btn btn-ghost"
                type="button"
                onClick={() => {
                  setLatency('250');
                  setLoss('8');
                  setThroughput('450');
                }}
              >
                Degradation preset
              </button>
            </div>

            {sampleNotice && (
              <div className="notice" data-tone={sample.isError ? 'error' : 'ok'}>{sampleNotice}</div>
            )}
          </form>
        </Panel>

        <Panel
          title="Recent samples"
          flush
          meta={<span className="mono tiny faint">{metrics.data?.totalElements ?? 0} retained</span>}
        >
          {samples.length === 0 ? (
            <div className="empty">No telemetry has been ingested for this path</div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th className="right">Latency</th>
                  <th className="right">Loss</th>
                  <th className="right">Throughput</th>
                </tr>
              </thead>
              <tbody>
                {[...samples].reverse().slice(0, 12).map((entry) => (
                  <tr key={entry.id}>
                    <td className="mono tiny">{fmtTimestamp(entry.timestamp)}</td>
                    <td className="right numeric">{fmtLatency(entry.latencyMs)}</td>
                    <td className="right numeric">{fmtLoss(entry.packetLossPct)}</td>
                    <td className="right numeric">{fmtThroughput(entry.throughputMbps)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>
      </div>
    </main>
  );
}
