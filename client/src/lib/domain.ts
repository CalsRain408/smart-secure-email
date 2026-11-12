/**
 * Domain identity colours ("postmarks"). Which domain an address belongs to is real
 * information in this product (two isolated mail domains), so it gets a consistent mark.
 */
const KNOWN: Record<string, string> = {
  'domain-a.test': '#2B59C3',
  'domain-b.test': '#94468A',
};

const FALLBACK = ['#2F7D8C', '#7A6A2E', '#5C5FA8', '#8C4F3A'];

export function domainOf(address: string): string {
  const at = address.lastIndexOf('@');
  return at >= 0 ? address.slice(at + 1).toLowerCase() : '';
}

export function domainColor(domain: string): string {
  const known = KNOWN[domain];
  if (known) {
    return known;
  }
  let hash = 0;
  for (const ch of domain) {
    hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  }
  return FALLBACK[Math.abs(hash) % FALLBACK.length] ?? '#5C6B7A';
}

export function localPart(address: string): string {
  const at = address.lastIndexOf('@');
  return at >= 0 ? address.slice(0, at) : address;
}
