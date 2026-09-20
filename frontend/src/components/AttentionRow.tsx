import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchRecommendation } from '../api/queries';
import type { NetworkPathDto } from '../api/types';
import { StatusPill } from './StatusPill';
import { fmtLatency } from '../lib/format';

export function AttentionRow({ path }: { path: NetworkPathDto }) {
  const { data, isLoading } = useQuery({
    queryKey: ['recommendation', path.id],
    queryFn: () => fetchRecommendation(path.id),
    staleTime: 20_000,
  });

  return (
    <div
      style={{
        padding: '10px 12px',
        borderBottom: '1px solid var(--line)',
      }}
    >
      <div className="spread">
        <Link to={`/paths/${path.id}`} style={{ fontWeight: 500 }}>
          {path.pathName}
        </Link>
        <StatusPill status={path.status} keyed={`attention-${path.id}-${path.status}`} />
      </div>

      <div className="mono tiny faint" style={{ marginTop: 3 }}>
        {path.sourceEndpointName ?? '—'} → {path.destinationEndpointName ?? '—'} ·{' '}
        {fmtLatency(path.averageLatencyMs)} · {path.metricsCount ?? 0} samples
      </div>

      {isLoading && <div className="tiny faint" style={{ marginTop: 6 }}>evaluating alternatives…</div>}

      {data?.hasAlternative && (
        <div className="tiny" style={{ marginTop: 6 }}>
          <span className="faint">recommended </span>
          <Link to={`/paths/${data.recommendedPathId}`} className="mono" style={{ color: 'var(--accent)' }}>
            {data.recommendedPathName}
          </Link>
          <div className="faint" style={{ marginTop: 2 }}>{data.reason}</div>
        </div>
      )}

      {data && !data.hasAlternative && (
        <div className="tiny faint" style={{ marginTop: 6 }}>{data.reason}</div>
      )}
    </div>
  );
}
