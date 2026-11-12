import type { ComposeRequest, MailDetail } from '../api/types';
import { fullTime } from './time';

export const EMPTY_COMPOSE: ComposeRequest = { to: [], cc: [], bcc: [], subject: '', body: '' };

/** Same lenient-but-safe shape the server accepts; the server stays authoritative. */
const ADDRESS = /^[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{0,62}$/;

export function normalizeAddress(raw: string): string {
  return raw.trim().toLowerCase();
}

export function isValidAddress(raw: string): boolean {
  const value = normalizeAddress(raw);
  return value.length <= 320 && ADDRESS.test(value);
}

function replySubject(subject: string): string {
  return /^re:/i.test(subject.trim()) ? subject.trim() : `Re: ${subject.trim()}`;
}

function quote(detail: MailDetail): string {
  const header = `${fullTime(detail.sentAt)}，${detail.from.displayName ?? detail.from.address} <${detail.from.address}> 写道：`;
  const quoted = detail.body
    .split('\n')
    .map((line) => `> ${line}`)
    .join('\n');
  return `\n\n${header}\n${quoted}`;
}

/**
 * Prefills a reply. "Reply all" keeps the other visible recipients (TO+CC) in CC, never
 * the viewer themself and never BCC (which a recipient does not know about anyway).
 */
export function buildReply(detail: MailDetail, myAddress: string, replyAll: boolean): ComposeRequest {
  const me = normalizeAddress(myAddress);
  const to = [detail.from.address].filter((a) => normalizeAddress(a) !== me);
  const cc = replyAll
    ? [...new Set([...detail.to, ...detail.cc].map(normalizeAddress))].filter(
        (a) => a !== me && !to.map(normalizeAddress).includes(a),
      )
    : [];
  return {
    to: to.length > 0 ? to : detail.to,
    cc,
    bcc: [],
    subject: replySubject(detail.subject),
    body: quote(detail),
  };
}

export function fromDraft(detail: MailDetail): ComposeRequest {
  return {
    to: detail.to,
    cc: detail.cc,
    bcc: detail.bcc ?? [],
    subject: detail.subject,
    body: detail.body,
  };
}

export function isBlank(c: ComposeRequest): boolean {
  return c.to.length + c.cc.length + c.bcc.length === 0 && !c.subject.trim() && !c.body.trim();
}
