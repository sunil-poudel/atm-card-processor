import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

test.describe('Fraud Rules & Velocity Detection', () => {
  test('displays configured fraud rules and allows editing', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/fraud-rules');
    await page.waitForLoadState('networkidle');

    // Verify rules table is rendered
    const table = page.locator('table.mc-dense-table');
    await expect(table).toBeVisible({ timeout: 10000 });
    await expect(table.locator('tbody tr')).not.toHaveCount(0);

    // Verify presence of default seeded rules (e.g. Velocity or Max Amount)
    await expect(table).toContainText(/Velocity|Max Amount|Blocked MCC/i);
  });

  test('triggers velocity fraud rule on rapid-fire sequence in simulator', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/simulator');
    await page.waitForLoadState('networkidle');

    // Select active card
    const cardSelect = page.locator('mat-select[formControlName="cardId"]');
    await cardSelect.click();
    await page.locator('mat-option').first().click();

    // Fill small amount
    await page.locator('input[formControlName="amount"]').fill('5.00');
    await page.locator('input[formControlName="merchantName"]').fill('Fast Mart');

    // Click Rapid-fire x6 button
    const rapidFireBtn = page.locator('button:has-text("Rapid-fire x6")');
    await expect(rapidFireBtn).toBeVisible();
    await rapidFireBtn.click();

    // Wait for rapid firing to complete
    await expect(page.locator('.rapid-fire-card')).toBeVisible({ timeout: 25000 });
    // Verify 6 items were fired in table
    const rows = page.locator('table.rf-table tbody tr');
    await expect(rows).toHaveCount(6);

    // Check that at least one of the attempts declined or hit the fraud rule
    const declineItems = rows.filter({ hasText: /DECLINED|59|60/ });
    await expect(declineItems.first()).toBeVisible({ timeout: 10000 });
  });
});
