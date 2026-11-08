import { describe, expect, it } from 'vitest';
import { formatMoney } from './money';

describe('formatMoney', () => {
  it('formats cents as currency', () => {
    expect(formatMoney(1299, 'USD')).toMatch(/12\.99/);
  });
});
