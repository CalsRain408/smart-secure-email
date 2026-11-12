import dayjs from 'dayjs';

/** Compact list time: today → 14:05, this year → 11月12日, older → 2025/11/12. */
export function listTime(iso: string | undefined, now: Date = new Date()): string {
  if (!iso) {
    return '';
  }
  const t = dayjs(iso);
  const n = dayjs(now);
  if (t.isSame(n, 'day')) {
    return t.format('HH:mm');
  }
  if (t.isSame(n, 'year')) {
    return t.format('M月D日');
  }
  return t.format('YYYY/M/D');
}

/** Full time for the reading pane. */
export function fullTime(iso: string | undefined): string {
  return iso ? dayjs(iso).format('YYYY年M月D日 HH:mm') : '';
}
