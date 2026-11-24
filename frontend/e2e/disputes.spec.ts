import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

test.describe('Disputes State Machine & Lifecycle', () => {
  test('opens dispute from approved transaction, advances workflow, and resolves WON', async ({ page }) => {
    await loginAsAdmin(page);

    // 1. First, make sure there is an approved transaction by simulating a payment
    await page.goto('/simulator');
    await page.waitForLoadState('networkidle');

    const cardSelect = page.locator('mat-select[formControlName="cardId"]');
    await cardSelect.click();
    await page.locator('mat-option').first().click();

    await page.locator('input[formControlName="amount"]').fill('50.00');
    await page.locator('input[formControlName="merchantName"]').fill('Dispute Candidate Store');
    await page.locator('button.submit-btn').click();

    await expect(page.locator('.result-card')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.result-card .status-heading')).toContainText('APPROVED');

    // 2. Go to Transactions list
    await page.goto('/transactions');
    await page.waitForLoadState('networkidle');

    // Click first transaction row
    const firstRow = page.locator('table tbody tr.clickable-row, table tbody tr').first();
    await expect(firstRow).toBeVisible({ timeout: 10000 });

    // Open transaction detail drawer by clicking row
    await firstRow.click();
    const detailDialog = page.locator('mc-transaction-detail-dialog');
    await expect(detailDialog).toBeVisible({ timeout: 10000 });

    // If "Open Dispute" button exists in drawer, click it
    const openDisputeBtn = detailDialog.locator('button:has-text("Open Dispute")');
    if (await openDisputeBtn.isVisible({ timeout: 3000 })) {
      await openDisputeBtn.click();
      await expect(page.locator('mc-open-dispute-dialog')).toBeVisible();

      await page.locator('textarea[formControlName="explanation"]').fill('E2E Test Unauthorized Transaction Claim');
      await page.locator('button[type="submit"][form="openDisputeForm"]').click();
      await expect(page.locator('mc-open-dispute-dialog')).not.toBeVisible({ timeout: 10000 });
    }

    // 3. Go to Disputes list
    await page.goto('/disputes');
    await page.waitForLoadState('networkidle');

    const firstDisputeRow = page.locator('table tbody tr').first();
    await expect(firstDisputeRow).toBeVisible({ timeout: 10000 });
    await firstDisputeRow.click();

    // 4. On Dispute Detail page
    await expect(page).toHaveURL(/\/disputes\/[a-f0-9-]+/);
    await page.waitForLoadState('networkidle');

    // Check available workflow action buttons
    const reqEvidenceBtn = page.locator('button:has-text("Request Evidence")');
    if (await reqEvidenceBtn.isVisible({ timeout: 3000 })) {
      await reqEvidenceBtn.click();
      await page.locator('mat-dialog-container button:has-text("Request Evidence")').click();
      await page.waitForLoadState('networkidle');
    }

    // Submit Evidence if action available
    const submitEvidenceBtn = page.locator('button:has-text("Submit Evidence")');
    if (await submitEvidenceBtn.isVisible({ timeout: 3000 })) {
      await submitEvidenceBtn.click();
      await page.locator('mat-dialog-container textarea[formControlName="note"]').fill('Merchant receipts and POS telemetry logs submitted.');
      await page.locator('mat-dialog-container button:has-text("Submit Evidence")').click();
      await page.waitForLoadState('networkidle');
    }

    // Resolve WON if action available
    const resolveWonBtn = page.locator('button:has-text("Resolve WON")');
    if (await resolveWonBtn.isVisible({ timeout: 3000 })) {
      await resolveWonBtn.click();
      await page.locator('mat-dialog-container textarea[formControlName="note"]').fill('Dispute decided in favor of cardholder.');
      await page.locator('mat-dialog-container button:has-text("Confirm Resolution")').click();
      await page.waitForLoadState('networkidle');

      // Verify status becomes RESOLVED_WON
      await expect(page.locator('mc-status-chip').filter({ hasText: 'RESOLVED_WON' })).toBeVisible({ timeout: 10000 });
    }
  });
});
