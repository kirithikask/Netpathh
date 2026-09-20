import axios, { AxiosError } from 'axios';
import type { ApiError } from './types';

const TOKEN_KEY = 'netpath.token';

let onUnauthorized: (() => void) | null = null;

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/** Lets the auth layer reset UI state when the API rejects a stale token. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

/**
 * Where the API lives. Locally the Vite dev server proxies `/api` to the backend on port 8080, so
 * the relative default works. A deployed build sets VITE_API_BASE_URL to the full API root,
 * including the `/api` segment - for example https://netpath-api.onrender.com/api.
 */
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

export const api = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      clearToken();
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

/** Turns an axios failure into the message the API actually returned. */
export function describeError(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    if (error.response?.data?.message) {
      return error.response.data.message;
    }
    if (error.code === 'ERR_NETWORK') {
      return `Cannot reach the NETPATH API at ${baseURL}.`;
    }
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Unexpected error';
}
