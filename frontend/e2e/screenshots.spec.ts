import { test } from '@playwright/test';
import * as path from 'path';
import { loginAsAdmin } from './helpers';

test.describe('Documentation UI Screenshot Captures', () => {
  const screenshotsDir = path.resolve(__dirname, '../../docs/screenshots');

  test('captures clean UI screenshots for documentation and README', async ({ page }) => {
    // 1. Login page
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(screenshotsDir, 'login.png') });

    // 2. Authenticate
    await loginAsAdmin(page);

    // 3. Dashboard
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(screenshotsDir, 'dashboard.png') });

    // 4. Cards
    await page.goto('/cards');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(screenshotsDir, 'cards.png') });

    // 5. Simulator
    await page.goto('/simulator');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(screenshotsDir, 'simulator.png') });

    // 6. Fraud Rules
    await page.goto('/fraud-rules');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(screenshotsDir, 'fraud-rules.png') });

    // 7. Disputes
    await page.goto('/disputes');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(screenshotsDir, 'disputes.png') });

    // 8. Reports
    await page.goto('/reports');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(screenshotsDir, 'reports.png') });
  });
});
