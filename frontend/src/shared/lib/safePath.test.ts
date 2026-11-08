import { describe, expect, it } from 'vitest';
import { isSafeInternalPath } from './safePath';

describe('isSafeInternalPath', () => {
  it('allows normal internal paths', () => {
    expect(isSafeInternalPath('/cart')).toBe(true);
    expect(isSafeInternalPath('/orders/1')).toBe(true);
    expect(isSafeInternalPath('/products/slug-name')).toBe(true);
  });

  it('allows safe query strings and fragments', () => {
    expect(isSafeInternalPath('/admin?tab=1')).toBe(true);
    expect(isSafeInternalPath('/orders/1?status=PAID')).toBe(true);
    expect(isSafeInternalPath('/account#profile')).toBe(true);
    expect(isSafeInternalPath('/search?q=hello%20world')).toBe(true);
  });

  it('rejects protocol-relative and external URLs', () => {
    expect(isSafeInternalPath('//evil.com')).toBe(false);
    expect(isSafeInternalPath('https://evil.com')).toBe(false);
    expect(isSafeInternalPath('http://evil.com/path')).toBe(false);
    expect(isSafeInternalPath('evil.com')).toBe(false);
    expect(isSafeInternalPath('\\evil.com')).toBe(false);
  });

  it('rejects literal backslashes', () => {
    expect(isSafeInternalPath('/\\evil.com')).toBe(false);
    expect(isSafeInternalPath('/path\\to')).toBe(false);
  });

  it('rejects URI-encoded backslashes', () => {
    expect(isSafeInternalPath('/%5Cevil.com')).toBe(false);
    expect(isSafeInternalPath('/%5cevil.com')).toBe(false);
    expect(isSafeInternalPath('/orders/%5C..')).toBe(false);
  });

  it('rejects double-encoded backslashes', () => {
    expect(isSafeInternalPath('/%255Cevil.com')).toBe(false);
    expect(isSafeInternalPath('/%25255Cevil.com')).toBe(false);
  });

  it('rejects malformed percent-encoding without throwing', () => {
    expect(isSafeInternalPath('/%')).toBe(false);
    expect(isSafeInternalPath('/%zz')).toBe(false);
    expect(isSafeInternalPath('/cart%')).toBe(false);
    expect(() => isSafeInternalPath('/%E0%A4%A')).not.toThrow();
    expect(isSafeInternalPath('/%E0%A4%A')).toBe(false);
  });

  it('rejects empty and non-string values', () => {
    expect(isSafeInternalPath('')).toBe(false);
    expect(isSafeInternalPath(null)).toBe(false);
    expect(isSafeInternalPath(undefined)).toBe(false);
    expect(isSafeInternalPath(123)).toBe(false);
  });
});
