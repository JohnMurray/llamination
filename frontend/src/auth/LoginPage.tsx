import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';

import { ApiError } from '../api/httpClient';
import { useAuth } from './AuthProvider';

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (auth.authenticated) return <Navigate to="/lobbies" replace />;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    const data = new FormData(event.currentTarget);
    try {
      await auth.login(String(data.get('username')), String(data.get('password')));
      const returnTo = searchParams.get('returnTo');
      navigate(returnTo?.startsWith('/') ? returnTo : '/lobbies', { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Unable to sign in.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="centered-shell">
      <section className="auth-card" aria-labelledby="login-title">
        <p className="eyebrow">Cooperative RTS</p>
        <h1 id="login-title">Llamination</h1>
        <p className="subtitle">Sign in to rally your herd.</p>
        <form onSubmit={submit}>
          <label htmlFor="username">Username</label>
          <input id="username" name="username" autoComplete="username" required autoFocus />
          <label htmlFor="password">Password</label>
          <input id="password" name="password" type="password" autoComplete="current-password" required />
          <button className="primary full-width" type="submit" disabled={submitting}>
            {submitting ? 'Entering…' : 'Enter the pasture'}
          </button>
          <p className="form-error" role="alert">{error}</p>
        </form>
      </section>
    </main>
  );
}
