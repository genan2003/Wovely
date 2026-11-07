import { deliveryOptions } from './fixtures';
import { Page } from '@playwright/test';

/** Realistic catalog used by the accessibility and screen-review specs. */
export const catalog = [
  ['p-1', 'Woven willow basket', 42.5, 'ZERO', 'Sibiu'],
  ['p-2', 'Indigo linen table runner', 118, 'LOW', 'Cluj-Napoca'],
  ['p-3', 'Speckled stoneware mug', 64, 'STANDARD', 'Horezu'],
  ['p-4', 'Beeswax taper candles, set of four', 36, 'LOW', 'Brașov'],
  ['p-5', 'Hand-felted wool slippers', 149, 'ZERO', 'Sibiu'],
  ['p-6', 'Oak serving board', 95, 'STANDARD', 'Suceava'],
].map(([id, name, price, deliveryEmission, city]) => ({
  id, name, price, deliveryEmission, city,
  description: 'Made in small batches in the maker’s own workshop. Each one differs slightly from the last.',
  sellerId: 's-1', sellerName: 'Ana Pop', imageUrl: '', category: 'Home', categoryPath: 'Home', region: city,
  co2EmissionScore: 'Low', shippingMethod: 'Bicycle', isHandmade: true, stockQuantity: 5, lowStockThreshold: 2,
  status: 'APPROVED', localPickup: deliveryEmission === 'ZERO',
}));

export const orders = (['PENDING', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELLED'] as const).map((status, i) => ({
  id: `o-${i}`, orderNumber: `WV-20${i}`, buyerId: 'buyer-1', buyerName: 'testuser', sellerId: 's-1', sellerName: 'Ana Pop',
  status, totalAmount: 42.5, createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z', shippingAddress: 'Str. Florilor 3, Sibiu',
  items: [{ productId: 'p-1', productName: 'Woven willow basket', quantity: 1, price: 42.5, imageUrl: '' }],
  timeline: [
    { from: null, status: 'PENDING', at: '2026-11-01T10:00:00Z', actor: 'BUYER', reason: null, inferred: false },
    ...(status === 'PENDING' ? [] : [{
      from: 'PENDING', status, at: '2026-11-03T15:20:00Z', actor: status === 'COMPLETED' ? 'SYSTEM' : 'SELLER',
      reason: status === 'CANCELLED' ? 'Out of willow until spring' : null, inferred: false,
    }]),
  ],
}));

export const inventoryItems = catalog.map((p, i) => ({
  productId: p.id, sellerId: 's-1', productName: p.name, description: p.description, imageUrl: '', category: 'Home',
  price: p.price, stockQuantity: [5, 1, 0, 12, 3, 8][i], lowStockThreshold: 2, isLowStock: i === 1, isOutOfStock: i === 2,
  co2EmissionScore: 'Low', shippingMethod: 'Bicycle', deliveryEmission: p.deliveryEmission,
  status: (['APPROVED', 'NEEDS_CHANGES', 'APPROVED', 'PENDING', 'APPROVED', 'APPROVED'] as const)[i],
}));

export type Persona = 'guest' | 'buyer' | 'seller' | 'admin';

const roles: Record<Exclude<Persona, 'guest'>, string[]> = {
  buyer: ['ROLE_USER'], seller: ['ROLE_USER', 'ROLE_SELLER'], admin: ['ROLE_ADMIN'],
};

export async function signIn(page: Page, persona: Persona): Promise<void> {
  if (persona === 'guest') return;
  await page.addInitScript(([id, list]) => {
    sessionStorage.setItem('auth-user', JSON.stringify({ id, username: id, email: `${id}@wovely.test`, roles: list, accessToken: 'e2e' }));
    sessionStorage.setItem('auth-token', 'e2e');
  }, [persona === 'seller' ? 's-1' : `${persona}-1`, roles[persona]] as const);
}

/** Answers every API call with demo data; the catch-all is an empty list. */
export async function mockShop(page: Page): Promise<void> {
  const conversation = { id: 'm1', senderId: 's-1', receiverId: 'buyer-1', content: 'The basket ships tomorrow.', timestamp: '2026-11-01T10:00:00Z', isRead: false };
  const routes: [RegExp, unknown][] = [
    [/^\/api\/public\/delivery-options$/, deliveryOptions],
    [/^\/api\/orders\/o-2$/, orders[2]], // DELIVERED, for the review page
    [/^\/api\/products$/, catalog],
    [/^\/api\/products\/p-\d$/, catalog[0]],
    [/^\/api\/products\/seller\/[^/]+$/, { items: inventoryItems, totalItems: 6, lowStockCount: 1, outOfStockCount: 1 }],
    [/^\/api\/products\/seller\/[^/]+\/dashboard$/, { totalProducts: 6, inStock: 5, lowStock: 1, outOfStock: 1, categoryBreakdown: { Home: 6 }, lowStockPreview: [inventoryItems[1]] }],
    [/^\/api\/products\/admin\/pending$/, catalog.slice(0, 2).map((p) => ({ ...p, status: 'PENDING' }))],
    [/^\/api\/reviews\/product\//, { averageRating: 4.5, totalReviews: 2, reviews: [
      { id: 'r1', productId: 'p-1', userId: 'b1', username: 'mara', rating: 5, comment: 'Sturdy, and smells of willow.', verifiedPurchase: true },
      { id: 'r2', productId: 'p-1', userId: 'b2', username: 'dan', rating: 4, comment: 'Lovely weave.', verifiedPurchase: false },
    ] }],
    [/^\/api\/public\/users\/seller\/[^/]+$/, { id: 's-1', username: 'anapop', fullName: 'Ana Pop', city: 'Sibiu', makerStory: 'I weave willow from the Olt river banks, the way my grandmother did.' }],
    [/^\/api\/public\/users\/seller\/[^/]+\/reviews$/, []],
    [/^\/api\/categories\/roots$/, [{ id: 'c1', name: 'Home & Living', parentId: null, slug: 'home', level: 0 }, { id: 'c2', name: 'Clothing', parentId: null, slug: 'clothing', level: 0 }]],
    [/^\/api\/categories\/children\//, [{ id: 'c3', name: 'Ceramics', parentId: 'c1', slug: 'ceramics', level: 1 }, { id: 'c4', name: 'Textiles', parentId: 'c1', slug: 'textiles', level: 1 }]],
    [/^\/api\/orders\/buyer\//, orders],
    [/^\/api\/seller\/orders\/seller\//, { orders, totalOrders: orders.length }],
    [/^\/api\/admin\/orders$/, orders],
    [/^\/api\/admin\/users$/, [{ id: 'u1', username: 'anapop', fullName: 'Ana Pop', email: 'ana@wovely.test', roles: ['ROLE_SELLER'], strikes: 1, accountStatus: 'ACTIVE' }]],
    [/^\/api\/wallet$/, { balanceCents: 25000, balance: 250, currency: 'RON' }],
    [/^\/api\/user\/addresses$/, [{ id: 'home', label: 'Home', recipientName: 'Mara Ionescu', street: 'Str. Florilor 3', city: 'Sibiu', postalCode: '550000', country: 'Romania', default: true }]],
    [/^\/api\/user\/profile\//, { username: 'anapop', fullName: 'Ana Pop', city: 'Sibiu', makerStory: 'I weave willow.', workshopImageUrl: '' }],
    [/^\/api\/chat\/conversations\//, [conversation]],
    [/^\/api\/chat\/unread\//, [conversation]],
  ];
  await page.route(/\/api\//, (route) => {
    const path = new URL(route.request().url()).pathname;
    const match = routes.find(([pattern]) => pattern.test(path));
    return route.fulfill({ json: match ? match[1] : [] });
  });
  await page.routeWebSocket(/\/ws/, () => undefined);
}

export async function seedCart(page: Page, userId: string): Promise<void> {
  await page.addInitScript(([key, items]) => localStorage.setItem(key as string, items as string), [
    `wovely_cart_${userId}`,
    JSON.stringify([{ ...catalog[0], quantity: 2 }, { ...catalog[1], sellerId: 's-2', sellerName: 'Ioana Vlad', quantity: 1 }]),
  ]);
}
