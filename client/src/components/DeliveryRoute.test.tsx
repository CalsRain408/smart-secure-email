import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DeliveryRoute } from './DeliveryRoute';

describe('DeliveryRoute', () => {
  it('shows a receipt per recipient and a summary', () => {
    render(
      <DeliveryRoute
        originDomain="domain-a.test"
        delivery={[
          { address: 'carol@domain-a.test', type: 'TO', status: 'DELIVERED' },
          { address: 'bob@domain-b.test', type: 'CC', status: 'PENDING' },
          { address: 'ghost@domain-b.test', type: 'BCC', status: 'FAILED' },
        ]}
      />,
    );

    expect(screen.getByText('已送达')).toBeInTheDocument();
    expect(screen.getByText('投递中')).toBeInTheDocument();
    expect(screen.getByText('投递失败')).toBeInTheDocument();
    expect(screen.getByText('1 位已送达，1 位失败')).toBeInTheDocument();
    expect(screen.getByText('密送')).toBeInTheDocument();
  });

  it('says everything arrived once all recipients are delivered', () => {
    render(
      <DeliveryRoute
        originDomain="domain-a.test"
        delivery={[{ address: 'bob@domain-b.test', type: 'TO', status: 'DELIVERED' }]}
      />,
    );
    expect(screen.getByText('全部送达')).toBeInTheDocument();
  });
});
