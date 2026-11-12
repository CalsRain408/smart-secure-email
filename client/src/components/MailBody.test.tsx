import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MailBody } from './MailBody';

describe('MailBody', () => {
  it('shows HTML in a mail body as text and never creates elements from it', () => {
    const payload = '<script>window.__pwned = true</script><img src=x onerror="window.__pwned=true">Hi';
    const { container } = render(<MailBody text={payload} />);

    expect(screen.getByText(payload)).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect((window as unknown as { __pwned?: boolean }).__pwned).toBeUndefined();
  });

  it('keeps line breaks of plain-text mail', () => {
    const { container } = render(<MailBody text={'a\nb'} />);
    expect(container.firstElementChild?.textContent).toBe('a\nb');
    expect(container.firstElementChild).toHaveClass('reader__body'); // white-space: pre-wrap
  });
});
