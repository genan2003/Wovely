import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { vi } from 'vitest';

import { ProductDetailsComponent } from './product-details.component';
import { AuthService } from '../../services/auth.service';
import { CartService } from '../../services/cart.service';
import { provideTestDefaults } from '../../../testing/test-providers';
import { aProduct, anAuthUser } from '../../../testing/fixtures';

describe('ProductDetailsComponent', () => {
  let http: HttpTestingController;

  async function setup() {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [ProductDetailsComponent],
      providers: [
        ...provideTestDefaults(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'p-1' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ProductDetailsComponent);
    await fixture.whenStable();
    return { fixture, root: fixture.nativeElement as HTMLElement, navigate };
  }

  const flushProduct = (over = {}) => {
    http.expectOne('/api/products/p-1').flush(aProduct(over));
    http.match((r) => r.url.includes('/reviews/')).forEach((r) => r.flush({ reviews: [], averageRating: 0, totalReviews: 0 }));
    http.match((r) => r.url.includes('/public/users/')).forEach((r) => r.flush({ username: 'maker' }));
  };

  it('never places an order itself: Buy now goes through checkout (regression)', async () => {
    const { fixture, navigate } = await setup();
    TestBed.inject(AuthService).user.set(anAuthUser());
    flushProduct();
    await fixture.whenStable();

    fixture.componentInstance.buyNow();

    http.expectNone('/api/orders');
    expect(TestBed.inject(CartService).items().map((i) => i.id)).toEqual(['p-1']);
    expect(navigate).toHaveBeenCalledWith(['/cart']);
  });

  it('asks guests to log in before Buy now', async () => {
    const { fixture, navigate } = await setup();
    flushProduct();
    await fixture.whenStable();

    fixture.componentInstance.buyNow();

    expect(TestBed.inject(CartService).items()).toEqual([]);
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.objectContaining({ queryParams: expect.anything() }));
  });

  it('does not invent a rating for a product without reviews (regression)', async () => {
    const { fixture, root } = await setup();
    flushProduct();
    await fixture.whenStable();
    expect(fixture.componentInstance.averageRating()).toBe(0);
    expect(root.querySelector('.buy__rating')).toBeNull();
  });

  it('blocks buying when sold out', async () => {
    const { fixture, root } = await setup();
    flushProduct({ stockQuantity: 0 });
    await fixture.whenStable();
    expect(root.textContent).toContain('Sold out for now.');
    expect(root.querySelector<HTMLButtonElement>('.add-to-cart-btn')?.disabled).toBe(true);
  });

  it('explains a missing product instead of showing a demo one', async () => {
    const { fixture, root } = await setup();
    http.expectOne('/api/products/p-1').flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(root.textContent).toContain('This product isn’t available');
  });

  it('offers a retry when the API is down', async () => {
    const { fixture, root } = await setup();
    http.expectOne('/api/products/p-1').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(root.textContent).toContain('This product didn’t load');
  });
});
