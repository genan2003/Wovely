import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { DeliveryOptionService } from './delivery-option.service';
import { CartService, CartItem } from './cart.service';
import { provideTestDefaults } from '../../testing/test-providers';
import { DeliveryOption } from '../models/delivery-option';

const OPTIONS: DeliveryOption[] = [
  { id: 'ELECTRIC_VAN', label: 'Electric van', fee: 15, feeCents: 1500, co2Kg: 0.5, estimatedDays: 3 },
  { id: 'BICYCLE', label: 'Bicycle delivery', fee: 10, feeCents: 1000, co2Kg: 0.1, estimatedDays: 2 },
];

const item = (id: string, sellerId: string, extra: Partial<CartItem> = {}): CartItem =>
  ({ id, name: id, price: 10, sellerId, quantity: 1, ...extra }) as CartItem;

/** PLAN.md 3.7 — delivery is priced by the server; the SPA never invents a fee. */
describe('DeliveryOptionService + cart grouping', () => {
  let delivery: DeliveryOptionService;
  let cart: CartService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    delivery = TestBed.inject(DeliveryOptionService);
    cart = TestBed.inject(CartService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the price list once and preselects the lowest-CO₂ option', () => {
    delivery.load();
    delivery.load();
    http.expectOne((r) => r.url.endsWith('/api/public/delivery-options')).flush(OPTIONS);
    expect(delivery.defaultOption()?.id).toBe('BICYCLE');
  });

  it('flags a failed load and retries on demand', () => {
    delivery.load();
    http.expectOne((r) => r.url.endsWith('/delivery-options')).flush(null, { status: 503, statusText: 'Down' });
    expect(delivery.failed()).toBe(true);
    delivery.load();
    http.expectOne((r) => r.url.endsWith('/delivery-options')).flush(OPTIONS);
    expect(delivery.failed()).toBe(false);
    expect(delivery.options()).toHaveLength(2);
  });

  it('groups per seller with the server price of the chosen option', () => {
    delivery.options.set(OPTIONS);
    cart.items.set([item('a', 's-1'), item('b', 's-2', { deliveryOptionId: 'ELECTRIC_VAN' })]);
    const [g1, g2] = cart.getGroupedItems();
    expect(g1.selectedShipping?.id).toBe('BICYCLE');
    expect(g1.deliveryCost).toBe(10);
    expect(g2.selectedShipping?.id).toBe('ELECTRIC_VAN');
    expect(g2.deliveryCost).toBe(15);

    cart.setShippingForSeller('s-1', g2.selectedShipping!);
    expect(cart.getGroupedItems()[0].selectedShipping?.id).toBe('ELECTRIC_VAN');
  });

  it('offers no delivery (so no checkout) until the price list is known', () => {
    cart.items.set([item('a', 's-1')]);
    const [group] = cart.getGroupedItems();
    expect(group.selectedShipping).toBeUndefined();
    expect(group.deliveryCost).toBe(0);
  });
});
