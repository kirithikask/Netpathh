import { NavLink, Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { fetchDashboardStats } from '../api/queries';

const NAV = [
  { to: '/', label: 'Dashboard', index: '01' },
  { to: '/paths', label: 'Network paths', index: '02' },
  { to: '/endpoints', label: 'Endpoints', index: '03' },
  { to: '/applications', label: 'Applications', index: '04' },
];

export function Layout() {
  const { operator, signOut } = useAuth();

  const { data: stats } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: fetchDashboardStats,
    refetchInterval: 15_000,
  });

  const total = stats?.totalPaths ?? 0;
  const healthy = stats?.healthyPaths ?? 0;
  const degraded = stats?.degradedPaths ?? 0;
  const down = stats?.downPaths ?? 0;
  const unknown = stats?.unknownPaths ?? 0;
  const pct = (value: number) => (total > 0 ? `${(value / total) * 100}%` : '0%');

  return (
    <div className="shell">
      <aside className="rail">
        <div className="rail-brand">
          <div className="rail-mark">NETPATH</div>
          <div className="rail-tagline">
            Network reliability &amp; route intelligence console
          </div>
        </div>

        <nav className="rail-nav">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => (isActive ? 'rail-link rail-active' : 'rail-link')}
            >
              <span>{item.label}</span>
              <span className="rail-link-index">{item.index}</span>
            </NavLink>
          ))}
        </nav>

        <div className="rail-foot">
          <div className="label" style={{ marginBottom: 6 }}>
            Fleet state
          </div>
          <div className="health-bar">
            <span style={{ width: pct(healthy), background: 'var(--status-healthy)' }} />
            <span style={{ width: pct(degraded), background: 'var(--status-degraded)' }} />
            <span style={{ width: pct(down), background: 'var(--status-down)' }} />
            <span style={{ width: pct(unknown), background: 'var(--status-unknown)' }} />
          </div>
          <div className="mono tiny" style={{ marginTop: 8 }}>
            {healthy} healthy · {degraded} degraded · {down} down · {unknown} unknown
          </div>
        </div>
      </aside>

      <div className="main netpath-surface">
        <header className="topbar">
          <div className="topbar-crumbs">
            <strong className="mono">operator@netpath</strong>
            <span className="faint">/</span>
            <span>{operator?.name ?? 'unknown'}</span>
            <span className="faint mono tiny">{operator?.role ?? ''}</span>
          </div>
          <div className="row">
            <span className="mono tiny faint">
              {total} paths tracked
            </span>
            <button className="btn btn-ghost" onClick={signOut} type="button">
              Sign out
            </button>
          </div>
        </header>

        <Outlet />
      </div>
    </div>
  );
}
