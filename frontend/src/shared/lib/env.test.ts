import { describe, expect, it } from 'vitest';
import { assertStripeTestPublishableKey } from './env';

describe('env', () => {
  it('rejects secret keys in frontend', () => {
    expect(() => assertStripeTestPublishableKey('sk_test_abc')).toThrow(/secret/i);
  });

  it('rejects live publishable keys', () => {
    expect(() => assertStripeTestPublishableKey('pk_live_abc')).toThrow(/Live Mode/i);
  });

  it('allows empty or test publishable keys', () => {
    expect(() => assertStripeTestPublishableKey('')).not.toThrow();
    expect(() => assertStripeTestPublishableKey('pk_test_abc')).not.toThrow();
  });
});
