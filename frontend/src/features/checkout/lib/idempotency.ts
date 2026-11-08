const STORAGE_PREFIX = 'checkout-idem:';

function normalizePromo(code: string | null | undefined): string {
  if (!code || !code.trim()) {
    return '';
  }
  return code.trim().toUpperCase();
}

function normalizeShipping(shippingAddressJson: string | null | undefined): string {
  if (!shippingAddressJson || !shippingAddressJson.trim()) {
    return '';
  }
  return shippingAddressJson.trim();
}

/** Mirrors backend CheckoutIdempotencyHasher fingerprint material (not the SHA itself). */
export function checkoutPayloadFingerprint(
  promotionCode: string | null | undefined,
  shippingAddressJson: string | null | undefined,
): string {
  return `v1|promo=${normalizePromo(promotionCode)}|ship=${normalizeShipping(shippingAddressJson)}`;
}

export function createIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * Returns a stable Idempotency-Key for the same checkout payload within this browser session.
 * Changing promo or shipping generates a new key. Uncertain retries with unchanged payload reuse it.
 */
export function getOrCreateCheckoutIdempotencyKey(
  promotionCode: string | null | undefined,
  shippingAddressJson: string | null | undefined,
): string {
  const fingerprint = checkoutPayloadFingerprint(promotionCode, shippingAddressJson);
  const storageKey = STORAGE_PREFIX + fingerprint;
  try {
    const existing = sessionStorage.getItem(storageKey);
    if (existing && existing.length > 0 && existing.length <= 128) {
      return existing;
    }
    const next = createIdempotencyKey();
    sessionStorage.setItem(storageKey, next);
    return next;
  } catch {
    return createIdempotencyKey();
  }
}

/** Force a fresh key after IDEMPOTENCY_KEY_CONFLICT or intentional restart. */
export function rotateCheckoutIdempotencyKey(
  promotionCode: string | null | undefined,
  shippingAddressJson: string | null | undefined,
): string {
  const fingerprint = checkoutPayloadFingerprint(promotionCode, shippingAddressJson);
  const storageKey = STORAGE_PREFIX + fingerprint;
  const next = createIdempotencyKey();
  try {
    sessionStorage.setItem(storageKey, next);
  } catch {
    /* ignore quota */
  }
  return next;
}
