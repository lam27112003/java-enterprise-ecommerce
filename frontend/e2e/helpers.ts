import type { Page, TestInfo } from '@playwright/test';

/** Skip the test when the local app is not reachable (ECONNREFUSED, etc.). */
export async function skipIfAppDown(page: Page, testInfo: TestInfo): Promise<void> {
  try {
    const response = await page.request.get('/', { timeout: 3_000 });
    if (!response.ok()) {
      testInfo.skip(true, `App not reachable (HTTP ${response.status()})`);
    }
  } catch {
    testInfo.skip(true, 'App not reachable (connection refused)');
  }
}
