import { describe, expect, it } from 'vitest';
import type { MailDetail } from '../api/types';
import { buildReply, isValidAddress } from './compose';

const mail: MailDetail = {
  id: 9,
  folder: 'INBOX',
  messageId: 'm@domain-b.test',
  status: 'SENT',
  from: { address: 'bob@domain-b.test', displayName: 'Bob' },
  to: ['alice@domain-a.test', 'carol@domain-a.test'],
  cc: ['dave@domain-b.test', 'ALICE@domain-a.test'],
  subject: 'Q4 plan',
  body: 'line 1\nline 2',
  read: true,
  starred: false,
  sentAt: '2026-11-12T08:00:00Z',
  updatedAt: '2026-11-12T08:00:00Z',
};

describe('buildReply', () => {
  it('replies to the sender only, with a Re: subject and quoted body', () => {
    const reply = buildReply(mail, 'alice@domain-a.test', false);
    expect(reply.to).toEqual(['bob@domain-b.test']);
    expect(reply.cc).toEqual([]);
    expect(reply.bcc).toEqual([]);
    expect(reply.subject).toBe('Re: Q4 plan');
    expect(reply.body).toContain('> line 1\n> line 2');
  });

  it('reply-all keeps other visible recipients but never the viewer', () => {
    const reply = buildReply(mail, 'alice@domain-a.test', true);
    expect(reply.to).toEqual(['bob@domain-b.test']);
    expect(reply.cc).toEqual(['carol@domain-a.test', 'dave@domain-b.test']);
  });

  it('does not stack Re: prefixes', () => {
    expect(buildReply({ ...mail, subject: 'RE: Q4 plan' }, 'alice@domain-a.test', false).subject).toBe('RE: Q4 plan');
  });

  it('replying to your own sent mail addresses its recipients', () => {
    const own = { ...mail, from: { address: 'alice@domain-a.test' }, to: ['bob@domain-b.test'], cc: [] };
    expect(buildReply(own, 'alice@domain-a.test', false).to).toEqual(['bob@domain-b.test']);
  });
});

describe('isValidAddress', () => {
  it.each(['bob@domain-b.test', ' Bob@Domain-B.Test '])('accepts %s', (a) => expect(isValidAddress(a)).toBe(true));
  it.each(['bob', 'bob@', 'Bob <bob@x.test>', 'bob@localhost', 'a@b@c.test'])('rejects %s', (a) =>
    expect(isValidAddress(a)).toBe(false),
  );
});
