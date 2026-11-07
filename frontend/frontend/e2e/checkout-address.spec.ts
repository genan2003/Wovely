import { expect, test } from '@playwright/test';
import { mockBackend, sampleProducts } from './fixtures';

/** Checkout sends the chosen address-book entry instead of a hardcoded address. */
test('buyer picks a saved address at checkout', async ({ page }) => {
  const addresses = [
    { id: 'home', label: 'Home', recipientName: 'Ana Pop', street: 'Str. Florilor 3', city: 'Sibiu',
      postalCode: '550000', country: 'Romania', default: true },
    { id: 'studio', label: 'Studio', recipientName: 'Ana Pop', street: 'Bd. Unirii 10', city: 'Cluj-Napoca',
      postalCode: '400000', country: 'Romania', default: false },
  ];
  await page.addInitScript(([product]) => {
    sessionStorage.setItem('auth-user', JSON.stringify({ id: 'buyer-1', username: 'testuser', roles: ['ROLE_USER'] }));
    sessionStorage.setItem('auth-token', 'e2e-token');
    localStorage.setItem('wovely_cart', JSON.stringify([{ ...product, quantity: 1 }]));
  }, [sampleProducts[0]]);

  let orderBody: Record<string, unknown> | undefined;
  await mockBackend(page, {
    '^/api/wallet$': { balanceCents: 10000, balance: 100, currency: 'RON' },
    '^/api/user/addresses$': addresses,
    '^/api/products/p-1$': sampleProducts[0],
  });
  await page.route(/\/api\/orders$/, async (route) => {
    if (route.request().method() !== 'POST') return route.fulfill({ json: [] });
    orderBody = route.request().postDataJSON();
    return route.fulfill({ status: 201, json: { id: 'o-1', status: 'PENDING', items: [] } });
  });

  await page.goto('/cart');
  const shipTo = page.getByRole('group', { name: 'Ship to' });
  await expect(shipTo.getByLabel(/Str\. Florilor 3/)).toBeChecked();
  await shipTo.getByLabel(/Bd\. Unirii 10/).check();
  await page.getByRole('button', { name: /Checkout for this Shop/ }).click();

  await expect.poll(() => orderBody).toBeDefined();
  expect(orderBody).toMatchObject({ addressId: 'studio', deliveryOption: 'BICYCLE', items: [{ productId: 'p-1', quantity: 1 }] });
  expect(orderBody).not.toHaveProperty('shippingAddress');
});

test('checkout is blocked until an address exists', async ({ page }) => {
  await page.addInitScript(([product]) => {
    sessionStorage.setItem('auth-user', JSON.stringify({ id: 'buyer-1', username: 'testuser', roles: ['ROLE_USER'] }));
    sessionStorage.setItem('auth-token', 'e2e-token');
    localStorage.setItem('wovely_cart', JSON.stringify([{ ...product, quantity: 1 }]));
  }, [sampleProducts[0]]);
  await mockBackend(page, { '^/api/user/addresses$': [], '^/api/wallet$': { balanceCents: 10000, balance: 100, currency: 'RON' } });

  await page.goto('/cart');
  await expect(page.getByText('Add a shipping address before checking out.')).toBeVisible();
  await expect(page.getByRole('button', { name: /Checkout for this Shop/ })).toBeDisabled();
  await expect(page.getByRole('link', { name: 'Add an address in Settings' })).toHaveAttribute('href', '/settings#addresses');
});

/** PLAN.md 3.7 — the wallet must cover the items before checkout is possible. */
test('checkout is blocked when the wallet balance is too low', async ({ page }) => {
  await page.addInitScript(([product]) => {
    sessionStorage.setItem('auth-user', JSON.stringify({ id: 'buyer-1', username: 'testuser', roles: ['ROLE_USER'] }));
    sessionStorage.setItem('auth-token', 'e2e-token');
    localStorage.setItem('wovely_cart', JSON.stringify([{ ...product, quantity: 1 }]));
  }, [sampleProducts[0]]);
  await mockBackend(page, {
    '^/api/wallet$': { balanceCents: 1000, balance: 10, currency: 'RON' },
    '^/api/user/addresses$': [{ id: 'home', recipientName: 'Ana Pop', street: 'Str. Florilor 3', city: 'Sibiu', country: 'Romania', default: true }],
  });

  await page.goto('/cart');
  await expect(page.getByText('Paid from your wallet: 52.50 RON')).toBeVisible(); // 42.50 + 10.00 bicycle delivery
  await expect(page.getByText("Your balance doesn't cover this order.")).toBeVisible();
  await expect(page.getByRole('button', { name: /Checkout for this Shop/ })).toBeDisabled();
  await expect(page.getByRole('link', { name: 'Add funds' })).toHaveAttribute('href', '/settings#wallet');
});

/** PLAN.md 3.7 — delivery is charged: the server's price, shown before paying, sent with the order. */
test('the chosen delivery is added to the total and sent with the order', async ({ page }) => {
  await page.addInitScript(([product]) => {
    sessionStorage.setItem('auth-user', JSON.stringify({ id: 'buyer-1', username: 'testuser', roles: ['ROLE_USER'] }));
    sessionStorage.setItem('auth-token', 'e2e-token');
    localStorage.setItem('wovely_cart', JSON.stringify([{ ...product, quantity: 1 }]));
  }, [sampleProducts[0]]);
  let orderBody: Record<string, unknown> | undefined;
  await mockBackend(page, {
    '^/api/wallet$': { balanceCents: 10000, balance: 100, currency: 'RON' },
    '^/api/user/addresses$': [{ id: 'home', recipientName: 'Ana Pop', street: 'Str. Florilor 3', city: 'Sibiu', country: 'Romania', default: true }],
    '^/api/products/p-1$': sampleProducts[0],
  });
  await page.route(/\/api\/orders$/, async (route) => {
    if (route.request().method() !== 'POST') return route.fulfill({ json: [] });
    orderBody = route.request().postDataJSON();
    return route.fulfill({ status: 201, json: { id: 'o-1', status: 'PENDING', items: [] } });
  });

  await page.goto('/cart');
  const delivery = page.getByRole('group', { name: 'Delivery' });
  await expect(delivery.getByRole('radio', { name: /Bicycle delivery/ })).toBeChecked(); // lowest CO₂ first
  await delivery.getByText('Carbon-neutral courier').click();

  await expect(page.getByText('Paid from your wallet: 67.50 RON')).toBeVisible(); // 42.50 + 25.00
  await page.getByRole('button', { name: /Checkout for this Shop/ }).click();
  await expect.poll(() => orderBody).toBeDefined();
  expect(orderBody).toMatchObject({ deliveryOption: 'CARBON_NEUTRAL_COURIER' });
  expect(orderBody).not.toHaveProperty('deliveryFee');
});

test('checkout pauses when delivery prices cannot be loaded', async ({ page }) => {
  await page.addInitScript(([product]) => {
    sessionStorage.setItem('auth-user', JSON.stringify({ id: 'buyer-1', username: 'testuser', roles: ['ROLE_USER'] }));
    sessionStorage.setItem('auth-token', 'e2e-token');
    localStorage.setItem('wovely_cart', JSON.stringify([{ ...product, quantity: 1 }]));
  }, [sampleProducts[0]]);
  await mockBackend(page, {
    '^/api/wallet$': { balanceCents: 10000, balance: 100, currency: 'RON' },
    '^/api/user/addresses$': [{ id: 'home', recipientName: 'Ana Pop', street: 'Str. Florilor 3', city: 'Sibiu', country: 'Romania', default: true }],
  });
  await page.route(/\/api\/public\/delivery-options$/, (route) => route.fulfill({ status: 503, json: { message: 'down' } }));

  await page.goto('/cart');
  await expect(page.getByRole('alert').filter({ hasText: 'Delivery prices couldn’t be loaded' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Checkout for this Shop/ })).toBeDisabled();
});
