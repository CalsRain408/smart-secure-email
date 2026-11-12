import { describe, expect, it } from 'vitest';
import { errorMessage, fieldErrors, isUnauthenticated, recipientProblems } from './errors';
import { ApiError } from './http';

describe('error helpers', () => {
  it('translates known codes and falls back for unknown errors', () => {
    expect(errorMessage(new ApiError(401, { code: 'AUTH_INVALID_CREDENTIALS', message: 'x' }))).toBe('用户名或密码不正确。');
    expect(errorMessage(new Error('boom'))).toBe('操作失败，请稍后再试。');
  });

  it('explains each undeliverable recipient', () => {
    const error = new ApiError(400, {
      code: 'MAIL_RECIPIENT_INVALID',
      message: 'x',
      details: [
        { field: 'ghost@domain-a.test', reason: 'UNKNOWN_RECIPIENT' },
        { field: 'x@domain-z.test', reason: 'DOMAIN_NOT_SUPPORTED' },
      ],
    });
    expect(recipientProblems(error)).toEqual([
      { address: 'ghost@domain-a.test', reason: '本域没有这个用户' },
      { address: 'x@domain-z.test', reason: '无法投递到这个域名' },
    ]);
  });

  it('collapses indexed field names', () => {
    const error = new ApiError(400, {
      code: 'VALIDATION_FAILED',
      message: 'x',
      details: [{ field: 'to[1]', reason: 'invalid mail address' }],
    });
    expect(fieldErrors(error)).toEqual({ to: 'invalid mail address' });
  });

  it('treats only UNAUTHENTICATED as session expiry, not a failed login', () => {
    expect(isUnauthenticated(new ApiError(401, { code: 'UNAUTHENTICATED', message: 'x' }))).toBe(true);
    expect(isUnauthenticated(new ApiError(401, { code: 'AUTH_INVALID_CREDENTIALS', message: 'x' }))).toBe(false);
  });
});
