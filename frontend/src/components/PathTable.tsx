import { Link } from 'react-router-dom';
import type { NetworkPathDto } from '../api/types';
import { StatusPill } from './StatusPill';
import { fmtLatency, fmtLoss, fmtRelative, shortId } from '../lib/format';

interface Props {
  paths: NetworkPathDto[];
  showApplication?: boolean;
  emptyMessage?: string;
}

export function PathTable({ paths, showApplication = false, emptyMessage }: Props) {
  if (paths.length === 0) {
    return <div className="empty">{emptyMessage ?? 'No network paths match the current filter'}</div>;
  }

  return (
    <table className="table">
      <thead>
        <tr>
          <th>Id</th>
          <th>Path</th>
          <th>Flow</th>
          {showApplication && <th>Application</th>}
          <th>Status</th>
          <th className="right">Latency</th>
          <th className="right">Loss</th>
          <th className="right">Samples</th>
          <th className="right">Updated</th>
        </tr>
      </thead>
      <tbody>
        {paths.map((path) => (
          <tr key={path.id}>
            <td className="mono faint">{shortId(path.id)}</td>
            <td>
              <Link to={`/paths/${path.id}`} style={{ display: 'block' }}>
                <span style={{ fontWeight: 500 }}>{path.pathName}</span>
                <span className="tiny faint mono">
                  {path.hops ?? 1} hop{(path.hops ?? 1) === 1 ? '' : 's'}
                  {path.isPrimary === false ? ' · backup' : ''}
                </span>
              </Link>
            </td>
            <td className="mono tiny">
              <span>{path.sourceEndpointName ?? '—'}</span>
              <span className="faint"> → </span>
              <span>{path.destinationEndpointName ?? '—'}</span>
              <span className="faint" style={{ display: 'block' }}>
                {path.sourceEndpointIp ?? '—'} → {path.destinationEndpointIp ?? '—'}
              </span>
            </td>
            {showApplication && <td className="muted">{path.applicationName ?? '—'}</td>}
            <td>
              <StatusPill status={path.status} keyed={`${path.id}-${path.status}`} />
            </td>
            <td className="right numeric">{fmtLatency(path.averageLatencyMs)}</td>
            <td className="right numeric">{fmtLoss(path.averagePacketLossPct)}</td>
            <td className="right mono faint">{path.metricsCount ?? 0}</td>
            <td className="right mono faint">{fmtRelative(path.lastUpdated)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
