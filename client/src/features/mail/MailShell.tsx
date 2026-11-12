import { EditOutlined, FileTextOutlined, InboxOutlined, LogoutOutlined, SendOutlined, SettingOutlined } from '@ant-design/icons';
import { App, Button } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { NavLink, Outlet, useMatch, useNavigate } from 'react-router';
import { errorMessage } from '../../api/errors';
import { DomainMark } from '../../components/DomainMark';
import { useLogout, useMe, useSystemInfo } from '../../hooks/queries';
import { ComposeDrawer } from './ComposeDrawer';
import { ComposeContext, type ComposeInit } from './compose-context';

const FOLDERS = [
  { to: '/mail/inbox', label: '收件箱', icon: <InboxOutlined /> },
  { to: '/mail/sent', label: '已发送', icon: <SendOutlined /> },
  { to: '/mail/drafts', label: '草稿箱', icon: <FileTextOutlined /> },
];

export function MailShell() {
  const me = useMe();
  const info = useSystemInfo();
  const logout = useLogout();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [compose, setCompose] = useState<{ key: number; init: ComposeInit } | null>(null);

  const reading = useMatch('/mail/:folder/:id') !== null;
  const wide = useMatch('/settings') !== null;

  const openCompose = useCallback((init: ComposeInit = {}) => setCompose({ key: Date.now(), init }), []);
  const ctx = useMemo(() => ({ openCompose }), [openCompose]);

  const onLogout = () =>
    logout.mutate(undefined, {
      onSuccess: () => navigate('/login', { replace: true }),
      onError: (error) => message.error(errorMessage(error)),
    });

  const domain = info.data?.domain ?? '';

  return (
    <ComposeContext.Provider value={ctx}>
      <div className={`shell${wide ? ' shell--wide' : ''}${reading ? ' shell--reading' : ''}`}>
        <aside className="sidebar" aria-label="邮箱导航">
          <div className="sidebar__identity">
            <div className="sidebar__domain">
              {domain && <DomainMark domain={domain} />}
              {domain}
            </div>
            <div className="sidebar__org">{info.data?.displayName}</div>
          </div>

          <Button type="primary" icon={<EditOutlined />} onClick={() => openCompose()} size="large">
            写邮件
          </Button>

          <nav className="sidebar__nav">
            {FOLDERS.map((f) => (
              <NavLink key={f.to} to={f.to} className="navlink">
                {f.icon}
                {f.label}
              </NavLink>
            ))}
          </nav>

          <div className="sidebar__footer">
            <div className="sidebar__me">
              {me.data?.displayName}
              <small>{me.data?.address}</small>
            </div>
            <NavLink to="/settings" className="navlink">
              <SettingOutlined />
              <span className="navlink__text">账户</span>
            </NavLink>
            <button type="button" className="navlink" onClick={onLogout} disabled={logout.isPending}>
              <LogoutOutlined />
              <span className="navlink__text">退出登录</span>
            </button>
          </div>
        </aside>

        <Outlet />
      </div>

      {compose && (
        <ComposeDrawer
          key={compose.key}
          init={compose.init}
          onClose={() => setCompose(null)}
          onSent={(sentId) => {
            setCompose(null);
            navigate(`/mail/sent/${sentId}`);
          }}
        />
      )}
    </ComposeContext.Provider>
  );
}
