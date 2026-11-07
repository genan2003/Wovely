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
 * Stubs every backend call (relative /api, normally proxied to the gateway) so E2E tests run
 * without the Java services. Unmatched API calls get an empty JSON array.
 */
/** The server's delivery price list (core EDeliveryOption). */
export const deliveryOptions = [
  { id: 'BICYCLE', label: 'Bicycle delivery', fee: 10, feeCents: 1000, co2Kg: 0.1, estimatedDays: 2 },
  { id: 'ELECTRIC_VAN', label: 'Electric van', fee: 15, feeCents: 1500, co2Kg: 0.5, estimatedDays: 3 },
  { id: 'CARBON_NEUTRAL_COURIER', label: 'Carbon-neutral courier', fee: 25, feeCents: 2500, co2Kg: 1.2, estimatedDays: 2 },
];

export async function mockBackend(page: Page, overrides: Record<string, unknown> = {}): Promise<void> {
  await page.route(/\/api\//, (route: Route) => {
    const url = new URL(route.request().url());
    const all: Record<string, unknown> = { '/api/public/delivery-options$': deliveryOptions, ...overrides };
    for (const [pattern, body] of Object.entries(all)) {
      if (url.pathname.match(pattern)) {
        return route.fulfill({ json: body });
      }
    }
    return route.fulfill({ json: [] });
  });
}
