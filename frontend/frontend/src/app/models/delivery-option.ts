/** Delivery choice at checkout; prices come from the server (GET /api/public/delivery-options). */
export type DeliveryOptionId = 'BICYCLE' | 'ELECTRIC_VAN' | 'CARBON_NEUTRAL_COURIER';

export interface DeliveryOption {
  id: DeliveryOptionId;
  label: string;
  /** RON, added to the order total by the server. */
  fee: number;
  feeCents: number;
  co2Kg: number;
  estimatedDays: number;
}
