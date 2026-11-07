import { expect, test } from '@playwright/test';
import { mockShop } from './visual-fixtures';

/** A maker can open a seller account from the browser; the radio group works from the keyboard. */
test('a maker signs up as a seller', async ({ page }) => {
  await mockShop(page);
  let body: Record<string, unknown> | undefined;
  await page.route(/\/api\/auth\/signup$/, (route) => {
    body = route.request().postDataJSON();
    return route.fulfill({ json: { message: 'User registered successfully!' } });
  });

  await page.goto('/register');
  await page.getByLabel('Username').fill('ana_weaves');
  await page.getByLabel('Email').fill('ana@example.com');
  await page.getByLabel('Password').fill('secret123');

  const group = page.getByRole('group', { name: 'How will you use Wovely?' });
  await group.getByRole('radio', { name: /^Shop Buy/ }).focus();
  await page.keyboard.press('ArrowDown');
  await expect(group.getByRole('radio', { name: /Shop and sell my work/ })).toBeChecked();

  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Your maker account is ready' })).toBeVisible();
  expect(body?.['roles']).toEqual(['seller']);
});
