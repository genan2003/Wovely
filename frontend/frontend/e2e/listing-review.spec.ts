import { expect, test } from '@playwright/test';
import { inventoryItems, mockShop, signIn } from './visual-fixtures';

/** A seller sees which listings need attention, and an edit after "changes requested" resubmits it. */
test('a seller resubmits a listing a moderator sent back', async ({ page }) => {
  await signIn(page, 'seller');
  await mockShop(page);
  const sentBack = inventoryItems[1];
  let body: Record<string, unknown> | undefined;
  await page.route(new RegExp(`/api/products/seller/s-1/product/${sentBack.productId}$`), (route) => {
    body = route.request().postDataJSON();
    return route.fulfill({ json: { ...sentBack, status: 'PENDING' } });
  });

  await page.goto('/seller/inventory');
  const card = page.locator('.inventory-card').filter({ hasText: sentBack.productName });
  await expect(card.getByText('Changes requested')).toBeVisible();
  // live listings carry no badge; listings awaiting review do
  await expect(page.locator('.inventory-card').filter({ hasText: inventoryItems[0].productName }).locator('app-listing-status span')).toHaveCount(0);
  await expect(page.locator('.inventory-card').filter({ hasText: inventoryItems[3].productName }).getByText('Awaiting review')).toBeVisible();

  await card.getByRole('button', { name: `Edit ${sentBack.productName}` }).click();
  await page.locator('#editName').fill(`${sentBack.productName} (new photos)`);
  await page.getByRole('button', { name: /Save Changes/ }).click();

  await expect(page.getByText('Changes saved. The listing is hidden from buyers until a moderator approves it again.')).toBeVisible();
  expect(body?.['name']).toBe(`${sentBack.productName} (new photos)`);
  expect(body).not.toHaveProperty('status');
});
