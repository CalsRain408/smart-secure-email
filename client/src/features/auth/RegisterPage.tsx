import { Alert, Button, Form, Input } from 'antd';
import { Link, useNavigate } from 'react-router';
import { errorMessage, fieldErrors } from '../../api/errors';
import { useRegister, useSystemInfo } from '../../hooks/queries';
import { AuthLayout } from './AuthLayout';

interface RegisterForm {
  username: string;
  displayName: string;
  password: string;
  confirmPassword: string;
}

// Mirrors the server rules for immediate feedback; the server re-validates everything.
const USERNAME = /^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,30}[A-Za-z0-9])$/;
const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/;

export function RegisterPage() {
  const info = useSystemInfo();
  const register = useRegister();
  const navigate = useNavigate();
  const [form] = Form.useForm<RegisterForm>();
  const serverFieldErrors = fieldErrors(register.error);

  const onFinish = (values: RegisterForm) => {
    register.mutate(
      {
        username: values.username.trim(),
        displayName: values.displayName.trim(),
        password: values.password,
        confirmPassword: values.confirmPassword,
      },
      { onSuccess: (user) => navigate('/login', { state: { username: user.username } }) },
    );
  };

  return (
    <AuthLayout title="创建账号" subtitle={info.data ? `新邮箱地址将是 用户名@${info.data.domain}` : '注册企业邮箱'}>
      {register.isError && (
        <Alert type="error" showIcon style={{ marginBottom: 20 }} message={errorMessage(register.error)} />
      )}
      <Form<RegisterForm> form={form} layout="vertical" requiredMark={false} onFinish={onFinish}>
        <Form.Item
          label="用户名"
          name="username"
          extra="3–32 位，可用字母、数字和 . _ -，不区分大小写"
          validateStatus={serverFieldErrors.username ? 'error' : undefined}
          help={serverFieldErrors.username}
          rules={[
            { required: true, message: '请输入用户名' },
            { pattern: USERNAME, message: '用户名格式不正确' },
          ]}
        >
          <Input
            autoComplete="username"
            autoFocus
            addonAfter={info.data ? `@${info.data.domain}` : undefined}
          />
        </Form.Item>
        <Form.Item
          label="显示名称"
          name="displayName"
          extra="收件人会看到这个名字"
          rules={[
            { required: true, whitespace: true, message: '请输入显示名称' },
            { max: 64, message: '最多 64 个字符' },
          ]}
        >
          <Input autoComplete="name" />
        </Form.Item>
        <Form.Item
          label="密码"
          name="password"
          extra="8–64 位，至少包含一个字母和一个数字，不能包含用户名"
          validateStatus={serverFieldErrors.password ? 'error' : undefined}
          help={serverFieldErrors.password}
          rules={[
            { required: true, message: '请输入密码' },
            { pattern: PASSWORD, message: '密码需 8–64 位，并包含字母和数字' },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          label="确认密码"
          name="confirmPassword"
          dependencies={['password']}
          rules={[
            { required: true, message: '请再次输入密码' },
            ({ getFieldValue }) => ({
              validator: (_, value) =>
                !value || getFieldValue('password') === value
                  ? Promise.resolve()
                  : Promise.reject(new Error('两次输入的密码不一致')),
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" size="large" block loading={register.isPending}>
          创建账号
        </Button>
      </Form>
      <p style={{ marginTop: 24, color: 'var(--ink-soft)' }}>
        已有账号？<Link to="/login">登录</Link>
      </p>
    </AuthLayout>
  );
}
