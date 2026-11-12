// Contracts mirrored from the backend (docs/protocol.md, Part A).

export interface FieldViolation {
  field: string;
  reason: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: FieldViolation[];
}

export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  error?: ApiErrorBody;
  timestamp: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SystemInfo {
  domain: string;
  displayName: string;
  apiVersion: string;
  serverVersion: string;
}

export interface CsrfToken {
  headerName: string;
  token: string;
}

export type UserRole = 'ADMIN' | 'MEMBER';

export interface CurrentUser {
  username: string;
  address: string;
  displayName: string;
  role: UserRole;
}

export type MailFolder = 'INBOX' | 'SENT' | 'DRAFTS' | 'TRASH';
export type MailStatus = 'DRAFT' | 'SENT' | 'RECALLED';
export type RecipientType = 'TO' | 'CC' | 'BCC';
export type DeliveryStatus =
  | 'PENDING'
  | 'DELIVERED'
  | 'RETRYING'
  | 'FAILED'
  | 'RECALLED'
  | 'RECALL_FAILED'
  | 'NOT_TRACKED';

export interface AddressView {
  address: string;
  displayName?: string;
}

export interface MailSummary {
  id: number;
  folder: MailFolder;
  status: MailStatus;
  from: AddressView;
  to?: string[];
  subject: string;
  preview: string;
  read: boolean;
  starred: boolean;
  sentAt?: string;
  updatedAt: string;
}

export interface RecipientDelivery {
  address: string;
  type: RecipientType;
  status: DeliveryStatus;
}

export interface MailDetail {
  id: number;
  folder: MailFolder;
  messageId: string;
  status: MailStatus;
  from: AddressView;
  to: string[];
  cc: string[];
  /** Only present when the viewer is the author. */
  bcc?: string[];
  subject: string;
  body: string;
  read: boolean;
  starred: boolean;
  sentAt?: string;
  updatedAt: string;
  /** Only present when the viewer is the author. */
  delivery?: RecipientDelivery[];
}

export interface ComposeRequest {
  to: string[];
  cc: string[];
  bcc: string[];
  subject: string;
  body: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  displayName: string;
  password: string;
  confirmPassword: string;
}
