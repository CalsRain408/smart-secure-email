import { ArrowLeftOutlined, StarFilled, StarOutlined } from '@ant-design/icons';
import { Alert, App, Button, Popconfirm, Skeleton } from 'antd';
import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router';
import { errorMessage } from '../../api/errors';
import type { MailDetail, MailFolder } from '../../api/types';
import { AddressTag } from '../../components/AddressTag';
import { DeliveryRoute } from '../../components/DeliveryRoute';
import { MailBody } from '../../components/MailBody';
import { Postmark } from '../../components/Postmark';
import { useDeleteDraft, useMailDetail, useMe, useSystemInfo, useUpdateFlags } from '../../hooks/queries';
import { buildReply, fromDraft } from '../../lib/compose';
import { domainOf } from '../../lib/domain';
import { fullTime } from '../../lib/time';
import { useCompose } from './compose-context';

interface Props {
  folder: MailFolder;
  slug: string;
  selectedId: number | null;
}

export function ReaderPane({ folder, slug, selectedId }: Props) {
  const detail = useMailDetail(selectedId);
  const mail = detail.data;

  return (
    <section className="pane pane--reader" aria-label="邮件内容">
      <div className="pane__body">
        <Link to={`/mail/${slug}`} className="mobile-only navlink" style={{ margin: '12px 8px 0' }}>
          <ArrowLeftOutlined /> 返回列表
        </Link>
        {selectedId === null && (
          <div className="empty">
            <p className="empty__title">选择一封邮件</p>
            <p>从左侧列表打开邮件后，内容和投递回执会显示在这里。</p>
          </div>
        )}
        {selectedId !== null && detail.isPending && (
          <div className="reader">
            <Skeleton active title={{ width: '60%' }} paragraph={{ rows: 8 }} />
          </div>
        )}
        {detail.isError && (
          <Alert type="error" showIcon style={{ margin: 24 }} message={errorMessage(detail.error)} />
        )}
        {mail && <Reader mail={mail} folder={folder} slug={slug} />}
      </div>
    </section>
  );
}

function Reader({ mail, folder, slug }: { mail: MailDetail; folder: MailFolder; slug: string }) {
  const me = useMe();
  const info = useSystemInfo();
  const flags = useUpdateFlags();
  const deleteDraft = useDeleteDraft();
  const { openCompose } = useCompose();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const localDomain = info.data?.domain ?? '';
  const myAddress = me.data?.address ?? '';
  const originDomain = domainOf(mail.from.address);
  const isDraft = mail.status === 'DRAFT';

  // Opening an unread message marks it read (explicit user action: they opened it).
  const { mutate: updateFlags } = flags;
  useEffect(() => {
    if (folder === 'INBOX' && !mail.read) {
      updateFlags({ id: mail.id, read: true });
    }
  }, [folder, mail.id, mail.read, updateFlags]);

  const reply = (all: boolean) =>
    openCompose({ values: buildReply(mail, myAddress, all), title: all ? '回复全部' : '回复' });

  const onDeleteDraft = () =>
    deleteDraft.mutate(mail.id, {
      onSuccess: () => {
        message.success('草稿已删除');
        navigate(`/mail/${slug}`, { replace: true });
      },
      onError: (e) => message.error(errorMessage(e)),
    });

  return (
    <article className="reader">
      <h2 className="reader__subject">{mail.subject || '（无主题）'}</h2>

      <div className="reader__meta">
        <Postmark domain={originDomain} sentAt={mail.sentAt} />
        <div>
          <div className="reader__parties">
            <span className="reader__label">发件人</span>
            <span>
              <AddressTag address={mail.from.address} displayName={mail.from.displayName} localDomain={localDomain} />
            </span>
            <span className="reader__label">收件人</span>
            <span>
              {mail.to.length === 0 ? <span className="reader__label">无</span> : mail.to.map((a) => (
                <AddressTag key={a} address={a} localDomain={localDomain} />
              ))}
            </span>
            {mail.cc.length > 0 && (
              <>
                <span className="reader__label">抄送</span>
                <span>
                  {mail.cc.map((a) => <AddressTag key={a} address={a} localDomain={localDomain} />)}
                </span>
              </>
            )}
            {mail.bcc && mail.bcc.length > 0 && (
              <>
                <span className="reader__label">密送</span>
                <span>
                  {mail.bcc.map((a) => <AddressTag key={a} address={a} localDomain={localDomain} />)}
                </span>
              </>
            )}
            <span className="reader__label">时间</span>
            <span className="tnum">{isDraft ? `草稿，最后保存于 ${fullTime(mail.updatedAt)}` : fullTime(mail.sentAt)}</span>
          </div>

          <div className="reader__actions">
            {isDraft ? (
              <>
                <Button type="primary" onClick={() => openCompose({ draftId: mail.id, values: fromDraft(mail) })}>
                  继续编辑
                </Button>
                <Popconfirm
                  title="删除这封草稿？"
                  description="删除后无法恢复。"
                  okText="删除"
                  okButtonProps={{ danger: true }}
                  cancelText="取消"
                  onConfirm={onDeleteDraft}
                >
                  <Button danger loading={deleteDraft.isPending}>删除草稿</Button>
                </Popconfirm>
              </>
            ) : (
              <>
                <Button onClick={() => reply(false)}>回复</Button>
                <Button onClick={() => reply(true)}>回复全部</Button>
                <Button
                  icon={mail.starred ? <StarFilled style={{ color: 'var(--pending)' }} /> : <StarOutlined />}
                  onClick={() => flags.mutate({ id: mail.id, starred: !mail.starred })}
                  aria-pressed={mail.starred}
                >
                  {mail.starred ? '已加星标' : '加星标'}
                </Button>
                {folder === 'INBOX' && mail.read && (
                  <Button type="text" onClick={() => flags.mutate({ id: mail.id, read: false })}>
                    标为未读
                  </Button>
                )}
              </>
            )}
          </div>

          {!isDraft && mail.delivery && mail.delivery.length > 0 && (
            <DeliveryRoute originDomain={originDomain} delivery={mail.delivery} />
          )}
        </div>
      </div>

      <MailBody text={mail.body} />
    </article>
  );
}
