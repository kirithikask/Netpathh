import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Panel } from '../components/Panel';
import { fetchApplications, fetchPaths } from '../api/queries';
import { describeError } from '../api/client';
import { StatusPill } from '../components/StatusPill';
import { fmtLatency, fmtLoss, fmtNumber, fmtTimestamp } from '../lib/format';

export function ApplicationsPage() {
  const applications = useQuery({ queryKey: ['applications'], queryFn: fetchApplications });
  const paths = useQuery({
    queryKey: ['paths', { page: 0, size: 100 }],
    queryFn: () => fetchPaths({ page: 0, size: 100 }),
  });

  const pathsByApplication = (applicationId: number) =>
    (paths.data?.content ?? []).filter((path) => path.applicationId === applicationId);

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">Applications</h1>
          <p className="page-sub">
            Ownership boundary for endpoints and network paths. Reliability is reported per
            application so a team can be pointed at what it owns.
          </p>
        </div>
      </div>

      {applications.isError && (
        <div className="notice" data-tone="error">{describeError(applications.error)}</div>
      )}

      <div className="stack">
        {(applications.data ?? []).map((application) => {
          const owned = pathsByApplication(application.id);
          return (
            <Panel
              key={application.id}
              title={application.name}
              meta={
                <span className="row tiny faint mono">
                  <span>{application.status}</span>
                  <span>·</span>
                  <span>{application.endpointCount ?? 0} endpoints</span>
                  <span>·</span>
                  <span>{application.pathCount ?? owned.length} paths</span>
                </span>
              }
              actions={
                <span className="mono tiny faint" title={fmtTimestamp(application.createdAt)}>
                  created {fmtTimestamp(application.createdAt)}
                </span>
              }
            >
              <p className="muted tiny" style={{ marginTop: 0 }}>
                {application.description ?? 'No description recorded'}
              </p>

              {owned.length === 0 ? (
                <div className="empty">No network paths owned by this application</div>
              ) : (
                <table className="table">
                  <thead>
                    <tr>
                      <th>Path</th>
                      <th>Flow</th>
                      <th>Status</th>
                      <th className="right">Latency</th>
                      <th className="right">Loss</th>
                    </tr>
                  </thead>
                  <tbody>
                    {owned.map((path) => (
                      <tr key={path.id}>
                        <td>
                          <Link to={`/paths/${path.id}`}>{path.pathName}</Link>
                        </td>
                        <td className="mono tiny">
                          {path.sourceEndpointName} → {path.destinationEndpointName}
                        </td>
                        <td>
                          <StatusPill status={path.status} />
                        </td>
                        <td className="right numeric">{fmtLatency(path.averageLatencyMs)}</td>
                        <td className="right numeric">{fmtLoss(path.averagePacketLossPct)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Panel>
          );
        })}

        {applications.data?.length === 0 && (
          <Panel title="Applications">
            <div className="empty">No applications registered</div>
          </Panel>
        )}
      </div>

      <p className="tiny faint" style={{ margin: 0 }}>
        {fmtNumber(applications.data?.length ?? 0)} applications ·{' '}
        {fmtNumber(paths.data?.totalElements ?? 0)} paths tracked
      </p>
    </main>
  );
}
