import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { request } from '../api/httpClient';

export interface Session {
  authenticated: boolean;
  username: string | null;
}

interface AuthContextValue extends Session {
  loading: boolean;
  login(username: string, password: string): Promise<void>;
  logout(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>({ authenticated: false, username: null });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    request<Session>('/api/auth/session')
      .then(setSession)
      .catch(() => setSession({ authenticated: false, username: null }))
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...session,
      loading,
      async login(username, password) {
        const next = await request<Session>('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ username, password }),
        });
        setSession(next);
      },
      async logout() {
        try {
          await request<null>('/api/auth/logout', { method: 'POST' });
        } finally {
          setSession({ authenticated: false, username: null });
        }
      },
    }),
    [loading, session],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('useAuth must be used inside AuthProvider');
  return auth;
}
