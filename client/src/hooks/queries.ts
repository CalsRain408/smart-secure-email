import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { authApi, mailApi, systemApi } from '../api/endpoints';
import { resetCsrf } from '../api/http';
import { isUnauthenticated } from '../api/errors';
import type { ComposeRequest, CurrentUser, MailDetail, MailFolder } from '../api/types';

export const PAGE_SIZE = 20;

export const keys = {
  system: ['system'] as const,
  me: ['me'] as const,
  mails: ['mails'] as const,
  list: (folder: MailFolder, page: number) => ['mails', 'list', folder, page] as const,
  detail: (id: number) => ['mails', 'detail', id] as const,
};

/** Delivery states that will still change on their own (cross-domain delivery in progress). */
const IN_FLIGHT = new Set(['PENDING', 'RETRYING']);

export function useSystemInfo() {
  return useQuery({ queryKey: keys.system, queryFn: systemApi.info, staleTime: Infinity });
}

/** Current user, or null when not signed in (401 is an answer here, not an error). */
export function useMe() {
  return useQuery<CurrentUser | null>({
    queryKey: keys.me,
    queryFn: async () => {
      try {
        return await authApi.me();
      } catch (error) {
        if (isUnauthenticated(error)) {
          return null;
        }
        throw error;
      }
    },
    staleTime: 5 * 60_000,
    retry: false,
  });
}

/** Called by the global error hooks: any UNAUTHENTICATED response ends the client session. */
export function handleSessionExpired(client: QueryClient): void {
  resetCsrf();
  client.setQueryData(keys.me, null);
  client.removeQueries({ queryKey: keys.mails });
}

export function useLogin() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (user) => {
      resetCsrf(); // the server rotated the token together with the session id
      client.removeQueries({ queryKey: keys.mails });
      client.setQueryData(keys.me, user);
    },
  });
}

export function useRegister() {
  return useMutation({ mutationFn: authApi.register });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      resetCsrf();
      client.removeQueries({ queryKey: keys.mails });
      client.setQueryData(keys.me, null);
    },
  });
}

export function useMailList(folder: MailFolder, page: number) {
  return useQuery({
    queryKey: keys.list(folder, page),
    queryFn: () => mailApi.list(folder, page, PAGE_SIZE),
    placeholderData: (previous) => previous,
    // New mail can arrive from either domain at any time.
    refetchInterval: folder === 'INBOX' ? 15_000 : false,
  });
}

export function useMailDetail(id: number | null) {
  return useQuery({
    queryKey: keys.detail(id ?? -1),
    queryFn: () => mailApi.get(id as number),
    enabled: id !== null,
    // While a cross-domain delivery is in flight, follow it until it settles.
    refetchInterval: (query) =>
      query.state.data?.delivery?.some((d) => IN_FLIGHT.has(d.status)) ? 1500 : false,
  });
}

function useMailMutation<TArgs>(fn: (args: TArgs) => Promise<MailDetail | void>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (result) => {
      if (result) {
        client.setQueryData(keys.detail(result.id), result);
      }
      return client.invalidateQueries({ queryKey: keys.mails });
    },
  });
}

export function useSendMail() {
  return useMailMutation((body: ComposeRequest) => mailApi.send(body));
}

export function useSaveDraft() {
  return useMailMutation(({ id, body }: { id: number | null; body: ComposeRequest }) =>
    id === null ? mailApi.createDraft(body) : mailApi.updateDraft(id, body),
  );
}

export function useSendDraft() {
  return useMailMutation((id: number) => mailApi.sendDraft(id));
}

export function useDeleteDraft() {
  return useMailMutation((id: number) => mailApi.deleteDraft(id));
}

export function useUpdateFlags() {
  return useMailMutation(({ id, read, starred }: { id: number; read?: boolean; starred?: boolean }) =>
    mailApi.updateFlags(id, { read, starred }),
  );
}
