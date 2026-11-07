import { expect, test } from '@playwright/test';
import { catalog, mockShop, orders, seedCart, signIn } from './visual-fixtures';

/** PLAN.md 5.1 — the wrong role never reaches a protected area. */
test('a buyer is turned away from the seller area and told why', async ({ page }) => {
  await signIn(page, 'buyer');
  await mockShop(page);
  await page.goto('/seller/inventory');

  await expect(page).toHaveURL(/\/products$/);
  await expect(page.getByRole('alert')).toContainText('doesn’t have access');
});

test('login returns a guest to the page they wanted', async ({ page }) => {
  await mockShop(page);
  await page.route(/\/api\/auth\/signin$/, (route) =>
    route.fulfill({ json: { id: 'buyer-1', username: 'mara', email: 'm@w.ro', roles: ['ROLE_USER'], accessToken: 'jwt', refreshToken: 'r' } }));

  await page.goto('/settings');
  await page.getByLabel('Username').fill('mara');
  await page.getByLabel('Password').fill('secret1');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page).toHaveURL(/\/settings$/);
  await expect(page.getByRole('button', { name: /mara/ })).toBeVisible();
});

test('a wrong password can be corrected without reloading', async ({ page }) => {
  await mockShop(page);
  await page.route(/\/api\/auth\/signin$/, (route) =>
    route.fulfill({ status: 401, json: { message: 'Bad credentials' } }));

  await page.goto('/login');
  await page.getByLabel('Username').fill('mara');
  await page.getByLabel('Password').fill('wrong');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page.getByRole('alert').filter({ hasText: 'Bad credentials' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Log in' })).toBeEnabled();
});

test('an expired session sends the user to login with a notice', async ({ page }) => {
  await signIn(page, 'buyer');
  await mockShop(page);
  await page.route(/\/api\/user\/profile\//, (route) => route.fulfill({ status: 401, json: {} }));

  await page.goto('/settings');

  await expect(page).toHaveURL(/\/login\?returnUrl=/);
  await expect(page.getByText('Your session has expired')).toBeVisible();
});

/** PLAN.md 5.3 — no browser dialogs anywhere in a destructive flow. */
test('a seller cancels an order through the in-app dialog', async ({ page }) => {
  page.on('dialog', () => {
    throw new Error('Native browser dialog opened');
  });
  await signIn(page, 'seller');
  await mockShop(page);
  let body: Record<string, unknown> | undefined;
  await page.route(/\/api\/seller\/orders\/o-0\/status$/, (route) => {
    body = route.request().postDataJSON();
    return route.fulfill({ json: orders[0] });
  });

  await page.goto('/seller/orders');
  await page.getByRole('row', { name: /WV-200/ }).getByRole('button', { name: 'Cancel' }).click();

  const dialog = page.getByRole('dialog', { name: 'Cancel order WV-200?' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Cancel order' })).toBeDisabled();

  await dialog.getByLabel(/Reason/).fill('Out of willow');
  await dialog.getByRole('button', { name: 'Cancel order' }).click();

  await expect(dialog).toBeHidden();
  await expect(page.getByText('Order WV-200 cancelled.')).toBeVisible();
  expect(body).toMatchObject({ status: 'CANCELLED', reason: 'Out of willow' });
});

test('dismissing a dialog with Escape changes nothing', async ({ page }) => {
  await signIn(page, 'seller');
  await mockShop(page);
  let called = false;
  await page.route(/\/api\/products\/seller\/s-1\/product\/p-1$/, (route) => {
    called = true;
    return route.fulfill({ json: {} });
  });

  await page.goto('/seller/inventory');
  await page.locator('.inventory-card, .product-card, .card').filter({ hasText: 'Woven willow basket' }).first()
    .locator('button.text-danger').click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await page.keyboard.press('Escape');

  await expect(dialog).toBeHidden();
  expect(called).toBe(false);
});

/** PLAN.md 5.6 — the next person on a shared browser doesn't inherit a cart. */
test('logging out leaves the cart behind', async ({ page }) => {
  await signIn(page, 'buyer');
  await seedCart(page, 'buyer-1');
  await mockShop(page);
  await page.goto('/products');

  await expect(page.getByRole('link', { name: 'Cart, 3 items' })).toBeVisible();
  await page.getByRole('button', { name: /Account menu/ }).click();
  await page.getByRole('button', { name: 'Log out' }).click();

  await expect(page.getByRole('link', { name: 'Cart', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Log in' })).toBeVisible();
});

test('Buy now goes to checkout and creates no order on its own', async ({ page }) => {
  await signIn(page, 'buyer');
  await mockShop(page);
  let posted = false;
  await page.route(/\/api\/orders$/, (route) => {
    posted = route.request().method() === 'POST';
    return route.fulfill({ json: [] });
  });

  await page.goto('/products/p-1');
  await page.getByRole('button', { name: 'Buy now' }).click();

  await expect(page).toHaveURL(/\/cart$/);
  await expect(page.getByText(catalog[0].name)).toBeVisible();
  await expect(page.getByRole('group', { name: 'Ship to' })).toBeVisible();
  expect(posted).toBe(false);
});

test('the catalog says so when the API is down, and shows no invented products', async ({ page }) => {
  await mockShop(page);
  await page.route(/\/api\/products(\?.*)?$/, (route) => route.fulfill({ status: 503, json: {} }));

  await page.goto('/products');

  await expect(page.getByRole('heading', { name: 'The catalog didn’t load' })).toBeVisible();
  await expect(page.locator('.product-card')).toHaveCount(0);
});

test('the shop menu works from the keyboard', async ({ page }) => {
  await mockShop(page);
  await page.goto('/products');

  const shop = page.getByRole('button', { name: 'Shop' });
  await shop.focus();
  await page.keyboard.press('Enter');
  await expect(shop).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('link', { name: 'Home & Living' })).toBeVisible();

  await page.keyboard.press('Escape');
  await expect(shop).toHaveAttribute('aria-expanded', 'false');
});
