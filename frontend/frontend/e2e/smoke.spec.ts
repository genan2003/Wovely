import { expect, test } from '@playwright/test';
import { mockBackend, sampleProducts } from './fixtures';

test.describe('smoke', () => {
  test('root redirects to the product catalog', async ({ page }) => {
    await mockBackend(page);
    await page.goto('/');
    await expect(page).toHaveURL(/\/products$/);
    await expect(page.locator('.wv-brand')).toHaveText('Wovely');
  });

  test('catalog renders products returned by the API', async ({ page }) => {
    await mockBackend(page, { '^/api/products$': sampleProducts });
    await page.goto('/products');
    await expect(page.getByText('Woven Willow Basket').first()).toBeVisible();
  });

  test('protected route redirects anonymous users to login', async ({ page }) => {
    await mockBackend(page);
    await page.goto('/settings');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Fsettings$/);
  });
});
