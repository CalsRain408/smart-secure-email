import { Button, Descriptions } from 'antd';
import { useNavigate } from 'react-router';
import { DomainMark } from '../../components/DomainMark';
import { useLogout, useMe, useSystemInfo } from '../../hooks/queries';

const ROLE_LABEL = { ADMIN: '管理员', MEMBER: '成员' } as const;

/** Basic account page. Profile/password changes are not offered by the server yet. */
export function SettingsPage() {
  const me = useMe();
  const info = useSystemInfo();
  const logout = useLogout();
  const navigate = useNavigate();
  const user = me.data;
  const secure = window.location.protocol === 'https:';

  return (
    <section className="pane" aria-label="账户">
      <div className="pane__body">
        <div className="settings">
          <h1 className="pane__title" style={{ marginBottom: 20 }}>账户</h1>

          <div className="settings__section">
            <h2>个人信息</h2>
            <Descriptions column={1} size="small" colon={false} labelStyle={{ width: 96, color: 'var(--ink-faint)' }}>
              <Descriptions.Item label="显示名称">{user?.displayName}</Descriptions.Item>
              <Descriptions.Item label="邮箱地址">{user?.address}</Descriptions.Item>
              <Descriptions.Item label="用户名">{user?.username}</Descriptions.Item>
              <Descriptions.Item label="角色">{user ? ROLE_LABEL[user.role] : ''}</Descriptions.Item>
            </Descriptions>
          </div>

          <div className="settings__section">
            <h2>所属域名</h2>
            <Descriptions column={1} size="small" colon={false} labelStyle={{ width: 96, color: 'var(--ink-faint)' }}>
              <Descriptions.Item label="域名">
                {info.data && (
                  <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
                    <DomainMark domain={info.data.domain} /> {info.data.domain}
                  </span>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="组织">{info.data?.displayName}</Descriptions.Item>
              <Descriptions.Item label="服务版本">{info.data?.serverVersion}</Descriptions.Item>
            </Descriptions>
          </div>

          <div className="settings__section">
            <h2>会话与安全</h2>
            <Descriptions column={1} size="small" colon={false} labelStyle={{ width: 96, color: 'var(--ink-faint)' }}>
              <Descriptions.Item label="连接">
                {secure ? '已加密（HTTPS）' : '未加密（HTTP，仅适用于本地开发）'}
              </Descriptions.Item>
              <Descriptions.Item label="自动退出">闲置 30 分钟后需要重新登录</Descriptions.Item>
            </Descriptions>
            <Button
              style={{ marginTop: 12 }}
              loading={logout.isPending}
              onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) })}
            >
              退出登录
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
}
