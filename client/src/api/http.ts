import type { ApiEnvelope, ApiErrorBody, CsrfToken, FieldViolation } from './types';

/** Error raised for any non-successful API call; `code` is the backend's stable error code. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: FieldViolation[];

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code;
    this.details = body.details ?? [];
  }
}

const CSRF_PATH = '/api/v1/auth/csrf';
const SAFE_METHODS = new Set(['GET', 'HEAD']);

/**
 * The CSRF token is bound to the server-side session and kept only in memory (never in
 * storage). It rotates on login and dies on logout, so callers reset it at those points.
 */
let csrf: CsrfToken | null = null;
let csrfInFlight: Promise<CsrfToken> | null = null;

export function resetCsrf(): void {
  csrf = null;
  csrfInFlight = null;
}

async function ensureCsrf(): Promise<CsrfToken> {
  if (csrf) {
    return csrf;
  }
  csrfInFlight ??= send<CsrfToken>('GET', CSRF_PATH, undefined, null).then((token) => {
    csrf = token;
    csrfInFlight = null;
    return token;
  });
  return csrfInFlight;
}

async function send<T>(method: string, path: string, body: unknown, token: CsrfToken | null): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers[token.headerName] = token.token;
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: 'Network error' });
  }

  let envelope: ApiEnvelope<T> | null = null;
  try {
    envelope = (await response.json()) as ApiEnvelope<T>;
  } catch {
    envelope = null;
  }

  if (!response.ok || !envelope || !envelope.success) {
    throw new ApiError(
      response.status,
      envelope?.error ?? { code: response.status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN_ERROR', message: response.statusText },
    );
  }
  return envelope.data as T;
}

/**
 * Calls the JSON API. State-changing requests carry the CSRF token; if the server rejects
 * it with 403 (e.g. the session expired and a new one was created), the token is fetched
 * again and the request retried exactly once.
 */
export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const upper = method.toUpperCase();
  if (SAFE_METHODS.has(upper)) {
    return send<T>(upper, path, body, null);
  }
  try {
    return await send<T>(upper, path, body, await ensureCsrf());
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      resetCsrf();
      return send<T>(upper, path, body, await ensureCsrf());
    }
    throw error;
  }
}
