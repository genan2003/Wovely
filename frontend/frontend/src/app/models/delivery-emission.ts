/** Delivery CO₂ level of a listing (PLAN.md 3.5). The server computes it; never sent by the client. */
export type DeliveryEmission = 'ZERO' | 'LOW' | 'STANDARD';

/** Catalog filter: the dirtiest acceptable level, or '' for any. */
export type DeliveryEmissionFilter = '' | 'LOW' | 'ZERO';

const ZERO_METHODS = /pick ?up|bicycle|bike|on foot|walk/;
const LOW_METHODS = /carbon[ -]?neutral|electric|\bev\b|eco/;

/**
 * Same rule as the products service's `EDeliveryEmission.classify`. Used only for previews (seller form)
 * and the offline demo catalog; real listings carry the server-computed `deliveryEmission`.
 */
export function classifyDelivery(shippingMethod?: string | null, co2EmissionScore?: string | null,
                                 localPickup?: boolean | null): DeliveryEmission {
  const method = (shippingMethod ?? '').toLowerCase();
  if (localPickup || ZERO_METHODS.test(method)) return 'ZERO';
  if (LOW_METHODS.test(method) || (co2EmissionScore ?? '').trim().toLowerCase() === 'low') return 'LOW';
  return 'STANDARD';
}

/** True if `level` is at least as clean as `ceiling` (ZERO ≤ LOW ≤ STANDARD). */
export function withinCeiling(level: DeliveryEmission, ceiling: DeliveryEmissionFilter): boolean {
  if (!ceiling) return true;
  const rank: Record<DeliveryEmission, number> = { ZERO: 0, LOW: 1, STANDARD: 2 };
  return rank[level] <= rank[ceiling];
}
