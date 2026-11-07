import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockShop, orders, seedCart, signIn } from './visual-fixtures';

/** PLAN.md 7.1 — README Story 2: "Sellers must be able to see a clear status timeline for every active order". */
test('a seller opens the status timeline of an order', async ({ page }) => {
  await signIn(page, 'seller');
  await mockShop(page);
  await page.goto('/seller/orders');

  const shipped = orders[1];
  const toggle = page.getByRole('button', { name: 'Show timeline' }).nth(1);
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await toggle.click();
  await expect(page.getByRole('button', { name: 'Hide timeline' })).toHaveAttribute('aria-expanded', 'true');

  const timeline = page.getByRole('list', { name: `Progress of order ${shipped.orderNumber}` });
  await expect(timeline.getByRole('listitem')).toHaveText([/Order placed.*by the buyer/, /Shipped.*by the seller/, /Delivered/, /Completed/]);
  await expect(timeline.locator('[aria-current="step"]')).toContainText('Shipped');
  await expect(timeline.locator('time').first()).toHaveAttribute('datetime', '2026-11-01T10:00:00Z');

  const { violations } = await new AxeBuilder({ page }).include('.timeline-row').withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(violations.map((v) => v.id)).toEqual([]);

  await page.getByRole('button', { name: 'Hide timeline' }).click();
  await expect(timeline).toHaveCount(0);
});

test('a buyer sees why an order was cancelled in its history', async ({ page }) => {
  await signIn(page, 'buyer');
  await seedCart(page, 'buyer-1');
  await mockShop(page);
  await page.goto('/cart?tab=history');

  const card = page.locator('.shop-card').filter({ hasText: orders[4].orderNumber });
  await card.getByText('Order history').click();
  const timeline = card.getByRole('list', { name: `History of order ${orders[4].orderNumber}` });
  await expect(timeline.getByRole('listitem').last()).toContainText('Cancelled');
  // compact view in past orders: the step, not the reason paragraph
  await expect(timeline.locator('.reason')).toHaveCount(0);
});

for (const colorScheme of ['light', 'dark'] as const) {
  test(`an open order timeline has no accessibility violations (${colorScheme})`, async ({ page }) => {
    await page.emulateMedia({ colorScheme, reducedMotion: 'reduce' });
    await signIn(page, 'buyer');
    await seedCart(page, 'buyer-1');
    await mockShop(page);
    await page.goto('/cart?tab=shipped');
    await page.getByText('Order progress').first().click();

    const { violations } = await new AxeBuilder({ page }).include('.order-progress').withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
    expect(violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
  });
}
