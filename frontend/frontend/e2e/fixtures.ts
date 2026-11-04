import { Page, Route } from '@playwright/test';

/** Product shape as returned by GET /api/products (only fields the UI reads). */
export const sampleProducts = [
  {
    id: 'p-1',
    name: 'Woven Willow Basket',
    description: 'Hand-woven in Sibiu.',
    price: 42.5,
    sellerId: 's-1',
    imageUrl: '',
    category: 'Home & Living',
    categoryPath: 'Home & Living',
    city: 'Sibiu',
    region: 'Sibiu',
    co2EmissionScore: 'Low',
    shippingMethod: 'Carbon Neutral Courier',
    isHandmade: true,
    stockQuantity: 5,
    lowStockThreshold: 2,
    status: 'APPROVED',
  },
];

/**
 * Stubs every backend call (core :8081, products :8082, inventory :8083) so E2E tests run
 * without the Java services. Unmatched API calls get an empty JSON array.
 */
export async function mockBackend(page: Page, overrides: Record<string, unknown> = {}): Promise<void> {
  await page.route(/localhost:808[1-3]\/api\//, (route: Route) => {
    const url = new URL(route.request().url());
    for (const [pattern, body] of Object.entries(overrides)) {
      if (url.pathname.match(pattern)) {
        return route.fulfill({ json: body });
      }
    }
    return route.fulfill({ json: [] });
  });
}
