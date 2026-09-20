import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { clearToken, getToken, setToken, setUnauthorizedHandler } from '../api/client';
import { login as loginRequest } from '../api/queries';

const OPERATOR_KEY = 'netpath.operator';

export interface Operator {
  email: string;
  name: string;
  role: string;
}

interface AuthValue {
  operator: Operator | null;
  isAuthenticated: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

function readOperator(): Operator | null {
  const raw = localStorage.getItem(OPERATOR_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Operator;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [operator, setOperator] = useState<Operator | null>(() => (getToken() ? readOperator() : null));

  const signOut = useCallback(() => {
    clearToken();
    localStorage.removeItem(OPERATOR_KEY);
    setOperator(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => signOut());
    return () => setUnauthorizedHandler(null);
  }, [signOut]);

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await loginRequest(email, password);
    setToken(response.token);
    const next: Operator = { email: response.email, name: response.name, role: response.role };
    localStorage.setItem(OPERATOR_KEY, JSON.stringify(next));
    setOperator(next);
  }, []);

  const value = useMemo<AuthValue>(
    () => ({ operator, isAuthenticated: operator !== null, signIn, signOut }),
    [operator, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
