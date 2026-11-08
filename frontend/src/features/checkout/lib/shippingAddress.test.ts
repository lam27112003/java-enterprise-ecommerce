import { describe, expect, it } from 'vitest';
import {
  EMPTY_SHIPPING,
  parseShippingAddress,
  serializeShippingAddress,
  validateShippingAddress,
} from './shippingAddress';

describe('shippingAddress', () => {
  it('serializes with stable field order and trim', () => {
    const json = serializeShippingAddress({
      ...EMPTY_SHIPPING,
      fullName: ' Ada ',
      line1: ' 1 Main ',
      city: ' Austin ',
      region: ' TX ',
      postalCode: ' 78701 ',
      country: 'us',
    });
    expect(json).toBe(
      '{"fullName":"Ada","line1":"1 Main","line2":"","city":"Austin","region":"TX","postalCode":"78701","country":"US"}',
    );
  });

  it('round-trips parse', () => {
    const original = {
      fullName: 'Ada',
      line1: '1 Main',
      line2: '',
      city: 'Austin',
      region: 'TX',
      postalCode: '78701',
      country: 'US',
    };
    expect(parseShippingAddress(serializeShippingAddress(original))).toEqual(original);
  });

  it('validates required fields', () => {
    expect(validateShippingAddress(EMPTY_SHIPPING)).toMatch(/Full name/i);
    expect(
      validateShippingAddress({
        fullName: 'Ada',
        line1: '1 Main',
        line2: '',
        city: 'Austin',
        region: 'TX',
        postalCode: '78701',
        country: 'US',
      }),
    ).toBeNull();
  });
});
