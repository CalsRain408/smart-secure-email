import { Alert, App, Button, Drawer, Form, Input, Popconfirm, Select, Space, Tag } from 'antd';
import { useMemo, useState } from 'react';
import { errorMessage, fieldErrors, recipientProblems } from '../../api/errors';
import type { ComposeRequest } from '../../api/types';
import { useDeleteDraft, useSaveDraft, useSendDraft, useSendMail } from '../../hooks/queries';
import { EMPTY_COMPOSE, isValidAddress, normalizeAddress } from '../../lib/compose';
import type { ComposeInit } from './compose-context';

interface Props {
  init: ComposeInit;
  onClose: () => void;
  onSent: (sentItemId: number) => void;
}

type FieldName = 'to' | 'cc' | 'bcc';

const MAX_RECIPIENTS = 50;

function normalize(values: Partial<ComposeRequest>): ComposeRequest {
  const list = (v?: string[]) => [...new Set((v ?? []).map(normalizeAddress).filter(Boolean))];
  return {
    to: list(values.to),
    cc: list(values.cc),
    bcc: list(values.bcc),
    subject: values.subject ?? '',
    body: values.body ?? '',
  };
}

/** The visible <label> of the surrounding Form.Item names this input (via its id). */
function RecipientSelect({ id, value, onChange }: {
  id?: string;
  value?: string[];
  onChange?: (v: string[]) => void;
}) {
  const [typing, setTyping] = useState('');
  // Commit text that was typed but not yet turned into a tag (e.g. user clicks "发送" directly).
  const commitTyping = () => {
    const pending = typing
      .split(/[,，;；\s]+/)
      .map(normalizeAddress)
      .filter(Boolean);
    if (pending.length > 0) {
      onChange?.([...new Set([...(value ?? []), ...pending])]);
    }
    setTyping('');
  };
  return (
    <Select
      id={id}
      mode="tags"
      value={value}
      onChange={(v: string[]) => {
        setTyping('');
        onChange?.(v);
      }}
      searchValue={typing}
      onSearch={setTyping}
      onBlur={commitTyping}
      open={false}
      suffixIcon={null}
      tokenSeparators={[',', '，', ';', '；', ' ']}
      placeholder="输入地址，按回车或逗号分隔"
      tagRender={({ label: text, value: v, closable, onClose }) => {
        const ok = isValidAddress(String(v));
        return (
          <Tag
            closable={closable}
            onClose={onClose}
            color={ok ? undefined : 'error'}
            style={{ marginInlineEnd: 4 }}
            title={ok ? String(v) : '地址格式不正确'}
          >
            {text}
          </Tag>
        );
      }}
    />
  );
}

export function ComposeDrawer({ init, onClose, onSent }: Props) {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<ComposeRequest>();
  const initial = useMemo(() => normalize(init.values ?? EMPTY_COMPOSE), [init.values]);
  const [draftId, setDraftId] = useState<number | null>(init.draftId ?? null);
  const [savedSnapshot, setSavedSnapshot] = useState(JSON.stringify(initial));
  const [showCc, setShowCc] = useState(initial.cc.length > 0);
  const [showBcc, setShowBcc] = useState(initial.bcc.length > 0);
  const [error, setError] = useState<unknown>(null);

  const sendMail = useSendMail();
  const saveDraft = useSaveDraft();
  const sendDraft = useSendDraft();
  const deleteDraft = useDeleteDraft();
  const busy = sendMail.isPending || saveDraft.isPending || sendDraft.isPending || deleteDraft.isPending;

  const current = () => normalize(form.getFieldsValue(true));
  const isDirty = () => JSON.stringify(current()) !== savedSnapshot;

  const problems = recipientProblems(error);
  const serverFields = fieldErrors(error);

  const validateRecipients = (values: ComposeRequest): boolean => {
    const invalid = [...values.to, ...values.cc, ...values.bcc].filter((a) => !isValidAddress(a));
    if (invalid.length > 0) {
      message.error(`地址格式不正确：${invalid.join('，')}`);
      return false;
    }
    if (values.to.length + values.cc.length + values.bcc.length > MAX_RECIPIENTS) {
      message.error(`一封邮件最多 ${MAX_RECIPIENTS} 位收件人`);
      return false;
    }
    return true;
  };

  const onSend = async () => {
    const values = current();
    if (values.to.length + values.cc.length + values.bcc.length === 0) {
      form.setFields([{ name: 'to', errors: ['至少填写一位收件人'] }]);
      return;
    }
    if (!validateRecipients(values)) {
      return;
    }
    setError(null);
    try {
      let sent;
      if (draftId === null) {
        sent = await sendMail.mutateAsync(values);
      } else {
        await saveDraft.mutateAsync({ id: draftId, body: values });
        sent = await sendDraft.mutateAsync(draftId);
      }
      message.success('已发送');
      if (sent) {
        onSent(sent.id);
      }
    } catch (e) {
      setError(e);
    }
  };

  const onSaveDraft = async () => {
    const values = current();
    if (!validateRecipients(values)) {
      return;
    }
    setError(null);
    try {
      const saved = await saveDraft.mutateAsync({ id: draftId, body: values });
      if (saved) {
        setDraftId(saved.id);
      }
      setSavedSnapshot(JSON.stringify(values));
      message.success('草稿已保存');
    } catch (e) {
      setError(e);
    }
  };

  const onDeleteDraft = async () => {
    if (draftId === null) {
      return;
    }
    try {
      await deleteDraft.mutateAsync(draftId);
      message.success('草稿已删除');
      onClose();
    } catch (e) {
      setError(e);
    }
  };

  const requestClose = () => {
    if (!isDirty()) {
      onClose();
      return;
    }
    modal.confirm({
      title: '放弃未保存的修改？',
      content: '关闭后这次的修改不会保留。需要保留的话，请先保存草稿。',
      okText: '放弃修改',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: onClose,
    });
  };

  const recipientItem = (name: FieldName, label: string) => (
    <Form.Item
      label={label}
      name={name}
      validateStatus={serverFields[name] ? 'error' : undefined}
      help={serverFields[name]}
    >
      <RecipientSelect />
    </Form.Item>
  );

  return (
    <Drawer
      open
      title={init.title ?? (draftId === null ? '写邮件' : '编辑草稿')}
      width="min(720px, 100vw)"
      onClose={requestClose}
      maskClosable={false}
      destroyOnHidden
      footer={
        <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Space>
            <Button type="primary" onClick={onSend} loading={sendMail.isPending || sendDraft.isPending} disabled={busy}>
              发送
            </Button>
            <Button onClick={onSaveDraft} loading={saveDraft.isPending && !sendDraft.isPending} disabled={busy}>
              保存草稿
            </Button>
          </Space>
          {draftId !== null && (
            <Popconfirm
              title="删除这封草稿？"
              description="删除后无法恢复。"
              okText="删除"
              okButtonProps={{ danger: true }}
              cancelText="取消"
              onConfirm={onDeleteDraft}
            >
              <Button danger type="text" disabled={busy}>
                删除草稿
              </Button>
            </Popconfirm>
          )}
        </Space>
      }
    >
      {error !== null && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message={errorMessage(error)}
          description={
            problems.length > 0 ? (
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {problems.map((p) => (
                  <li key={p.address}>
                    {p.address}：{p.reason}
                  </li>
                ))}
              </ul>
            ) : undefined
          }
        />
      )}
      <Form<ComposeRequest> form={form} layout="vertical" initialValues={initial} requiredMark={false}>
        {recipientItem('to', '收件人')}
        {(!showCc || !showBcc) && (
          <Space size={4} style={{ marginTop: -12, marginBottom: 12 }}>
            {!showCc && (
              <Button type="link" size="small" onClick={() => setShowCc(true)} style={{ paddingInline: 0 }}>
                添加抄送
              </Button>
            )}
            {!showBcc && (
              <Button type="link" size="small" onClick={() => setShowBcc(true)} style={{ paddingInline: 4 }}>
                添加密送
              </Button>
            )}
          </Space>
        )}
        {showCc && recipientItem('cc', '抄送')}
        {showBcc && recipientItem('bcc', '密送（其他收件人看不到）')}
        <Form.Item label="主题" name="subject" rules={[{ max: 255, message: '主题最多 255 个字符' }]}>
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item label="正文" name="body" rules={[{ max: 100_000, message: '正文太长' }]}>
          <Input.TextArea autoSize={{ minRows: 12, maxRows: 28 }} maxLength={100_000} />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
