import type { DeliveryStatus, RecipientDelivery } from '../api/types';
import { domainOf } from '../lib/domain';
import { DomainMark } from './DomainMark';

export const DELIVERY_LABEL: Record<DeliveryStatus, string> = {
  DELIVERED: '已送达',
  PENDING: '投递中',
  RETRYING: '重试中',
  FAILED: '投递失败',
  RECALLED: '已撤回',
  RECALL_FAILED: '撤回失败',
  NOT_TRACKED: '—',
};

const TYPE_LABEL = { TO: '收件人', CC: '抄送', BCC: '密送' } as const;

interface Props {
  originDomain: string;
  delivery: RecipientDelivery[];
}

/**
 * Per-recipient receipt, drawn as a route from the sender's domain to each recipient's
 * domain. Local hops are solid, cross-domain hops dashed. Only the author sees this.
 */
export function DeliveryRoute({ originDomain, delivery }: Props) {
  const settled = delivery.filter((d) => d.status === 'DELIVERED').length;
  const failed = delivery.filter((d) => d.status === 'FAILED').length;
  const summary =
    failed > 0
      ? `${settled} 位已送达，${failed} 位失败`
      : settled === delivery.length
        ? '全部送达'
        : `${settled} / ${delivery.length} 已送达`;

  return (
    <section className="route" aria-label="投递回执">
      <div className="route__head">
        <span>投递回执</span>
        <span aria-live="polite">{summary}</span>
      </div>
      {delivery.map((d) => {
        const target = domainOf(d.address);
        const local = target === originDomain;
        return (
          <div className="route__row" key={d.address}>
            <div className="route__path">
              <DomainMark domain={originDomain} size={12} />
              <span className={`route__line${local ? ' route__line--local' : ''}`} aria-hidden="true" />
              <DomainMark domain={target} size={12} />
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {d.address}
              </span>
              <span style={{ color: 'var(--ink-faint)', flex: 'none' }}>{TYPE_LABEL[d.type]}</span>
            </div>
            <span className={`route__status route__status--${d.status}`}>
              <span className="route__dot" aria-hidden="true" />
              {DELIVERY_LABEL[d.status]}
            </span>
          </div>
        );
      })}
    </section>
  );
}
