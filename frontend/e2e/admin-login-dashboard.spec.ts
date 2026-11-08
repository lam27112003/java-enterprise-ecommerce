import { expect, test } from '@playwright/test';
import { skipIfAppDown } from './helpers';

test.describe('Admin dashboard (credentialed)', () => {
  test('admin can open dashboard when credentials are set', async ({ page }, testInfo) => {
    await skipIfAppDown(page, testInfo);

    const email = process.env.E2E_ADMIN_EMAIL;
    const password = process.env.E2E_ADMIN_PASSWORD;
    if (!email || !password) {
      testInfo.skip(true, 'Set E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD to run');
    }

    await page.goto('/login');
    await page.getByLabel(/email/i).fill(email!);
    await page.getByLabel(/password/i).fill(password!);
    await page.getByRole('button', { name: /log in|sign in/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15_000 });

    await page.goto('/admin');
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/payment-period/i)).toBeVisible();

    await page.goto('/admin/orders');
    await expect(page.getByRole('heading', { name: /orders/i })).toBeVisible();

    await page.goto('/admin/refunds');
    await expect(page.getByRole('heading', { name: /refund/i })).toBeVisible();
  });
});
