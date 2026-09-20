import { fmtClock } from '../lib/format';

export interface TimelinePoint {
  timestamp: string;
  value: number;
}

interface Props {
  title: string;
  unit: string;
  points: TimelinePoint[];
  color: string;
  digits?: number;
  warning?: number;
  severe?: number;
}

const VIEW_WIDTH = 600;
const VIEW_HEIGHT = 88;

/**
 * Threshold lines are drawn from the same configuration the backend classifies with, so the chart
 * explains the status rather than just decorating it.
 */
export function Timeline({ title, unit, points, color, digits = 0, warning, severe }: Props) {
  if (points.length === 0) {
    return (
      <div className="panel">
        <header className="panel-head">
          <span className="panel-title">{title}</span>
        </header>
        <div className="empty">No telemetry samples recorded yet</div>
      </div>
    );
  }

  const values = points.map((point) => point.value);
  const max = Math.max(...values, severe ?? 0, warning ?? 0, 1) * 1.15;
  const last = values[values.length - 1];
  const min = Math.min(...values);

  const x = (index: number) => (points.length === 1 ? 0 : (index / (points.length - 1)) * VIEW_WIDTH);
  const y = (value: number) => VIEW_HEIGHT - (value / max) * VIEW_HEIGHT;

  const line = points.map((point, index) => `${x(index).toFixed(2)},${y(point.value).toFixed(2)}`).join(' ');
  const area = `0,${VIEW_HEIGHT} ${line} ${VIEW_WIDTH},${VIEW_HEIGHT}`;

  return (
    <div className="panel">
      <header className="panel-head">
        <span className="panel-title">{title}</span>
        <span className="row tiny">
          <span className="mono" style={{ color }}>
            {last.toFixed(digits)} {unit}
          </span>
          <span className="faint mono">
            min {min.toFixed(digits)} / max {Math.max(...values).toFixed(digits)}
          </span>
        </span>
      </header>
      <div className="panel-body">
        <svg
          viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
          preserveAspectRatio="none"
          style={{ width: '100%', height: 88, display: 'block' }}
          role="img"
          aria-label={`${title} timeline`}
        >
          <polygon points={area} fill={color} opacity={0.1} />
          {warning !== undefined && (
            <line
              x1={0}
              x2={VIEW_WIDTH}
              y1={y(warning)}
              y2={y(warning)}
              stroke="var(--status-degraded)"
              strokeWidth={1}
              strokeDasharray="3 4"
              vectorEffect="non-scaling-stroke"
            />
          )}
          {severe !== undefined && (
            <line
              x1={0}
              x2={VIEW_WIDTH}
              y1={y(severe)}
              y2={y(severe)}
              stroke="var(--status-down)"
              strokeWidth={1}
              strokeDasharray="3 4"
              vectorEffect="non-scaling-stroke"
            />
          )}
          <polyline
            points={line}
            fill="none"
            stroke={color}
            strokeWidth={1.5}
            vectorEffect="non-scaling-stroke"
          />
        </svg>
        <div className="spread tiny faint mono" style={{ marginTop: 6 }}>
          <span>{fmtClock(points[0].timestamp)}</span>
          <span>
            {warning !== undefined && `warn ${warning}${unit} `}
            {severe !== undefined && `down ${severe}${unit}`}
          </span>
          <span>{fmtClock(points[points.length - 1].timestamp)}</span>
        </div>
      </div>
    </div>
  );
}
