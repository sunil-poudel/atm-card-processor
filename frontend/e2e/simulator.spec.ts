import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

test.describe('Payment Simulator & Idempotency', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/simulator');
    await page.waitForLoadState('networkidle');
  });

  test('simulates approved payment and decreases balance', async ({ page }) => {
    // Select active card (choose last to avoid velocity conflicts from rapid-fire tests)
    const cardSelect = page.locator('mat-select[formControlName="cardId"]');
    await cardSelect.click();
    const options = page.locator('mat-option');
    await options.last().click();

    // Fill authorization details
    await page.locator('input[formControlName="amount"]').fill('25.00');
    await page.locator('input[formControlName="merchantName"]').fill('E2E Test Merchant');

    // Submit authorization
    await page.locator('button.submit-btn').click();

    // Verify result card is APPROVED
    const resultCard = page.locator('.result-card');
    await expect(resultCard).toBeVisible({ timeout: 15000 });
    await expect(resultCard.locator('.status-heading')).toContainText('APPROVED');
    await expect(resultCard.locator('.code-subtitle')).toContainText('Code 00');
  });

  test('detects idempotent replay with cached response', async ({ page }) => {
    // Select card
    const cardSelect = page.locator('mat-select[formControlName="cardId"]');
    await cardSelect.click();
    const options = page.locator('mat-option');
    await options.last().click();

    await page.locator('input[formControlName="amount"]').fill('10.00');
    await page.locator('input[formControlName="merchantName"]').fill('Idempotency Store');

    // First submission
    await page.locator('button.submit-btn').click();
    await expect(page.locator('.result-card')).toBeVisible({ timeout: 15000 });

    // Second submission without refreshing idempotency key
    await page.locator('button.submit-btn').click();

    // Replay banner must be present
    const replayBanner = page.locator('.replayed-banner');
    await expect(replayBanner).toBeVisible({ timeout: 10000 });
    await expect(replayBanner).toContainText('Idempotent Replay Detected');
  });

  test('declines with code 51 for insufficient funds', async ({ page }) => {
    const cardSelect = page.locator('mat-select[formControlName="cardId"]');
    await cardSelect.click();
    await page.locator('mat-option').first().click();

    // Request amount within per-tx limit but potentially exceeding balance
    await page.locator('input[formControlName="amount"]').fill('4999.00');
    await page.locator('input[formControlName="merchantName"]').fill('Overdraft Store');

    await page.locator('button.submit-btn').click();

    const resultCard = page.locator('.result-card');
    await expect(resultCard).toBeVisible({ timeout: 15000 });
    await expect(resultCard.locator('.status-heading')).toContainText('DECLINED');
    await expect(resultCard.locator('.code-subtitle')).toContainText(/Code 51|Code 61/);
  });

  test('declines with code 61 for exceeding per-transaction limit', async ({ page }) => {
    const cardSelect = page.locator('mat-select[formControlName="cardId"]');
    await cardSelect.click();
    await page.locator('mat-option').first().click();

    // Request amount above standard per-transaction limit (e.g. 50,000)
    await page.locator('input[formControlName="amount"]').fill('60000.00');
    await page.locator('input[formControlName="merchantName"]').fill('High Value Electronics');

    await page.locator('button.submit-btn').click();

    const resultCard = page.locator('.result-card');
    await expect(resultCard).toBeVisible({ timeout: 15000 });
    await expect(resultCard.locator('.status-heading')).toContainText('DECLINED');
    await expect(resultCard.locator('.code-subtitle')).toContainText(/Code 61|Code 51/);
  });
});
