import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { CartService, CartItem } from './cart.service';
import { provideTestDefaults } from '../../testing/test-providers';

const item = (id: string, name: string): CartItem =>
  ({ id, name, price: 10, sellerId: 's-1', quantity: 1 }) as CartItem;

describe('CartService.pruneUnavailable (PLAN.md 2.8)', () => {
  let cart: CartService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    cart = TestBed.inject(CartService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('removes archived, unapproved and deleted products and keeps the rest', () => {
    cart.items.set([item('ok', 'Basket'), item('arch', 'Old rug'), item('gone', 'Vase'), item('flaky', 'Mug')]);
    let removed: CartItem[] = [];
    cart.pruneUnavailable().subscribe((r) => (removed = r));

    http.expectOne((r) => r.url.endsWith('/api/products/ok')).flush({ id: 'ok', status: 'APPROVED' });
    http.expectOne((r) => r.url.endsWith('/api/products/arch')).flush({ id: 'arch', status: 'ARCHIVED' });
    http.expectOne((r) => r.url.endsWith('/api/products/gone')).flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne((r) => r.url.endsWith('/api/products/flaky')).flush(null, { status: 503, statusText: 'Unavailable' });

    expect(removed.map((r) => r.id)).toEqual(['arch', 'gone']);
    expect(cart.items().map((i) => i.id)).toEqual(['ok', 'flaky']);
  });

  it('does nothing for an empty cart', () => {
    cart.items.set([]);
    let removed: CartItem[] | undefined;
    cart.pruneUnavailable().subscribe((r) => (removed = r));
    expect(removed).toEqual([]);
  });
});
