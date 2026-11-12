import type { MailFolder } from '../../api/types';

export const FOLDER_BY_SLUG: Record<string, MailFolder> = {
  inbox: 'INBOX',
  sent: 'SENT',
  drafts: 'DRAFTS',
};

export const FOLDER_TITLE: Record<MailFolder, string> = {
  INBOX: '收件箱',
  SENT: '已发送',
  DRAFTS: '草稿箱',
  TRASH: '废纸篓',
};
