import { Alert, Button, Form, Input } from 'antd';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useLogin, useMe, useSystemInfo } from '../../hooks/queries';
import { AuthLayout } from './AuthLayout';

interface LoginForm {
  username: string;
  password: string;
}

/** Only allow in-app redirects after login (no open redirect via ?next=). */
function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/mail/inbox';
}

export function LoginPage() {
  const me = useMe();
  const info = useSystemInfo();
  const login = useLogin();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const location = useLocation();
  const prefill = (location.state as { username?: string } | null)?.username ?? '';
  const next = safeNext(params.get('next'));

  if (me.data) {
    return <Navigate to={next} replace />;
  }

  const onFinish = (values: LoginForm) => {
    login.mutate(
      { username: values.username.trim(), password: values.password },
      { onSuccess: () => navigate(next, { replace: true }) },
    );
  };

  return (
    <AuthLayout title="登录" subtitle={info.data ? `使用你在 ${info.data.domain} 的账号` : '使用你的企业邮箱账号'}>
      {prefill && !login.isError && (
        <Alert type="success" showIcon style={{ marginBottom: 20 }} message="账号已创建，请登录。" />
      )}
      {login.isError && (
        <Alert type="error" showIcon style={{ marginBottom: 20 }} message={errorMessage(login.error)} />
      )}
      <Form<LoginForm> layout="vertical" requiredMark={false} onFinish={onFinish} initialValues={{ username: prefill }}>
        <Form.Item
          label="用户名或邮箱地址"
          name="username"
          rules={[{ required: true, message: '请输入用户名' }]}
        >
          <Input autoComplete="username" autoFocus={!prefill} size="large" />
        </Form.Item>
        <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password autoComplete="current-password" autoFocus={Boolean(prefill)} size="large" />
        </Form.Item>
        <Button type="primary" htmlType="submit" size="large" block loading={login.isPending}>
          登录
        </Button>
      </Form>
      <p style={{ marginTop: 24, color: 'var(--ink-soft)' }}>
        还没有账号？<Link to="/register">注册</Link>
      </p>
    </AuthLayout>
  );
}
