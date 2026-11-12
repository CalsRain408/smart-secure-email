import { createBrowserRouter, Navigate } from 'react-router';
import { RequireAuth } from './components/RequireAuth';
import { LoginPage } from './features/auth/LoginPage';
import { RegisterPage } from './features/auth/RegisterPage';
import { MailShell } from './features/mail/MailShell';
import { MailboxView } from './features/mail/MailboxView';
import { SettingsPage } from './features/settings/SettingsPage';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <MailShell />,
        children: [
          { path: '/mail/:folder', element: <MailboxView /> },
          { path: '/mail/:folder/:id', element: <MailboxView /> },
          { path: '/settings', element: <SettingsPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/mail/inbox" replace /> },
]);
