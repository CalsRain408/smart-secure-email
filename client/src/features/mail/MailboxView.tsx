import { Navigate, useParams } from 'react-router';
import { FOLDER_BY_SLUG } from './folders';
import { MailListPane } from './MailListPane';
import { ReaderPane } from './ReaderPane';

/** List + reading pane for one folder. The URL is the source of truth for the selection. */
export function MailboxView() {
  const { folder: slug = 'inbox', id } = useParams();
  const folder = FOLDER_BY_SLUG[slug];
  if (!folder) {
    return <Navigate to="/mail/inbox" replace />;
  }
  const selectedId = id && /^\d+$/.test(id) ? Number(id) : null;

  return (
    <>
      <MailListPane key={folder} folder={folder} slug={slug} selectedId={selectedId} />
      <ReaderPane folder={folder} slug={slug} selectedId={selectedId} />
    </>
  );
}
