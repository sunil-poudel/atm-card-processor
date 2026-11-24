import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

test.describe('Cards Lifecycle & Ledger Entries', () => {
  test('issues card, shows one-time PAN reveal, activates, tops up, and views ledger', async ({ page }) => {
    await loginAsAdmin(page);

    // Navigate to Cards list
    await page.goto('/cards');
    await page.waitForLoadState('networkidle');

    // Click "Issue Card" button
    const issueBtn = page.locator('button:has-text("Issue Card")');
    await expect(issueBtn).toBeVisible();
    await issueBtn.click();

    // Dialog should open
    await expect(page.locator('mc-card-issue-dialog')).toBeVisible();

    const uniqueHolderName = 'Alice Smith';
    await page.locator('input[formControlName="holderName"]').fill(uniqueHolderName);
    await page.locator('input[formControlName="perTransactionLimit"]').fill('5000.00');
    await page.locator('input[formControlName="dailyLimit"]').fill('20000.00');
    await page.locator('input[formControlName="initialBalance"]').fill('1000.00');

    // Submit issue form
    await page.locator('button[type="submit"][form="issueCardForm"]').click();

    // One-time PAN reveal step
    const revealDialog = page.locator('.reveal-title');
    await expect(revealDialog).toBeVisible({ timeout: 15000 });
    await expect(revealDialog).toContainText('Card Issued Successfully');

    const panDigits = page.locator('.pan-digits');
    await expect(panDigits).toBeVisible();
    const panText = await panDigits.innerText();
    expect(panText.trim().length).toBeGreaterThanOrEqual(16);

    // Copy button exists
    const copyBtn = page.locator('.pan-reveal-box mc-copy-button');
    await expect(copyBtn).toBeVisible();

    // Acknowledge checkbox and proceed
    await page.locator('.ack-container mat-checkbox input').click();
    await page.locator('button:has-text("Proceed to Card Details")').click();

    // Now on card details page
    await expect(page).toHaveURL(/\/cards\/[a-f0-9-]+/);
    await page.waitForLoadState('networkidle');

    // Verify card is in INACTIVE state
    await expect(page.locator('mc-status-chip').filter({ hasText: 'INACTIVE' })).toBeVisible();

    // Activate the card
    const activateBtn = page.locator('button:has-text("Activate")');
    await expect(activateBtn).toBeVisible();
    await activateBtn.click();

    // Confirm in dialog
    const confirmActivateBtn = page.locator('mat-dialog-container button:has-text("Activate Card")');
    await expect(confirmActivateBtn).toBeVisible();
    await confirmActivateBtn.click();

    // Wait for card status to become ACTIVE
    await expect(page.locator('mc-status-chip').filter({ hasText: 'ACTIVE' })).toBeVisible({ timeout: 10000 });

    // Top up the card
    const topUpBtn = page.locator('button:has-text("Top Up")');
    await expect(topUpBtn).toBeVisible();
    await topUpBtn.click();

    // Fill top up dialog
    const topUpAmountInput = page.locator('mat-dialog-container input[formControlName="amount"]');
    await expect(topUpAmountInput).toBeVisible();
    await topUpAmountInput.fill('500.00');
    await page.locator('mat-dialog-container button:has-text("Confirm Top Up")').click();

    // Switch to Ledger tab
    const ledgerTab = page.locator('.mat-mdc-tab').filter({ hasText: 'Ledger' });
    await ledgerTab.click();
    await page.waitForLoadState('networkidle');

    // Verify ledger table has rows
    const ledgerTable = page.locator('.ledger-table');
    await expect(ledgerTable).toBeVisible({ timeout: 10000 });
    await expect(ledgerTable.locator('tbody tr')).not.toHaveCount(0);
  });
});
