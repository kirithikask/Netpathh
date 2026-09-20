import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Panel } from '../components/Panel';
import { PathTable } from '../components/PathTable';
import { TopologyView } from '../components/TopologyView';
import { AttentionRow } from '../components/AttentionRow';
import { Timeline } from '../components/Timeline';
import {
  fetchDashboardStats,
  fetchEndpoints,
  fetchPathHealthConfig,
  fetchPathMetrics,
  fetchPaths,
  fetchRecentShifts,
} from '../api/queries';
import { describeError } from '../api/client';
import { fmtNumber, fmtRelative, fmtTimestamp, severityRank } from '../lib/format';

export function DashboardPage() {
  const stats = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: fetchDashboardStats,
    refetchInterval: 15_000,
  });

  const paths = useQuery({
    queryKey: ['paths', { page: 0, size: 10 }],
    queryFn: () => fetchPaths({ page: 0, size: 10 }),
    refetchInterval: 15_000,
  });

  const endpoints = useQuery({
    queryKey: ['endpoints', 'topology'],
    queryFn: () => fetchEndpoints({ page: 0, size: 100 }),
  });

  const shifts = useQuery({
    queryKey: ['shifts', 'recent'],
    queryFn: () => fetchRecentShifts(6),
    refetchInterval: 30_000,
  });

  const config = useQuery({
    queryKey: ['path-health-config'],
    queryFn: fetchPathHealthConfig,
    staleTime: Infinity,
  });

  const pathList = paths.data?.content ?? [];
  const total = stats.data?.totalPaths ?? 0;
  const share = (value: number | undefined) => (total > 0 ? `${((value ?? 0) / total) * 100}%` : '0%');

  const attention = [...pathList]
    .filter((path) => path.status === 'DOWN' || path.status === 'DEGRADED')
    .sort((a, b) => severityRank(b.status) - severityRank(a.status))
    .slice(0, 4);

  // The telemetry panels track the most urgent path that actually has samples to plot.
  const focus = [...pathList]
    .sort((a, b) => severityRank(b.status) - severityRank(a.status))
    .find((path) => (path.metricsCount ?? 0) > 0);

  const focusMetrics = useQuery({
    queryKey: ['metrics', focus?.id],
    queryFn: () => fetchPathMetrics(focus!.id, 30),
    enabled: Boolean(focus?.id),
    refetchInterval: 30_000,
  });

  const latencyPoints = [...(focusMetrics.data?.content ?? [])]
    .reverse()
    .map((sample) => ({ timestamp: sample.timestamp, value: sample.latencyMs }));

  const lossPoints = [...(focusMetrics.data?.content ?? [])]
    .reverse()
    .map((sample) => ({ timestamp: sample.timestamp, value: sample.packetLossPct }));

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">Network health</h1>
          <p className="page-sub">
            Path status is derived from ingested telemetry using configurable latency and packet-loss
            thresholds. Current state is served from Redis; history is retained in PostgreSQL.
          </p>
        </div>
        <div className="row tiny faint mono">
          {stats.isFetching ? <span className="spinner" /> : <span>live · 15s poll</span>}
        </div>
      </div>

      {stats.isError && (
        <div className="notice" data-tone="error">
          {describeError(stats.error)}
        </div>
      )}

      <div className="counter-strip">
        <div className="counter">
          <div className="counter-value">{fmtNumber(stats.data?.totalApplications)}</div>
          <div className="counter-label">Applications</div>
        </div>
        <div className="counter">
          <div className="counter-value">{fmtNumber(stats.data?.totalEndpoints)}</div>
          <div className="counter-label">Endpoints</div>
        </div>
        <div className="counter">
          <div className="counter-value">{fmtNumber(stats.data?.totalPaths)}</div>
          <div className="counter-label">Network paths</div>
        </div>
        <div className="counter">
          <div className="counter-value" style={{ color: 'var(--status-healthy)' }}>
            {fmtNumber(stats.data?.healthyPaths)}
          </div>
          <div className="counter-label">Healthy</div>
        </div>
        <div className="counter">
          <div className="counter-value" style={{ color: 'var(--status-degraded)' }}>
            {fmtNumber(stats.data?.degradedPaths)}
          </div>
          <div className="counter-label">Degraded</div>
        </div>
        <div className="counter">
          <div className="counter-value" style={{ color: 'var(--status-down)' }}>
            {fmtNumber(stats.data?.downPaths)}
          </div>
          <div className="counter-label">Down</div>
        </div>
        <div className="counter">
          <div className="counter-value" style={{ color: 'var(--status-unknown)' }}>
            {fmtNumber(stats.data?.unknownPaths)}
          </div>
          <div className="counter-label">Unknown</div>
        </div>
      </div>

      <div className="health-bar" style={{ height: 6 }}>
        <span style={{ width: share(stats.data?.healthyPaths), background: 'var(--status-healthy)' }} />
        <span style={{ width: share(stats.data?.degradedPaths), background: 'var(--status-degraded)' }} />
        <span style={{ width: share(stats.data?.downPaths), background: 'var(--status-down)' }} />
        <span style={{ width: share(stats.data?.unknownPaths), background: 'var(--status-unknown)' }} />
      </div>

      <div className="grid-2">
        <Panel
          title="Path status board"
          flush
          meta={<span className="mono tiny faint">{pathList.length} of {paths.data?.totalElements ?? 0}</span>}
          actions={
            <Link to="/paths" className="mono tiny" style={{ color: 'var(--accent)' }}>
              all paths →
            </Link>
          }
        >
          {paths.isLoading ? (
            <div className="empty"><span className="spinner" /></div>
          ) : (
            <PathTable
              paths={pathList}
              showApplication
              emptyMessage="No network paths registered yet"
            />
          )}
        </Panel>

        <Panel
          title="Requires attention"
          flush
          meta={<span className="mono tiny faint">{attention.length}</span>}
        >
          {attention.length === 0 ? (
            <div className="empty">Every tracked path is healthy or has no telemetry yet</div>
          ) : (
            attention.map((path) => <AttentionRow key={path.id} path={path} />)
          )}
        </Panel>
      </div>

      <Panel
        title="Topology"
        meta={
          <span className="mono tiny faint">
            {endpoints.data?.content.length ?? 0} endpoints · {pathList.length} paths shown
          </span>
        }
        flush
      >
        <TopologyView endpoints={endpoints.data?.content ?? []} paths={pathList} />
      </Panel>

      <div className="grid-2">
        <div className="stack">
          {focus ? (
            <>
              <Timeline
                title={`Latency · ${focus.pathName}`}
                unit="ms"
                color="var(--status-degraded)"
                points={latencyPoints}
                warning={config.data?.degradedLatencyMs}
                severe={config.data?.downLatencyMs}
              />
              <Timeline
                title={`Packet loss · ${focus.pathName}`}
                unit="%"
                color="var(--status-down)"
                digits={2}
                points={lossPoints}
                warning={config.data?.degradedPacketLossPct}
                severe={config.data?.downPacketLossPct}
              />
            </>
          ) : (
            <Panel title="Telemetry">
              <div className="empty">No telemetry to plot</div>
            </Panel>
          )}
        </div>

        <Panel
          title="Recent simulated shifts"
          flush
          meta={<span className="mono tiny faint">{shifts.data?.length ?? 0}</span>}
        >
          <div
            className="tiny faint"
            style={{ padding: '8px 12px', borderBottom: '1px solid var(--line)' }}
          >
            Recorded by <span className="mono">POST /api/paths/&#123;id&#125;/shift</span>. NETPATH does not
            control routers — these entries are simulated and stored for audit.
          </div>
          {(shifts.data ?? []).length === 0 ? (
            <div className="empty">No traffic shifts have been simulated yet</div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Path</th>
                  <th>Target</th>
                  <th>Status</th>
                  <th className="right">When</th>
                </tr>
              </thead>
              <tbody>
                {(shifts.data ?? []).map((shift) => (
                  <tr key={shift.id ?? `${shift.pathId}-${shift.shiftedAt}`}>
                    <td>
                      <Link to={`/paths/${shift.pathId}`}>{shift.oldPathName ?? shift.pathId}</Link>
                    </td>
                    <td className="mono tiny">{shift.newPathName ?? '—'}</td>
                    <td>
                      <span
                        className="mono tiny"
                        style={{
                          border: '1px solid var(--line-strong)',
                          borderRadius: 2,
                          padding: '1px 5px',
                          color: 'var(--text-dim)',
                        }}
                      >
                        {shift.status}
                      </span>
                    </td>
                    <td className="right mono tiny faint" title={fmtTimestamp(shift.shiftedAt)}>
                      {fmtRelative(shift.shiftedAt)}
                    </td>
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
