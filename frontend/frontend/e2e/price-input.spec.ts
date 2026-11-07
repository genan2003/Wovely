import { expect, test } from '@playwright/test';
import { inventoryItems, mockShop, signIn } from './visual-fixtures';

/** PLAN.md 7.5: the API keeps money in whole cents and rejects a third decimal, so the form stops it first. */
test('a seller is told to use two decimals before anything is sent', async ({ page }) => {
  await signIn(page, 'seller');
  await mockShop(page);
  const listing = inventoryItems[0];
  const sent: unknown[] = [];
  await page.route(new RegExp(`/api/products/seller/s-1/product/${listing.productId}$`), (route) => {
    if (route.request().method() === 'PUT') sent.push(route.request().postDataJSON());
    return route.fulfill({ json: { ...listing, price: 42.5, priceCents: 4250 } });
  });

  await page.goto('/seller/inventory');
  const card = page.locator('.inventory-card').filter({ hasText: listing.productName });
  await card.getByRole('button', { name: `Edit ${listing.productName}` }).click();
  const price = page.getByLabel('Price (RON)');
  await expect(price).toHaveAttribute('step', '0.01');

  await price.fill('12.345');
  await page.getByRole('button', { name: /Save Changes/ }).click();
  await expect(page.getByText('Enter a price in RON with at most two decimals, for example 42.50.')).toBeVisible();
  expect(sent).toHaveLength(0);

  await price.fill('42.50');
  await page.getByRole('button', { name: /Save Changes/ }).click();
  await expect(page.getByText(/Changes saved/)).toBeVisible();
  expect(sent).toHaveLength(1);
  expect((sent[0] as { price: number }).price).toBe(42.5);
});
