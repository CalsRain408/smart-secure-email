import { request } from './http';
import type {
  ComposeRequest,
  CurrentUser,
  LoginRequest,
  MailDetail,
  MailFolder,
  MailSummary,
  Page,
  RegisterRequest,
  SystemInfo,
} from './types';

export const systemApi = {
  info: () => request<SystemInfo>('GET', '/api/v1/system/info'),
};

export const authApi = {
  me: () => request<CurrentUser>('GET', '/api/v1/auth/me'),
  login: (body: LoginRequest) => request<CurrentUser>('POST', '/api/v1/auth/login', body),
  register: (body: RegisterRequest) => request<CurrentUser>('POST', '/api/v1/auth/register', body),
  logout: () => request<void>('POST', '/api/v1/auth/logout'),
};

export const mailApi = {
  list: (folder: MailFolder, page: number, size: number) =>
    request<Page<MailSummary>>(
      'GET',
      `/api/v1/mails?${new URLSearchParams({ folder, page: String(page), size: String(size) })}`,
    ),
  get: (id: number) => request<MailDetail>('GET', `/api/v1/mails/${id}`),
  updateFlags: (id: number, flags: { read?: boolean; starred?: boolean }) =>
    request<MailDetail>('PATCH', `/api/v1/mails/${id}`, flags),
  send: (body: ComposeRequest) => request<MailDetail>('POST', '/api/v1/mails', body),
  createDraft: (body: ComposeRequest) => request<MailDetail>('POST', '/api/v1/drafts', body),
  updateDraft: (id: number, body: ComposeRequest) => request<MailDetail>('PUT', `/api/v1/drafts/${id}`, body),
  deleteDraft: (id: number) => request<void>('DELETE', `/api/v1/drafts/${id}`),
  sendDraft: (id: number) => request<MailDetail>('POST', `/api/v1/drafts/${id}/send`),
};
