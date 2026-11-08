export type ShippingAddress = {
  fullName: string;
  line1: string;
  line2: string;
  city: string;
  region: string;
  postalCode: string;
  country: string;
};

export const EMPTY_SHIPPING: ShippingAddress = {
  fullName: '',
  line1: '',
  line2: '',
  city: '',
  region: '',
  postalCode: '',
  country: 'US',
};

/** Stable key order so idempotency hash stays stable for unchanged addresses. */
export function serializeShippingAddress(address: ShippingAddress): string {
  const payload = {
    fullName: address.fullName.trim(),
    line1: address.line1.trim(),
    line2: address.line2.trim(),
    city: address.city.trim(),
    region: address.region.trim(),
    postalCode: address.postalCode.trim(),
    country: address.country.trim().toUpperCase(),
  };
  return JSON.stringify(payload);
}

export function parseShippingAddress(json: string | null | undefined): ShippingAddress | null {
  if (!json || !json.trim()) {
    return null;
  }
  try {
    const raw = JSON.parse(json) as Partial<ShippingAddress>;
    return {
      fullName: String(raw.fullName ?? ''),
      line1: String(raw.line1 ?? ''),
      line2: String(raw.line2 ?? ''),
      city: String(raw.city ?? ''),
      region: String(raw.region ?? ''),
      postalCode: String(raw.postalCode ?? ''),
      country: String(raw.country ?? 'US'),
    };
  } catch {
    return null;
  }
}

export function validateShippingAddress(address: ShippingAddress): string | null {
  if (!address.fullName.trim()) return 'Full name is required';
  if (!address.line1.trim()) return 'Address line 1 is required';
  if (!address.city.trim()) return 'City is required';
  if (!address.region.trim()) return 'State / region is required';
  if (!address.postalCode.trim()) return 'Postal code is required';
  if (!address.country.trim()) return 'Country is required';
  return null;
}
