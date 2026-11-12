import { ApiError } from './http';

/** User-facing (Chinese) text per backend error code. Says what happened and what to do. */
const MESSAGES: Record<string, string> = {
  NETWORK_ERROR: '无法连接服务器，请检查网络或稍后再试。',
  VALIDATION_FAILED: '有字段未通过校验，请按提示修改。',
  MALFORMED_REQUEST: '请求格式不正确。',
  UNAUTHENTICATED: '登录已过期，请重新登录。',
  FORBIDDEN: '没有权限执行这个操作。',
  NOT_FOUND: '邮件不存在或已被删除。',
  CONFLICT: '内容已被其他操作修改，请刷新后再试。',
  PAYLOAD_TOO_LARGE: '内容太大，请缩短后再试。',
  RATE_LIMITED: '操作太频繁，请稍后再试。',
  INTERNAL_ERROR: '服务器出错了，请稍后再试。',
  AUTH_INVALID_CREDENTIALS: '用户名或密码不正确。',
  AUTH_PASSWORD_MISMATCH: '两次输入的密码不一致。',
  AUTH_USERNAME_UNAVAILABLE: '这个用户名不可用，请换一个。',
  MAIL_RECIPIENT_INVALID: '有收件人无法接收这封邮件。',
  MAIL_INVALID_STATE: '这封邮件已发送，不能再修改。',
};

const RECIPIENT_REASONS: Record<string, string> = {
  UNKNOWN_RECIPIENT: '本域没有这个用户',
  DOMAIN_NOT_SUPPORTED: '无法投递到这个域名',
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return MESSAGES[error.code] ?? error.message ?? '操作失败。';
  }
  return '操作失败，请稍后再试。';
}

/** Per-address explanations for MAIL_RECIPIENT_INVALID. */
export function recipientProblems(error: unknown): { address: string; reason: string }[] {
  if (!(error instanceof ApiError) || error.code !== 'MAIL_RECIPIENT_INVALID') {
    return [];
  }
  return error.details.map((d) => ({ address: d.field, reason: RECIPIENT_REASONS[d.reason] ?? d.reason }));
}

/** Field-level validation messages keyed by field name (e.g. "to[0]" → "to"). */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError) || error.code !== 'VALIDATION_FAILED') {
    return {};
  }
  const result: Record<string, string> = {};
  for (const detail of error.details) {
    const field = detail.field.replace(/\[\d+\]$/, '');
    result[field] ??= detail.reason;
  }
  return result;
}

/** Session missing/expired. A failed login is also 401 but has a different code. */
export function isUnauthenticated(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'UNAUTHENTICATED';
}
