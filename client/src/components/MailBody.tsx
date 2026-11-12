/**
 * Renders a mail body as inert plain text. Email content is untrusted: it is passed to
 * React as a text child (escaped by React) and NEVER through dangerouslySetInnerHTML,
 * so markup such as <script> or <img onerror> is displayed, not executed.
 */
export function MailBody({ text }: { text: string }) {
  return <div className="reader__body">{text}</div>;
}
