import { Tooltip } from 'antd';
import { domainColor, domainOf, localPart } from '../lib/domain';

interface Props {
  address: string;
  displayName?: string | undefined;
  /** The domain this client belongs to; other domains are spelled out. */
  localDomain: string;
}

/**
 * An address with its domain mark. Local colleagues show by name only; addresses on
 * another domain always show that domain, so cross-domain mail is never ambiguous.
 */
export function AddressTag({ address, displayName, localDomain }: Props) {
  const domain = domainOf(address);
  const external = domain !== localDomain;
  const name = displayName?.trim() || localPart(address);
  return (
    <Tooltip title={external ? `${address}（外部域名）` : address}>
      <span className="addr" style={{ color: domainColor(domain) }}>
        <span className="addr__mark" aria-hidden="true" />
        <span className="addr__name" style={{ color: 'var(--ink)' }}>
          {name}
        </span>
        {external && <span className="addr__domain">@{domain}</span>}
      </span>
    </Tooltip>
  );
}
