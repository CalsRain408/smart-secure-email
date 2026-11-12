import type { ReactNode } from 'react';
import { DomainMark } from '../../components/DomainMark';
import { useSystemInfo } from '../../hooks/queries';
import { domainColor } from '../../lib/domain';

/**
 * The two server instances look identical, so the sign-in screen leads with the one thing
 * that tells them apart: which mail domain you are about to enter.
 */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  const info = useSystemInfo();
  const domain = info.data?.domain ?? '';

  return (
    <div className="auth">
      <section className="auth__plate">
        <div className="auth__product">
          <img src="/favicon.svg" width={20} height={20} alt="" />
          Smart Secure Mail
        </div>
        <div>
          <h1 className="auth__domain" style={{ color: domain ? domainColor(domain) : 'var(--ink)' }}>
            {domain ? (
              <>
                <DomainMark domain={domain} size={40} />
                <span>{domain}</span>
              </>
            ) : info.isError ? (
              '服务器未连接'
            ) : (
              ' '
            )}
          </h1>
          <p className="auth__org">{info.data?.displayName}</p>
        </div>
        <p className="auth__note">
          账号只属于这个域名。发往其他已互信域名的邮件经过签名校验后投递，你可以在每封已发邮件里看到逐个收件人的回执。
        </p>
      </section>
      <section className="auth__panel">
        <div className="auth__form">
          <h2 className="auth__title">{title}</h2>
          <p className="auth__subtitle">{subtitle}</p>
          {children}
        </div>
      </section>
    </div>
  );
}
