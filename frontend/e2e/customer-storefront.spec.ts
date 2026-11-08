import { expect, test } from '@playwright/test';
import { skipIfAppDown } from './helpers';

test.describe('Customer storefront', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await skipIfAppDown(page, testInfo);
  });

  test('home and products are public', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText(/Enterprise E-commerce/i).first()).toBeVisible();
    await page.goto('/products');
    await expect(page.getByRole('heading', { name: /products/i })).toBeVisible({ timeout: 15_000 });
  });

  test('cart requires customer auth', async ({ page }) => {
    await page.goto('/cart');
    await expect(page).toHaveURL(/login/i);
  });

  test('checkout requires customer auth', async ({ page }) => {
    await page.goto('/checkout');
    await expect(page).toHaveURL(/login/i);
  });
});
