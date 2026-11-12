import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import './styles.css';

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { isUnauthenticated } from './api/errors';
import { handleSessionExpired } from './hooks/queries';
import { router } from './router';
import { theme } from './theme';

dayjs.locale('zh-cn');

// Any request answered with UNAUTHENTICATED ends the client-side session; the route guard
// then sends the user to the login page.
const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => isUnauthenticated(error) && handleSessionExpired(queryClient),
  }),
  mutationCache: new MutationCache({
    onError: (error) => isUnauthenticated(error) && handleSessionExpired(queryClient),
  }),
  defaultOptions: {
    queries: {
      retry: (count, error) => !isUnauthenticated(error) && count < 1,
      refetchOnWindowFocus: true,
    },
  },
});

const root = document.getElementById('root');
if (!root) {
  throw new Error('#root missing');
}

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ConfigProvider theme={theme} locale={zhCN} button={{ autoInsertSpace: false }}>
        <AntApp>
          <RouterProvider router={router} />
        </AntApp>
      </ConfigProvider>
    </QueryClientProvider>
  </StrictMode>,
);
