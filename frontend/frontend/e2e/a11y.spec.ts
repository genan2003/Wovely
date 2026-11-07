import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { Persona, mockShop, seedCart, signIn } from './visual-fixtures';

/** PLAN.md 5.2: every main screen is AXE-clean in both themes and has no sideways scroll at 360 px. */
const screens: [string, string, Persona][] = [
  ['catalog', '/products', 'guest'],
  ['product', '/products/p-1', 'guest'],
  ['login', '/login', 'guest'],
  ['register', '/register', 'guest'],
  ['cart', '/cart', 'buyer'],
  ['orders', '/cart?tab=shipped', 'buyer'],
  ['history', '/cart?tab=history', 'buyer'],
  ['settings', '/settings', 'buyer'],
  ['messages', '/messages', 'buyer'],
  ['maker', '/seller-profile/s-1', 'guest'],
  ['inventory', '/seller/inventory', 'seller'],
  ['seller orders', '/seller/orders', 'seller'],
  ['record a sale', '/seller/manual-order', 'seller'],
  ['review an order', '/review-order/o-2', 'buyer'],
  ['admin approvals', '/admin', 'admin'],
  ['admin users', '/admin?tab=users', 'admin'],
  ['admin orders', '/admin?tab=orders', 'admin'],
];

for (const colorScheme of ['light', 'dark'] as const) {
  for (const [name, url, persona] of screens) {
    test(`${name} has no accessibility violations (${colorScheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme, reducedMotion: 'reduce' });
      await signIn(page, persona);
      await seedCart(page, 'buyer-1');
      await mockShop(page);
      await page.goto(url);
      await page.waitForLoadState('networkidle');

      const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
      const summary = violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`);
      expect(summary).toEqual([]);
    });
  }
}

for (const [name, url, persona] of screens) {
  test(`${name} fits a 360 px phone`, async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await signIn(page, persona);
    await seedCart(page, 'buyer-1');
    await mockShop(page);
    await page.goto(url);
    await page.waitForLoadState('networkidle');

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
}
