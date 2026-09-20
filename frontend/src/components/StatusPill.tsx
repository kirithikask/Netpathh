import type { PathStatus } from '../api/types';

interface Props {
  status: PathStatus | null | undefined;
  chip?: boolean;
  keyed?: string | number;
}

export function StatusPill({ status, chip = false, keyed }: Props) {
  const value: PathStatus = status ?? 'UNKNOWN';
  return (
    <span
      key={keyed}
      className={`status${chip ? ' status-chip' : ''} pulse`}
      data-status={value}
      title={`Path status: ${value}`}
    >
      <span className="status-dot" />
      {value}
    </span>
  );
}
