import { domainColor } from '../lib/domain';

/** Small ring in the domain's colour: the "postmark" used wherever a domain is named. */
export function DomainMark({ domain, size = 14 }: { domain: string; size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <circle cx="8" cy="8" r="6" fill="none" stroke={domainColor(domain)} strokeWidth="2.5" />
      <circle cx="8" cy="8" r="2" fill={domainColor(domain)} />
    </svg>
  );
}
