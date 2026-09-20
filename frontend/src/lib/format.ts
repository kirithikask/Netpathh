import type { PathStatus } from '../api/types';

const DASH = '—';

export function fmtNumber(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || Number.isNaN(value)) return DASH;
  return value.toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

export function fmtLatency(value: number | null | undefined): string {
  if (value === null || value === undefined) return DASH;
  return `${value.toFixed(value < 10 ? 1 : 0)} ms`;
}

export function fmtLoss(value: number | null | undefined): string {
  if (value === null || value === undefined) return DASH;
  return `${value.toFixed(2)} %`;
}

export function fmtThroughput(value: number | null | undefined): string {
  if (value === null || value === undefined) return DASH;
  return `${fmtNumber(value, value < 10 ? 1 : 0)} Mbps`;
}

export function fmtClock(iso: string | null | undefined): string {
  if (!iso) return DASH;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return DASH;
  return date.toISOString().slice(11, 19) + 'Z';
}

export function fmtTimestamp(iso: string | null | undefined): string {
  if (!iso) return DASH;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return DASH;
  return date.toISOString().replace('T', ' ').slice(0, 19) + 'Z';
}

export function fmtRelative(iso: string | null | undefined): string {
  if (!iso) return DASH;
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return DASH;

  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

/**
 * Higher means more urgent. OUTAGE > impairment > no data, because UNKNOWN says nothing about
 * reachability while DOWN says the path is failing now.
 */
export function severityRank(status: PathStatus): number {
  switch (status) {
    case 'DOWN':
      return 3;
    case 'DEGRADED':
      return 2;
    case 'UNKNOWN':
      return 1;
    default:
      return 0;
  }
}

export const statusColor: Record<PathStatus, string> = {
  HEALTHY: 'var(--status-healthy)',
  DEGRADED: 'var(--status-degraded)',
  DOWN: 'var(--status-down)',
  UNKNOWN: 'var(--status-unknown)',
};

export function shortId(id: number | null | undefined): string {
  if (id === null || id === undefined) return DASH;
  return `#${String(id).padStart(4, '0')}`;
}
