import { expect, test } from '@playwright/test';
import { mockBackend, sampleProducts } from './fixtures';

/** PLAN.md 3.5 — buyers filter the catalog by delivery emission. */
test('catalog filters by zero-emission delivery and labels listings', async ({ page }) => {
  const bike = { ...sampleProducts[0], id: 'p-bike', name: 'Bike-delivered Basket', deliveryEmission: 'ZERO', localPickup: true };
  const truck = { ...sampleProducts[0], id: 'p-truck', name: 'Express Vase', deliveryEmission: 'STANDARD', shippingMethod: 'Express' };
  const seen: string[] = [];

  await mockBackend(page);
  await page.route(/\/api\/products(\?.*)?$/, (route) => {
    const url = new URL(route.request().url());
    seen.push(url.search);
    const ceiling = url.searchParams.get('deliveryEmission');
    return route.fulfill({ json: ceiling === 'ZERO' ? [bike] : [bike, truck] });
  });

  await page.goto('/products');
  await expect(page.getByText('Express Vase')).toBeVisible();
  const bikeCard = page.locator('.product-card', { hasText: 'Bike-delivered Basket' });
  await expect(bikeCard.getByText('Zero-emission')).toBeVisible();
  await expect(page.locator('.product-card', { hasText: 'Express Vase' }).getByText(/emission/)).toHaveCount(0);

  await page.getByLabel(/Zero-emission only/).check();
  await expect(page.getByText('Express Vase')).toHaveCount(0);
  await expect(page.getByText('Bike-delivered Basket')).toBeVisible();
  expect(seen.some((q) => q.includes('deliveryEmission=ZERO'))).toBe(true);

  await page.getByLabel('Local pickup available').check();
  await expect.poll(() => seen.some((q) => q.includes('localPickup=true'))).toBe(true);
});
