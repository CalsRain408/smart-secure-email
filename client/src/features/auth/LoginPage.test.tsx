import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntApp, ConfigProvider } from 'antd';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetCsrf } from '../../api/http';
import { LoginPage } from './LoginPage';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

/** Minimal fake backend: one account, session-bound CSRF token. */
function fakeServer() {
  const requests: { url: string; method: string; headers: Record<string, string> }[] = [];
  return {
    requests,
    fetch: vi.fn(async (url: string, init: RequestInit) => {
      const headers = (init.headers ?? {}) as Record<string, string>;
      requests.push({ url, method: init.method ?? 'GET', headers });
      if (url === '/api/v1/system/info') {
        return json(200, { success: true, data: { domain: 'domain-a.test', displayName: 'Domain A Corp' } });
      }
      if (url === '/api/v1/auth/me') {
        return json(401, { success: false, error: { code: 'UNAUTHENTICATED', message: 'x' } });
      }
      if (url === '/api/v1/auth/csrf') {
        return json(200, { success: true, data: { headerName: 'X-CSRF-TOKEN', token: 'tok' } });
      }
      if (url === '/api/v1/auth/login') {
        const body = JSON.parse(String(init.body)) as { username: string; password: string };
        if (headers['X-CSRF-TOKEN'] !== 'tok') {
          return json(403, { success: false, error: { code: 'FORBIDDEN', message: 'x' } });
        }
        return body.password === 'Str0ngPassw0rd'
          ? json(200, { success: true, data: { username: 'alice', address: 'alice@domain-a.test', displayName: 'Alice', role: 'MEMBER' } })
          : json(401, { success: false, error: { code: 'AUTH_INVALID_CREDENTIALS', message: 'x' } });
      }
      return json(404, { success: false, error: { code: 'NOT_FOUND', message: 'x' } });
    }),
  };
}

function renderLogin() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/mail/inbox', element: <p>inbox reached</p> },
    ],
    { initialEntries: ['/login'] },
  );
  render(
    <QueryClientProvider client={client}>
      {/* Same provider settings as main.tsx (no auto-inserted space in button labels). */}
      <ConfigProvider button={{ autoInsertSpace: false }}>
        <AntApp>
          <RouterProvider router={router} />
        </AntApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  let server: ReturnType<typeof fakeServer>;

  beforeEach(() => {
    resetCsrf();
    server = fakeServer();
    vi.stubGlobal('fetch', server.fetch);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('leads with the domain the user is signing into', async () => {
    renderLogin();
    expect(await screen.findByRole('heading', { level: 1, name: /domain-a\.test/ })).toBeInTheDocument();
  });

  it('shows a clear error for wrong credentials', async () => {
    renderLogin();
    await userEvent.type(await screen.findByLabelText('用户名或邮箱地址'), 'alice');
    await userEvent.type(screen.getByLabelText('密码'), 'wrong-password1');
    await userEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('用户名或密码不正确。')).toBeInTheDocument();
  });

  it('signs in with the CSRF token and moves to the inbox', async () => {
    renderLogin();
    await userEvent.type(await screen.findByLabelText('用户名或邮箱地址'), 'alice');
    await userEvent.type(screen.getByLabelText('密码'), 'Str0ngPassw0rd');
    await userEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('inbox reached')).toBeInTheDocument();
    await waitFor(() => {
      const login = server.requests.find((r) => r.url === '/api/v1/auth/login');
      expect(login?.headers['X-CSRF-TOKEN']).toBe('tok');
    });
  });
});
