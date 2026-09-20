import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import type { EndpointDto, NetworkPathDto, PathStatus } from '../api/types';
import { statusColor } from '../lib/format';

interface Props {
  endpoints: EndpointDto[];
  paths: NetworkPathDto[];
}

const WIDTH = 880;
const HEIGHT = 380;
const NODE_W = 132;
const NODE_H = 40;

/**
 * Regions become columns and endpoints stack inside their column, which keeps the graph readable
 * without pulling in a layout engine. Parallel paths between the same pair are bowed apart so they
 * stay distinguishable.
 */
export function TopologyView({ endpoints, paths }: Props) {
  const navigate = useNavigate();

  const { nodes, edges } = useMemo(() => {
    const regions = Array.from(new Set(endpoints.map((endpoint) => endpoint.region))).sort();
    const columnWidth = WIDTH / (regions.length + 1);

    const positioned = new Map<number, { x: number; y: number }>();
    const nodeList = regions.map((region) => {
      const inRegion = endpoints.filter((endpoint) => endpoint.region === region);
      const columnX = columnWidth * (regions.indexOf(region) + 1);
      return inRegion.map((endpoint, index) => {
        const y = (HEIGHT / (inRegion.length + 1)) * (index + 1);
        positioned.set(endpoint.id, { x: columnX, y });
        return { endpoint, x: columnX, y };
      });
    }).flat();

    const pairCounts = new Map<string, number>();
    const edgeList = paths.map((path) => {
      const from = positioned.get(path.sourceEndpointId);
      const to = positioned.get(path.destinationEndpointId);
      if (!from || !to) return null;

      const pairKey = [path.sourceEndpointId, path.destinationEndpointId].sort().join('-');
      const occurrence = pairCounts.get(pairKey) ?? 0;
      pairCounts.set(pairKey, occurrence + 1);

      const bow = (occurrence % 2 === 0 ? 1 : -1) * (14 + Math.floor(occurrence / 2) * 16);
      const midX = (from.x + to.x) / 2;
      const midY = (from.y + to.y) / 2;
      const controlX = midX;
      const controlY = midY + bow;

      return {
        path,
        d: `M ${from.x} ${from.y} Q ${controlX} ${controlY} ${to.x} ${to.y}`,
      };
    }).filter((edge): edge is { path: NetworkPathDto; d: string } => edge !== null);

    return { nodes: nodeList, edges: edgeList };
  }, [endpoints, paths]);

  if (endpoints.length === 0) {
    return <div className="empty">No endpoints registered yet</div>;
  }

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        style={{ width: '100%', minWidth: 640, height: 'auto', display: 'block' }}
        role="img"
        aria-label="Network topology"
      >
        {edges.map(({ path, d }) => {
          const status: PathStatus = path.status ?? 'UNKNOWN';
          return (
            <path
              key={path.id}
              d={d}
              fill="none"
              stroke={statusColor[status]}
              strokeWidth={status === 'DOWN' ? 2 : 1.25}
              strokeDasharray={path.isPrimary === false ? '5 4' : undefined}
              opacity={status === 'UNKNOWN' ? 0.45 : 0.9}
            />
          );
        })}

        {nodes.map(({ endpoint, x, y }) => (
          <g
            key={endpoint.id}
            transform={`translate(${x - NODE_W / 2}, ${y - NODE_H / 2})`}
            style={{ cursor: 'pointer' }}
            onClick={() => navigate(`/endpoints?region=${encodeURIComponent(endpoint.region)}`)}
          >
            <rect
              width={NODE_W}
              height={NODE_H}
              fill="var(--surface-inset)"
              stroke="var(--line-strong)"
              strokeWidth={1}
              rx={2}
            />
            <text x={9} y={16} fill="var(--text)" fontSize={11} fontWeight={600}>
              {endpoint.name.length > 17 ? `${endpoint.name.slice(0, 16)}…` : endpoint.name}
            </text>
            <text x={9} y={30} fill="var(--text-faint)" fontSize={10} fontFamily="var(--mono)">
              {endpoint.ipAddress}
            </text>
          </g>
        ))}
      </svg>

      <div className="row tiny faint mono" style={{ padding: '0 12px 12px' }}>
        {(['HEALTHY', 'DEGRADED', 'DOWN', 'UNKNOWN'] as PathStatus[]).map((status) => (
          <span key={status} className="row" style={{ gap: 5 }}>
            <span
              style={{
                display: 'inline-block',
                width: 14,
                height: 2,
                background: statusColor[status],
              }}
            />
            {status}
          </span>
        ))}
        <span style={{ marginLeft: 8 }}>dashed = non-primary path</span>
      </div>
    </div>
  );
}
