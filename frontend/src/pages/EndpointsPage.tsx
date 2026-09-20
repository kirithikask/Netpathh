import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Panel } from '../components/Panel';
import { fetchEndpoints, fetchPaths, fetchRegions } from '../api/queries';
import { describeError } from '../api/client';

export function EndpointsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const region = searchParams.get('region') ?? '';

  const [page, setPage] = useState(0);

  const endpoints = useQuery({
    queryKey: ['endpoints', { page, region }],
    queryFn: () => fetchEndpoints({ page, size: 25, region }),
  });

  const regions = useQuery({ queryKey: ['regions'], queryFn: fetchRegions });

  const paths = useQuery({
    queryKey: ['paths', { page: 0, size: 100 }],
    queryFn: () => fetchPaths({ page: 0, size: 100 }),
  });

  const pathCount = (endpointId: number) =>
    (paths.data?.content ?? []).filter(
      (path) => path.sourceEndpointId === endpointId || path.destinationEndpointId === endpointId,
    ).length;

  const visible = endpoints.data?.content ?? [];
  const totalPages = endpoints.data?.totalPages ?? 0;

  function selectRegion(next: string) {
    setPage(0);
    if (next) {
      setSearchParams({ region: next });
    } else {
      setSearchParams({});
    }
  }

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">Endpoints</h1>
          <p className="page-sub">
            Addressable nodes in the monitored estate. Each network path terminates on two of these.
          </p>
        </div>

        <div className="row">
          <span className="label">Region</span>
          <div className="segmented">
            <button type="button" data-active={region === ''} onClick={() => selectRegion('')}>
              ALL
            </button>
            {(regions.data ?? []).map((zone) => (
              <button key={zone} type="button" data-active={region === zone} onClick={() => selectRegion(zone)}>
                {zone}
              </button>
            ))}
          </div>
        </div>
      </div>

      {endpoints.isError && (
        <div className="notice" data-tone="error">{describeError(endpoints.error)}</div>
      )}

      <Panel
        title="Endpoint inventory"
        flush
        meta={
          <span className="mono tiny faint">
            {endpoints.data?.totalElements ?? 0} endpoints{region ? ` · ${region}` : ''}
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
              <span className="mono tiny faint">{page + 1} / {totalPages}</span>
              <button
                className="btn btn-ghost"
                type="button"
                disabled={endpoints.data?.last ?? true}
                onClick={() => setPage((current) => current + 1)}
              >
                next →
              </button>
            </span>
          ) : null
        }
      >
        {endpoints.isLoading ? (
          <div className="empty"><span className="spinner" /></div>
        ) : visible.length === 0 ? (
          <div className="empty">No endpoints registered in {region || 'the estate'}</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Address</th>
                <th>Region</th>
                <th>Application</th>
                <th>State</th>
                <th className="right">Paths</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((endpoint) => (
                <tr key={endpoint.id}>
                  <td style={{ fontWeight: 500 }}>{endpoint.name}</td>
                  <td className="mono tiny">{endpoint.ipAddress}</td>
                  <td className="mono tiny faint">{endpoint.region}</td>
                  <td className="muted">{endpoint.applicationName ?? '—'}</td>
                  <td>
                    <span className="mono tiny faint">{endpoint.status}</span>
                  </td>
                  <td className="right numeric">{pathCount(endpoint.id)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      <p className="tiny faint" style={{ margin: 0 }}>
        Latency and packet loss are attributes of a network path, not of an endpoint, so this view
        reports how many paths terminate on each node. Region selection is resolved by the API
        (<span className="mono">GET /api/endpoints/region/&#123;region&#125;</span>), so paging stays
        correct for regions that do not fit on the first page.
      </p>
    </main>
  );
}
