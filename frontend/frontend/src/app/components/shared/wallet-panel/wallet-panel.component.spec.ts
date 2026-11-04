import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { WalletPanelComponent } from './wallet-panel.component';
import { provideTestDefaults } from '../../../../testing/test-providers';

/** PLAN.md 3.7 — balance, demo top-up (amount only) and activity. */
describe('WalletPanelComponent', () => {
  let http: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({ imports: [WalletPanelComponent], providers: provideTestDefaults() });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(WalletPanelComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/wallet')).flush({ balanceCents: 12345, balance: 123.45, currency: 'RON' });
    http.expectOne((r) => r.url.endsWith('/api/wallet/transactions')).flush([
      { id: 't1', type: 'PAYMENT', amountCents: -2750, balanceAfterCents: 12345, description: 'Order ORD-1', createdAt: '2026-11-01T10:00:00Z' },
      { id: 't2', type: 'TOP_UP', amountCents: 15095, balanceAfterCents: 15095, createdAt: '2026-11-01T09:00:00Z' },
    ]);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('shows the balance and signed activity', () => {
    const el = setup().nativeElement as HTMLElement;
    expect(el.querySelector('.amount')?.textContent).toContain('123.45');
    const rows = el.querySelectorAll('.activity-row');
    expect(rows[0].textContent).toContain('Order payment');
    expect(rows[0].querySelector('.activity-amount')?.textContent?.trim()).toBe('−27.50');
    expect(rows[1].querySelector('.activity-amount')?.textContent?.trim()).toBe('+150.95');
  });

  it('tops up with an amount only and reloads', () => {
    const f = setup();
    f.componentInstance.topUp(100);
    const req = http.expectOne((r) => r.url.endsWith('/api/wallet/top-up'));
    expect(req.request.body).toEqual({ amount: 100 });
    req.flush({ balanceCents: 22345, balance: 223.45, currency: 'RON' });
    http.expectOne((r) => r.url.endsWith('/api/wallet')).flush({ balanceCents: 22345, balance: 223.45, currency: 'RON' });
    http.expectOne((r) => r.url.endsWith('/api/wallet/transactions')).flush([]);
    expect(f.componentInstance.wallet.balanceCents()).toBe(22345);
  });

  it('validates the custom amount locally', () => {
    const f = setup();
    f.componentInstance.topUp(0);
    f.componentInstance.topUp(6000);
    f.detectChanges();
    http.expectNone((r) => r.url.endsWith('/api/wallet/top-up'));
    expect((f.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain('between 0.01 and 5000');
  });

  it('shows the server message when a top-up fails', () => {
    const f = setup();
    f.componentInstance.topUp(10);
    http.expectOne((r) => r.url.endsWith('/api/wallet/top-up'))
      .flush({ message: 'amount can have at most 2 decimals' }, { status: 400, statusText: 'Bad Request' });
    f.detectChanges();
    expect(f.componentInstance.error()).toBe('amount can have at most 2 decimals');
  });
});
