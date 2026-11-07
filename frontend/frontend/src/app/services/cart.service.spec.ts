import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { CartService, CartItem } from './cart.service';
import { AuthService } from './auth.service';
import { provideTestDefaults } from '../../testing/test-providers';
import { aProduct, anAuthUser } from '../../testing/fixtures';

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

describe('CartService per-user storage (PLAN.md 5.6)', () => {
  const basket = item('p-1', 'Basket');
  const signIn = (id: string) => TestBed.inject(AuthService).user.set(anAuthUser({ id }));
  const signOut = () => TestBed.inject(AuthService).user.set(null);
  const saved = (key: string) => JSON.parse(localStorage.getItem(key) ?? '[]');

  function setup(): CartService {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    const cart = TestBed.inject(CartService);
    TestBed.tick();
    return cart;
  }

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('keeps each user’s cart to themselves on a shared browser', () => {
    const cart = setup();
    signIn('alice');
    TestBed.tick();
    cart.addToCart(aProduct({ id: 'alice-item' }));
    TestBed.tick();

    signOut();
    TestBed.tick();
    expect(cart.items()).toEqual([]);

    signIn('bob');
    TestBed.tick();
    expect(cart.items()).toEqual([]);

    signOut();
    TestBed.tick();
    signIn('alice');
    TestBed.tick();
    expect(cart.items().map((i) => i.id)).toEqual(['alice-item']);
  });

  it('carries a guest cart into the account on login and empties the guest cart', () => {
    localStorage.setItem('wovely_cart_alice', JSON.stringify([{ ...basket, quantity: 2 }]));
    const cart = setup();
    cart.items.set([{ ...basket, quantity: 1 }, item('p-2', 'Vase')]);
    TestBed.tick();

    signIn('alice');
    TestBed.tick();

    expect(cart.items().map((i) => [i.id, i.quantity])).toEqual([['p-1', 3], ['p-2', 1]]);
    expect(saved('wovely_cart_alice').length).toBe(2);
    expect(localStorage.getItem('wovely_cart')).toBeNull();
  });

  it('moves the old shared cart to a returning signed-in user', () => {
    localStorage.setItem('wovely_cart', JSON.stringify([basket]));
    sessionStorage.setItem('auth-user', JSON.stringify(anAuthUser({ id: 'alice' })));
    const cart = setup();

    expect(cart.items().map((i) => i.id)).toEqual(['p-1']);
    expect(saved('wovely_cart_alice').length).toBe(1);
    expect(localStorage.getItem('wovely_cart')).toBeNull();
  });
});
