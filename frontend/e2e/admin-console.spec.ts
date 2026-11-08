import { expect, test } from '@playwright/test';
import { skipIfAppDown } from './helpers';

test.describe('Admin console access', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await skipIfAppDown(page, testInfo);
  });

  test('admin routes require authentication', async ({ page }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/login/i);
  });

  test('customer cannot use admin after customer login when credentials provided', async ({
    page,
  }, testInfo) => {
    const email = process.env.E2E_CUSTOMER_EMAIL;
    const password = process.env.E2E_CUSTOMER_PASSWORD;
    if (!email || !password) {
      testInfo.skip(true, 'Set E2E_CUSTOMER_EMAIL / E2E_CUSTOMER_PASSWORD for this check');
    }

    await page.goto('/login');
    await page.getByLabel(/email/i).fill(email!);
    await page.getByLabel(/password/i).fill(password!);
    await page.getByRole('button', { name: /log in|sign in/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15_000 });

    await page.goto('/admin');
    await expect(page).toHaveURL(/unauthorized|login|\//);
  });
});
