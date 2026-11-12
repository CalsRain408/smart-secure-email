import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Pagination, Skeleton } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import type { MailFolder, MailSummary } from '../../api/types';
import { useMailList, useSystemInfo, PAGE_SIZE } from '../../hooks/queries';
import { domainOf, localPart } from '../../lib/domain';
import { listTime } from '../../lib/time';
import { useCompose } from './compose-context';
import { FOLDER_TITLE } from './folders';

interface Props {
  folder: MailFolder;
  slug: string;
  selectedId: number | null;
}

const EMPTY: Record<MailFolder, { title: string; text: string }> = {
  INBOX: { title: '收件箱是空的', text: '同事或其他域名发给你的邮件会出现在这里。' },
  SENT: { title: '还没有发出的邮件', text: '发出的邮件会带着逐个收件人的投递回执显示在这里。' },
  DRAFTS: { title: '没有草稿', text: '写到一半的邮件可以先保存为草稿，稍后继续。' },
  TRASH: { title: '废纸篓是空的', text: '' },
};

function shortAddress(address: string, localDomain: string): string {
  return domainOf(address) === localDomain ? localPart(address) : address;
}

function who(mail: MailSummary, localDomain: string): string {
  if (mail.folder === 'INBOX') {
    return mail.from.displayName || shortAddress(mail.from.address, localDomain);
  }
  const to = mail.to ?? [];
  if (to.length === 0) {
    return mail.folder === 'DRAFTS' ? '（未填写收件人）' : '（仅抄送或密送）';
  }
  const first = shortAddress(to[0] ?? '', localDomain);
  return `致 ${first}${to.length > 1 ? ` 等 ${to.length} 人` : ''}`;
}

export function MailListPane({ folder, slug, selectedId }: Props) {
  const [page, setPage] = useState(0);
  const list = useMailList(folder, page);
  const info = useSystemInfo();
  const { openCompose } = useCompose();
  const localDomain = info.data?.domain ?? '';
  const data = list.data;

  return (
    <section className="pane pane--list" aria-label={FOLDER_TITLE[folder]}>
      <header className="pane__head">
        <h1 className="pane__title">
          {FOLDER_TITLE[folder]}
          {data && <span className="pane__count tnum">{data.totalElements} 封</span>}
        </h1>
        <Button
          type="text"
          icon={<ReloadOutlined spin={list.isFetching && !list.isPending} />}
          onClick={() => list.refetch()}
          aria-label="刷新"
        />
      </header>

      <div className="pane__body">
        {list.isPending && <Skeleton active paragraph={{ rows: 6 }} style={{ padding: 20 }} />}
        {list.isError && (
          <Alert
            type="error"
            showIcon
            style={{ margin: 16 }}
            message={errorMessage(list.error)}
            action={<Button size="small" onClick={() => list.refetch()}>重试</Button>}
          />
        )}
        {data && data.items.length === 0 && (
          <div className="empty">
            <p className="empty__title">{EMPTY[folder].title}</p>
            <p>{EMPTY[folder].text}</p>
            {folder === 'DRAFTS' && <Button onClick={() => openCompose()}>写邮件</Button>}
          </div>
        )}
        {data && data.items.length > 0 && (
          <ul className="maillist">
            {data.items.map((mail) => (
              <li key={mail.id}>
                <Link
                  to={`/mail/${slug}/${mail.id}`}
                  className={`mailrow${!mail.read ? ' mailrow--unread' : ''}`}
                  aria-current={mail.id === selectedId ? 'true' : undefined}
                >
                  <div className="mailrow__top">
                    <span className="mailrow__who">
                      {!mail.read && <span className="sr-only">未读 </span>}
                      {who(mail, localDomain)}
                    </span>
                    <time className="mailrow__time tnum" dateTime={mail.sentAt ?? mail.updatedAt}>
                      {listTime(mail.sentAt ?? mail.updatedAt)}
                    </time>
                  </div>
                  <div className="mailrow__subject">{mail.subject || '（无主题）'}</div>
                  <div className="mailrow__preview">{mail.preview || '（无正文）'}</div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <footer className="pane__foot">
          <Pagination
            simple
            size="small"
            current={page + 1}
            pageSize={PAGE_SIZE}
            total={data.totalElements}
            onChange={(p) => setPage(p - 1)}
          />
        </footer>
      )}
    </section>
  );
}
