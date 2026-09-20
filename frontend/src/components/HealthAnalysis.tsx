import type { PathHealthConfig, PathHealthDto } from '../api/types';

interface Props {
  health: PathHealthDto;
  config: PathHealthConfig | undefined;
}

/**
 * Derives the reason for the current classification from the same aggregate values the backend
 * used, so the explanation cannot diverge from the status shown next to it.
 */
function explain(health: PathHealthDto, config: PathHealthConfig): string[] {
  const reasons: string[] = [];

  if (health.status === 'UNKNOWN') {
    reasons.push(
      `Only ${health.metricsCount} of ${config.minMetricsForEvaluation} required samples have been ingested, so the path cannot be classified yet.`,
    );
    return reasons;
  }

  const maxLatency = health.maxLatencyMs;
  const maxLoss = health.maxPacketLossPct;

  if (health.status === 'DOWN') {
    if (maxLatency != null && maxLatency >= config.downLatencyMs) {
      reasons.push(
        `Worst observed latency ${maxLatency.toFixed(0)} ms reached the unreachable threshold of ${config.downLatencyMs} ms.`,
      );
    }
    if (maxLoss != null && maxLoss >= config.downPacketLossPct) {
      reasons.push(
        `Worst observed packet loss ${maxLoss.toFixed(2)} % reached the unreachable threshold of ${config.downPacketLossPct} %.`,
      );
    }
    return reasons;
  }

  if (health.status === 'DEGRADED') {
    if (health.averageLatencyMs != null && health.averageLatencyMs >= config.degradedLatencyMs) {
      reasons.push(
        `Average latency ${health.averageLatencyMs.toFixed(0)} ms reached the warning threshold of ${config.degradedLatencyMs} ms.`,
      );
    }
    if (health.averagePacketLossPct != null && health.averagePacketLossPct >= config.degradedPacketLossPct) {
      reasons.push(
        `Average packet loss ${health.averagePacketLossPct.toFixed(2)} % reached the warning threshold of ${config.degradedPacketLossPct} %.`,
      );
    }
    return reasons;
  }

  reasons.push(
    `Average latency ${health.averageLatencyMs?.toFixed(0) ?? '—'} ms stays under ${config.degradedLatencyMs} ms and average packet loss ${health.averagePacketLossPct?.toFixed(2) ?? '—'} % stays under ${config.degradedPacketLossPct} %.`,
  );
  return reasons;
}

export function HealthAnalysis({ health, config }: Props) {
  if (!config) {
    return <div className="empty">Loading classification thresholds…</div>;
  }

  return (
    <div className="panel-body stack" style={{ gap: 12 }}>
      <div>
        <div className="label">Why this classification</div>
        <ul style={{ margin: '8px 0 0', paddingLeft: 16 }} className="tiny">
          {explain(health, config).map((reason) => (
            <li key={reason} style={{ marginBottom: 4 }}>{reason}</li>
          ))}
        </ul>
      </div>

      <div>
        <div className="label">Active thresholds</div>
        <table className="table" style={{ marginTop: 6 }}>
          <tbody>
            <tr>
              <td className="faint tiny">Latency degraded / unreachable</td>
              <td className="right mono tiny">
                {config.degradedLatencyMs} / {config.downLatencyMs} ms
              </td>
            </tr>
            <tr>
              <td className="faint tiny">Packet loss degraded / unreachable</td>
              <td className="right mono tiny">
                {config.degradedPacketLossPct} / {config.downPacketLossPct} %
              </td>
            </tr>
            <tr>
              <td className="faint tiny">Minimum samples before classifying</td>
              <td className="right mono tiny">{config.minMetricsForEvaluation}</td>
            </tr>
            <tr>
              <td className="faint tiny">Redis hot-state TTL</td>
              <td className="right mono tiny">{config.cacheTtlSeconds} s</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
