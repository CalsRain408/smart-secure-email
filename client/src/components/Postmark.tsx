import dayjs from 'dayjs';
import { useId } from 'react';
import { domainColor } from '../lib/domain';

/**
 * Circular postmark: the origin domain set around the ring, the sending date in the middle.
 * It answers "where did this come from, and when" at a glance.
 */
export function Postmark({ domain, sentAt, size = 96 }: { domain: string; sentAt?: string | undefined; size?: number }) {
  const pathId = useId();
  const color = domainColor(domain);
  const t = sentAt ? dayjs(sentAt) : null;
  const ringText = `${domain} ✦ ${domain} ✦ `;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      role="img"
      aria-label={`发件域 ${domain}${t ? `，${t.format('YYYY年M月D日 HH:mm')}` : ''}`}
    >
      <defs>
        <path id={pathId} d="M50,50 m-37,0 a37,37 0 1,1 74,0 a37,37 0 1,1 -74,0" />
      </defs>
      <circle cx="50" cy="50" r="47" fill="none" stroke={color} strokeWidth="2" />
      <circle cx="50" cy="50" r="29" fill="none" stroke={color} strokeWidth="1" />
      <text fill={color} fontSize="10.5" fontWeight="600">
        <textPath href={`#${pathId}`} textLength="228">
          {ringText}
        </textPath>
      </text>
      {t ? (
        <>
          <text x="50" y="49" textAnchor="middle" fill={color} fontSize="12" fontWeight="600" className="tnum">
            {t.format('M.D')}
          </text>
          <text x="50" y="63" textAnchor="middle" fill={color} fontSize="10" className="tnum">
            {t.format('HH:mm')}
          </text>
        </>
      ) : (
        <text x="50" y="54" textAnchor="middle" fill={color} fontSize="10">
          草稿
        </text>
      )}
    </svg>
  );
}
