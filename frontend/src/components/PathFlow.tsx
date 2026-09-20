import { StatusPill } from './StatusPill';
import type { NetworkPathDto, PathStatus } from '../api/types';

interface Props {
  path: NetworkPathDto;
  linkStatus: PathStatus;
}

export function PathFlow({ path, linkStatus }: Props) {
  return (
    <div className="hopline">
      <div className="hop-node">
        <div className="hop-node-name">{path.sourceEndpointName ?? 'source'}</div>
        <div className="hop-node-ip">{path.sourceEndpointIp ?? '—'}</div>
        <div className="hop-node-region">{path.sourceEndpointRegion ?? '—'}</div>
      </div>

      <div className="hop-link" data-status={linkStatus}>
        <span
          className="mono"
          style={{
            position: 'absolute',
            top: -18,
            left: '50%',
            transform: 'translateX(-50%)',
            fontSize: 10,
            color: 'var(--text-faint)',
            whiteSpace: 'nowrap',
          }}
        >
          {path.hops ?? 1} hop{(path.hops ?? 1) === 1 ? '' : 's'}
        </span>
      </div>

      <div className="hop-node">
        <div className="hop-node-name">{path.destinationEndpointName ?? 'destination'}</div>
        <div className="hop-node-ip">{path.destinationEndpointIp ?? '—'}</div>
        <div className="hop-node-region">{path.destinationEndpointRegion ?? '—'}</div>
      </div>

      <div style={{ marginLeft: 16 }}>
        <div className="label">Current state</div>
        <div style={{ marginTop: 6 }}>
          <StatusPill status={path.status} chip keyed={`flow-${path.status}`} />
        </div>
      </div>
    </div>
  );
}
