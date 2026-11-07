import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { vi } from 'vitest';

import { SellerOrdersComponent } from './seller-orders.component';
import { StorageService } from '../../services/storage.service';
import { provideTestDefaults } from '../../../testing/test-providers';
import { anOrder } from '../../../testing/fixtures';
import { Order, OrderStatus } from '../../models/order.model';
import { DialogService } from '../../services/dialog.service';

/** PLAN.md 3.6 — "Ship with Wovely": buy a label, see carrier/CO₂/tracking, print it safely. */
describe('SellerOrdersComponent', () => {
  let http: HttpTestingController;

  const order = (over: Partial<Order> = {}) => anOrder({ status: OrderStatus.PROCESSING, totalAmount: 20, ...over });

  function setup(orders: unknown[]) {
    TestBed.configureTestingModule({
      imports: [SellerOrdersComponent],
      providers: [...provideTestDefaults(), { provide: StorageService, useValue: { getUser: () => ({ id: 'seller-1' }) } }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SellerOrdersComponent);
    fixture.detectChanges();
    flushLoad(orders);
    fixture.detectChanges();
    return fixture;
  }

  function flushLoad(orders: unknown[]) {
    http.expectOne((r) => r.url.endsWith('/api/seller/orders/seller/seller-1')).flush({ orders, totalOrders: orders.length });
    http.match((r) => r.url.includes('/returns')).forEach((r) => r.flush([]));
  }

  afterEach(() => http.verify());

  it('offers "Ship with Wovely" for confirmed/processing orders without a label only', () => {
    const fixture = setup([
      order({ id: 'a', status: OrderStatus.CONFIRMED }),
      order({ id: 'b', status: OrderStatus.PROCESSING, ecoShippingLabel: 'L', trackingNumber: 'WVL-1' }),
      order({ id: 'c', status: OrderStatus.SHIPPED }),
    ]);
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(rows[0].textContent).toContain('Ship with Wovely');
    expect(rows[1].textContent).not.toContain('Ship with Wovely');
    expect(rows[1].textContent).toContain('Mark Shipped');
    expect(rows[2].textContent).not.toContain('Ship with Wovely');
  });

  it('generates a label, shows it and reloads the orders', () => {
    const fixture = setup([order()]);
    fixture.componentInstance.generateEcoLabel(order());
    const req = http.expectOne((r) => r.url.endsWith('/api/seller/orders/o-1/eco-label'));
    expect(req.request.method).toBe('POST');
    req.flush({ label: '--- LABEL ---\nTRACKING: WVL-GRL-X', order: {} });
    flushLoad([order({ ecoShippingLabel: '--- LABEL ---', trackingNumber: 'WVL-GRL-X', carrierName: 'GreenLogistics', estimatedCo2Kg: 0.6 })]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role=dialog] pre')?.textContent).toContain('WVL-GRL-X');
    expect(el.textContent).toContain('GreenLogistics, ~0.6 kg CO₂');
  });

  it('prints the label as text so a crafted address cannot inject markup', () => {
    const fixture = setup([order()]);
    const doc = document.implementation.createHTMLDocument('print');
    const print = vi.fn();
    vi.spyOn(window, 'open').mockReturnValue({ document: doc, print } as unknown as Window);

    fixture.componentInstance.viewLabel({ ecoShippingLabel: 'DESTINATION: <img src=x onerror="alert(1)">' });
    fixture.componentInstance.printLabel();

    expect(doc.querySelector('img')).toBeNull();
    expect(doc.querySelector('pre')?.textContent).toContain('<img src=x');
    expect(print).toHaveBeenCalled();
  });

  it('follows the server on editing and lets sellers cancel until shipping (README: edit locks beyond Processing)', () => {
    const fixture = setup([
      order({ id: 'a', status: OrderStatus.PROCESSING, editable: true }),
      order({ id: 'b', status: OrderStatus.PROCESSING, ecoShippingLabel: 'L', editable: false }),
      order({ id: 'c', status: OrderStatus.SHIPPED, editable: false }),
    ]);
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    const edit = (row: Element) => [...row.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Edit')!;
    const hasCancel = (row: Element) => [...row.querySelectorAll('button')].some((b) => b.textContent?.trim() === 'Cancel');
    expect(edit(rows[0]).disabled).toBe(false);
    expect(edit(rows[1]).disabled).toBe(true);
    expect(edit(rows[1]).getAttribute('title')).toContain('shipping label');
    expect(edit(rows[2]).disabled).toBe(true);
    expect(hasCancel(rows[0])).toBe(true);
    expect(hasCancel(rows[1])).toBe(true);
    expect(hasCancel(rows[2])).toBe(false);
  });
});
