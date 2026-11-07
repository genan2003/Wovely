import { expect, test } from '@playwright/test';
import { mockShop, orders, signIn } from './visual-fixtures';

/** Reviews can be given from the keyboard and are only posted once everything is rated. */
test('a buyer rates the seller and items with the keyboard', async ({ page }) => {
  await signIn(page, 'buyer');
  await mockShop(page);
  const posted: { url: string; body: Record<string, unknown> }[] = [];
  await page.route(/\/api\/(reviews|public\/users\/seller\/[^/]+\/reviews)$/, (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    posted.push({ url: route.request().url(), body: route.request().postDataJSON() });
    return route.fulfill({ status: 201, json: {} });
  });
  await page.route(/\/api\/orders\/o-2\/reviewed/, (route) => route.fulfill({ json: {} }));

  await page.goto('/review-order/o-2');
  const post = page.getByRole('button', { name: 'Post review' });
  await expect(post).toBeDisabled();
  await expect(page.getByText('Rate the seller and every item to post your review.')).toBeVisible();

  const overall = page.getByRole('group', { name: 'How was your overall experience with the seller?' });
  await overall.getByRole('radio', { name: '1 of 5 stars' }).focus();
  await page.keyboard.press('ArrowRight'); // native radio group: arrows move the choice
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('ArrowRight');
  await expect(overall.getByRole('radio', { name: '4 of 5 stars' })).toBeChecked();

  // pointer users click the star (the label of a visually hidden radio)
  const star = (group: string, n: number) => page.getByRole('group', { name: group }).locator('label.star').nth(n - 1);
  await star('How was the communication with the seller?', 5).click();
  await star('Did the order arrive in the promised time?', 5).click();
  for (const item of orders[2].items) {
    await star(`Rating for ${item.productName}`, 3).click();
    await expect(page.getByRole('group', { name: `Rating for ${item.productName}` })
      .getByRole('radio', { name: '3 of 5 stars' })).toBeChecked();
  }

  await expect(post).toBeEnabled();
  await post.click();
  await expect.poll(() => posted.length).toBe(orders[2].items.length + 1);
  const seller = posted.find((p) => p.url.includes('/seller/'))!;
  expect(seller.body).toMatchObject({ overallRating: 4, communicationRating: 5, shippingRating: 5 });
});
