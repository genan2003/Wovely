import { expect, test } from '@playwright/test';
import { mockBackend, sampleProducts } from './fixtures';

/** PLAN.md 3.3 — "Verified purchase" is shown only for reviews the server verified. */
test('product page distinguishes verified purchase reviews', async ({ page }) => {
  await mockBackend(page, {
    '^/api/products/p-1$': sampleProducts[0],
    '^/api/reviews/product/p-1$': {
      averageRating: 4.5,
      totalReviews: 2,
      reviews: [
        { id: 'r1', productId: 'p-1', userId: 'b1', username: 'realbuyer', rating: 5, comment: 'Sturdy.', verifiedPurchase: true },
        { id: 'r2', productId: 'p-1', userId: 'b2', username: 'browser', rating: 4, comment: 'Pretty.', verifiedPurchase: false },
      ],
    },
    '^/api/public/users/seller/s-1$': { id: 's-1', username: 'seller' },
  });
  await page.goto('/products/p-1');

  const verified = page.locator('.review-item', { hasText: 'realbuyer' });
  const unverified = page.locator('.review-item', { hasText: 'browser' });
  await expect(verified.getByText('Verified purchase')).toBeVisible();
  await expect(unverified).toBeVisible();
  await expect(unverified.getByText('Verified purchase')).toHaveCount(0);
});
