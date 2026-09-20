import { useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { describeError } from '../api/client';

export function LoginPage() {
  const { isAuthenticated, signIn } = useAuth();
  const location = useLocation();

  const [email, setEmail] = useState('operator@netpath.io');
  const [password, setPassword] = useState('netpath123');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (isAuthenticated) {
    const from = (location.state as { from?: string } | null)?.from ?? '/';
    return <Navigate to={from} replace />;
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(email, password);
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <div className="login-aside netpath-surface">
        <div>
          <div className="login-mark">NETPATH</div>
          <p className="muted" style={{ maxWidth: '52ch', marginTop: 18 }}>
            Path-level reliability for internal networks. NETPATH ingests telemetry per path,
            classifies health from configurable thresholds, keeps current state in Redis, retains
            history in PostgreSQL, and recommends a healthier route when a path degrades.
          </p>
        </div>

        <pre
          className="mono tiny faint"
          style={{ margin: 0, lineHeight: 1.7, overflowX: 'auto' }}
        >{`edge-a ──[primary]──────► edge-b      HEALTHY
edge-a ──[standby]──────► edge-b      DEGRADED
edge-a ──[via core]────► edge-b      HEALTHY

recommendation: primary → via core
reason: status improvement: DEGRADED → HEALTHY`}</pre>
      </div>

      <form className="login-form" onSubmit={submit}>
        <div>
          <div className="label">Operator access</div>
          <h1 className="page-title" style={{ marginTop: 6, fontSize: 17 }}>
            Sign in to the console
          </h1>
        </div>

        <label className="field">
          <span className="label">Email</span>
          <input
            type="email"
            value={email}
            autoComplete="username"
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>

        <label className="field">
          <span className="label">Password</span>
          <input
            type="password"
            value={password}
            autoComplete="current-password"
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>

        {error && (
          <div className="notice" data-tone="error">
            {error}
          </div>
        )}

        <button className="btn btn-primary" type="submit" disabled={busy}>
          {busy ? 'Authenticating…' : 'Sign in'}
        </button>

        <p className="tiny faint" style={{ margin: 0 }}>
          Demo credentials come from the seeded operator account
          (<span className="mono">app.demo-data.email</span> /{' '}
          <span className="mono">app.demo-data.password</span>).
        </p>
      </form>
    </div>
  );
}
