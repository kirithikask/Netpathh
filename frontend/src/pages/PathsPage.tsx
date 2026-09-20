import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Panel } from '../components/Panel';
import { PathTable } from '../components/PathTable';
import { fetchPaths } from '../api/queries';
import { describeError } from '../api/client';
import type { PathStatus } from '../api/types';

const FILTERS: Array<PathStatus | 'ALL'> = ['ALL', 'HEALTHY', 'DEGRADED', 'DOWN', 'UNKNOWN'];
const PAGE_SIZE = 15;

export function PathsPage() {
  const [filter, setFilter] = useState<PathStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);

  const paths = useQuery({
    queryKey: ['paths', { page, size: PAGE_SIZE, status: filter }],
    queryFn: () =>
      fetchPaths({
        page,
        size: PAGE_SIZE,
        status: filter === 'ALL' ? '' : filter,
      }),
  });

  const totalPages = paths.data?.totalPages ?? 0;

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">Network paths</h1>
          <p className="page-sub">
            Every monitored flow between two endpoints. Status reflects the most recent telemetry
            evaluation, not a configured intent.
          </p>
        </div>

        <div className="segmented">
          {FILTERS.map((status) => (
            <button
              key={status}
              type="button"
              data-active={filter === status}
              onClick={() => {
                setFilter(status);
                setPage(0);
              }}
            >
              {status}
            </button>
          ))}
        </div>
      </div>

      {paths.isError && (
        <div className="notice" data-tone="error">{describeError(paths.error)}</div>
      )}

      <Panel
        title="Path inventory"
        flush
        meta={
          <span className="mono tiny faint">
            {paths.data?.totalElements ?? 0} paths
            {filter !== 'ALL' ? ` · filter ${filter}` : ''}
          </span>
        }
        actions={
          totalPages > 1 ? (
            <span className="row">
              <button
                className="btn btn-ghost"
                type="button"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                ← prev
              </button>
              <span className="mono tiny faint">
                {page + 1} / {totalPages}
              </span>
              <button
                className="btn btn-ghost"
                type="button"
                disabled={paths.data?.last ?? true}
                onClick={() => setPage((current) => current + 1)}
              >
                next →
              </button>
            </span>
          ) : null
        }
      >
        {paths.isLoading ? (
          <div className="empty"><span className="spinner" /></div>
        ) : (
          <PathTable
            paths={paths.data?.content ?? []}
            showApplication
            emptyMessage={`No paths with status ${filter}`}
          />
        )}
      </Panel>
    </main>
  );
}
