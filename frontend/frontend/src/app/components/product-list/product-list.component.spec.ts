import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { ProductListComponent } from './product-list.component';
import { provideTestDefaults } from '../../../testing/test-providers';
import { aProduct } from '../../../testing/fixtures';

describe('ProductListComponent', () => {
  let http: HttpTestingController;
  const isCatalog = (r: { url: string }) => r.url === '/api/products';

  async function setup() {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [ProductListComponent], providers: provideTestDefaults() });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProductListComponent);
    await fixture.whenStable();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  it('renders what the API returns, priced in RON', async () => {
    const { fixture, root } = await setup();
    http.expectOne(isCatalog).flush([aProduct({ price: 42.5 })]);
    http.match(() => true).forEach((r) => r.flush({ reviews: [], totalReviews: 0 }));
    await fixture.whenStable();

    expect(root.querySelectorAll('.product-card').length).toBe(1);
    expect(root.querySelector('.product-price')?.textContent).toContain('42.50 RON');
    expect(root.querySelector<HTMLAnchorElement>('.product-title a')?.getAttribute('href')).toBe('/products/p-1');
  });

  it('shows an honest error and no invented products when the API fails', async () => {
    const { fixture, root } = await setup();
    http.expectOne(isCatalog).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    expect(root.querySelectorAll('.product-card').length).toBe(0);
    expect(root.textContent).toContain('The catalog didn’t load');

    root.querySelector<HTMLButtonElement>('app-empty-state button')!.click();
    http.expectOne(isCatalog).flush([]);
    await fixture.whenStable();
    expect(root.textContent).toContain('No products yet');
  });

  it('says the catalog is empty, without demo data, on a fresh install', async () => {
    const { fixture, root } = await setup();
    http.expectOne(isCatalog).flush([]);
    await fixture.whenStable();
    expect(root.querySelectorAll('.product-card').length).toBe(0);
    expect(root.textContent).toContain('No products yet');
    expect(root.querySelector('app-empty-state button')).toBeNull();
  });

  it('offers to clear filters when they are what hides everything', async () => {
    const { fixture, root } = await setup();
    http.expectOne(isCatalog).flush([]);
    fixture.componentInstance.filters.city = 'Sibiu';
    fixture.componentInstance.applyFilters();
    http.expectOne((r) => isCatalog(r) && r.params.get('city') === 'Sibiu').flush([]);
    await fixture.whenStable();
    expect(root.textContent).toContain('Nothing matches these filters');

    root.querySelector<HTMLButtonElement>('app-empty-state button')!.click();
    http.expectOne((r) => isCatalog(r) && !r.params.has('city')).flush([]);
  });
});
