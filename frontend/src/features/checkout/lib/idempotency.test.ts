import { afterEach, describe, expect, it } from 'vitest';
import {
  checkoutPayloadFingerprint,
  getOrCreateCheckoutIdempotencyKey,
  rotateCheckoutIdempotencyKey,
} from './idempotency';

describe('checkout idempotency helpers', () => {
  afterEach(() => {
    sessionStorage.clear();
  });

  it('fingerprints promo and shipping like the backend material', () => {
    expect(checkoutPayloadFingerprint(' save10 ', ' {"a":1} ')).toBe(
      'v1|promo=SAVE10|ship={"a":1}',
    );
    expect(checkoutPayloadFingerprint(null, null)).toBe('v1|promo=|ship=');
  });

  it('reuses the same key for unchanged payload', () => {
    const first = getOrCreateCheckoutIdempotencyKey('SAVE10', '{"line1":"A"}');
    const second = getOrCreateCheckoutIdempotencyKey('SAVE10', '{"line1":"A"}');
    expect(second).toBe(first);
  });

  it('issues a new key when shipping changes', () => {
    const first = getOrCreateCheckoutIdempotencyKey(null, '{"line1":"A"}');
    const second = getOrCreateCheckoutIdempotencyKey(null, '{"line1":"B"}');
    expect(second).not.toBe(first);
  });

  it('rotate replaces the stored key for the same payload', () => {
    const first = getOrCreateCheckoutIdempotencyKey(null, '{"x":1}');
    const rotated = rotateCheckoutIdempotencyKey(null, '{"x":1}');
    expect(rotated).not.toBe(first);
    expect(getOrCreateCheckoutIdempotencyKey(null, '{"x":1}')).toBe(rotated);
  });
});
