import type { ReactNode } from 'react';

interface Props {
  title: string;
  meta?: ReactNode;
  actions?: ReactNode;
  flush?: boolean;
  children: ReactNode;
}

export function Panel({ title, meta, actions, flush = false, children }: Props) {
  return (
    <section className="panel">
      <header className="panel-head">
        <span className="panel-title">{title}</span>
        <span className="row">
          {meta}
          {actions}
        </span>
      </header>
      <div className={`panel-body${flush ? ' flush' : ''}`}>{children}</div>
    </section>
  );
}
