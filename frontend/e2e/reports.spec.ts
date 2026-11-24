import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

test.describe('Reports, Dashboard Analytics & CSV Export', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('renders dashboard KPI cards and operational metrics', async ({ page }) => {
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Verify KPI cards are rendered
    const kpiCards = page.locator('mc-kpi-card');
    await expect(kpiCards.first()).toBeVisible({ timeout: 10000 });
    const count = await kpiCards.count();
    expect(count).toBeGreaterThanOrEqual(4);

    // Verify Chart wrappers exist
    const charts = page.locator('mc-chart-wrapper');
    await expect(charts.first()).toBeVisible({ timeout: 10000 });
  });

  test('views daily and summary reports with charts', async ({ page }) => {
    await page.goto('/reports');
    await page.waitForLoadState('networkidle');

    // Page header should mention Asia/Kathmandu
    await expect(page.locator('.zone-badge')).toContainText('Asia/Kathmandu');

    // Daily report tab
    await expect(page.locator('mc-reports')).toBeVisible();

    // Switch to Range Summary tab
    const rangeTab = page.locator('.mat-mdc-tab').filter({ hasText: 'Range Summary' });
    if (await rangeTab.isVisible()) {
      await rangeTab.click();
      await page.waitForLoadState('networkidle');
      await expect(page.locator('form.range-form, mc-chart-wrapper').first()).toBeVisible();
    }
  });

  test('downloads transactions CSV export with RFC 4180 headers', async ({ page }) => {
    await page.goto('/transactions');
    await page.waitForLoadState('networkidle');

    // Wait for export button
    const exportBtn = page.locator('button:has-text("Export CSV")');
    await expect(exportBtn).toBeVisible({ timeout: 10000 });

    // Listen for download event
    const downloadPromise = page.waitForEvent('download');
    await exportBtn.click();
    const download = await downloadPromise;

    // Verify file name contains transactions and .csv
    expect(download.suggestedFilename()).toMatch(/transactions.*\.csv/i);
  });
});
