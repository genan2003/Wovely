/** Address-book entry (core `/api/user/addresses`). `default` is set by the server; exactly one is true. */
export interface Address {
  id: string;
  label?: string;
  recipientName: string;
  street: string;
  city: string;
  postalCode?: string;
  country: string;
  phone?: string;
  default: boolean;
}

export type AddressRequest = Omit<Address, 'id' | 'default'> & { makeDefault?: boolean };

/** Same one-line form the server stores on orders. */
export function formatAddress(a: Pick<Address, 'recipientName' | 'street' | 'city' | 'postalCode' | 'country'>): string {
  return [a.recipientName, a.street, [a.postalCode, a.city].filter(Boolean).join(' '), a.country]
    .map((p) => p?.trim())
    .filter(Boolean)
    .join(', ');
}
