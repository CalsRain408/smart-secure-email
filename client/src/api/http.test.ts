import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request, resetCsrf } from './http';

type Call = { url: string; init: RequestInit };

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const ok = (data: unknown) => jsonResponse(200, { success: true, data, timestamp: 't' });
const csrfOk = (token: string) => ok({ headerName: 'X-CSRF-TOKEN', token });
const fail = (status: number, code: string, details?: unknown) =>
  jsonResponse(status, { success: false, error: { code, message: code, details }, timestamp: 't' });

describe('request()', () => {
  let calls: Call[];
  let responses: Response[];

  beforeEach(() => {
    resetCsrf();
    calls = [];
    responses = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      calls.push({ url, init });
      const next = responses.shift();
      if (!next) {
        throw new Error(`unexpected fetch ${url}`);
      }
      return next;
    }));
  });

  afterEach(() => vi.unstubAllGlobals());

  const header = (call: Call | undefined, name: string) =>
    (call?.init.headers as Record<string, string> | undefined)?.[name];

  it('sends GET without fetching a CSRF token', async () => {
    responses.push(ok({ domain: 'domain-a.test' }));

    await expect(request('GET', '/api/v1/system/info')).resolves.toEqual({ domain: 'domain-a.test' });
    expect(calls).toHaveLength(1);
    expect(header(calls[0], 'X-CSRF-TOKEN')).toBeUndefined();
    expect(calls[0]?.init.credentials).toBe('same-origin');
  });

  it('fetches the CSRF token once and attaches it to state-changing requests', async () => {
    responses.push(csrfOk('t1'), ok({ id: 1 }), ok({ id: 2 }));

    await request('POST', '/api/v1/drafts', { subject: 'a' });
    await request('PUT', '/api/v1/drafts/1', { subject: 'b' });

    expect(calls.map((c) => c.url)).toEqual(['/api/v1/auth/csrf', '/api/v1/drafts', '/api/v1/drafts/1']);
    expect(header(calls[1], 'X-CSRF-TOKEN')).toBe('t1');
    expect(header(calls[2], 'X-CSRF-TOKEN')).toBe('t1');
    expect(calls[1]?.init.body).toBe(JSON.stringify({ subject: 'a' }));
  });

  it('fetches a new token after resetCsrf (login/logout rotate it)', async () => {
    responses.push(csrfOk('before'), ok(null), csrfOk('after'), ok(null));

    await request('POST', '/api/v1/auth/login', {});
    resetCsrf();
    await request('POST', '/api/v1/auth/logout');

    expect(header(calls[3], 'X-CSRF-TOKEN')).toBe('after');
  });

  it('retries exactly once with a fresh token when the server answers 403', async () => {
    responses.push(csrfOk('stale'), fail(403, 'FORBIDDEN'), csrfOk('fresh'), ok({ id: 7 }));

    await expect(request('POST', '/api/v1/mails', {})).resolves.toEqual({ id: 7 });
    expect(header(calls[1], 'X-CSRF-TOKEN')).toBe('stale');
    expect(header(calls[3], 'X-CSRF-TOKEN')).toBe('fresh');
  });

  it('gives up after the single retry', async () => {
    responses.push(csrfOk('a'), fail(403, 'FORBIDDEN'), csrfOk('b'), fail(403, 'FORBIDDEN'));

    await expect(request('POST', '/api/v1/mails', {})).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' });
    expect(calls).toHaveLength(4);
  });

  it('maps the error envelope to ApiError with details', async () => {
    responses.push(csrfOk('t'), fail(400, 'MAIL_RECIPIENT_INVALID', [{ field: 'x@y.test', reason: 'UNKNOWN_RECIPIENT' }]));

    const error = await request('POST', '/api/v1/mails', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: 'MAIL_RECIPIENT_INVALID' });
    expect((error as ApiError).details).toEqual([{ field: 'x@y.test', reason: 'UNKNOWN_RECIPIENT' }]);
  });

  it('reports a non-JSON server failure as INTERNAL_ERROR', async () => {
    responses.push(new Response('<html>Bad gateway</html>', { status: 502 }));

    await expect(request('GET', '/api/v1/mails')).rejects.toMatchObject({ status: 502, code: 'INTERNAL_ERROR' });
  });

  it('reports network failures as NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));

    await expect(request('GET', '/api/v1/mails')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });
});
