import { createContext, useContext } from 'react';
import type { ComposeRequest } from '../../api/types';

export interface ComposeInit {
  /** Existing draft being edited (mailbox item id), or undefined for a new message. */
  draftId?: number;
  values?: ComposeRequest;
  title?: string;
}

export const ComposeContext = createContext<{ openCompose: (init?: ComposeInit) => void } | null>(null);

export function useCompose() {
  const ctx = useContext(ComposeContext);
  if (!ctx) {
    throw new Error('useCompose must be used inside MailShell');
  }
  return ctx;
}
