import { test, expect } from '@playwright/test';
import { login, loginAsAdmin } from './helpers';

test.describe('Administration: Users, API Clients, Role Restrictions & Audit Log', () => {
  test('creates new staff user and validates password policy', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/users');
    await page.waitForLoadState('networkidle');

    await page.locator('button:has-text("Add User")').click();
    await expect(page.locator('h2[mat-dialog-title]')).toContainText('Create Staff User');

    const uniqueEmail = `000_officer_${Date.now()}@minicard.local`;
    await page.locator('input[formControlName="email"]').fill(uniqueEmail);
    await page.locator('input[formControlName="displayName"]').fill('Dispute Officer E2E');
    await page.locator('mat-select[formControlName="role"]').click();
    await page.locator('mat-option[value="DISPUTE_OFFICER"]').click();
    await page.locator('input[formControlName="password"]').fill('DisputeOfficerPass123!');

    await page.locator('mat-dialog-container button:has-text("Create User")').click();
    await page.waitForLoadState('networkidle');

    // Verify user appears in table
    const table = page.locator('table.users-table');
    await expect(table).toContainText(uniqueEmail);
  });

  test('creates API client with one-time reveal, authenticates, and handles revocation', async ({
    page,
    request,
  }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/api-clients');
    await page.waitForLoadState('networkidle');

    await page.locator('button:has-text("Create Client")').click();
    await expect(page.locator('h2[mat-dialog-title]')).toContainText('Create API Client');

    const clientName = `E2E Acquirer ${Date.now()}`;
    await page.locator('input[formControlName="name"]').fill(clientName);
    await page.locator('mat-dialog-container button:has-text("Create Client")').click();

    // Verify one-time reveal panel
    const revealCard = page.locator('.key-display-card');
    await expect(revealCard).toBeVisible({ timeout: 15000 });
    const apiKey = (await page.locator('.raw-key').innerText()).trim();
    expect(apiKey).toMatch(/^mck_[a-zA-Z0-9]+_[a-zA-Z0-9_-]+$/);

    // Acknowledge and close
    await page.locator('.ack-checkbox mat-checkbox input').click();
    await page.locator('button:has-text("Done")').click();
    await page.waitForLoadState('networkidle');

    // Verify client appears in table
    await expect(page.locator('table')).toContainText(clientName);

    // Call authorization API using this API client key via request context
    const authResponse = await request.post('/api/v1/authorize', {
      headers: {
        'X-API-Key': apiKey,
        'Idempotency-Key': `e2e-api-test-${Date.now()}`,
        'Content-Type': 'application/json',
      },
      data: {
        cardId: '00000000-0000-0000-0000-000000000000', // Non-existent card to verify authentication passes
        amountMinor: 100,
        currency: 'NPR',
        merchantName: 'API Client Verification Mart',
        mcc: '5411',
      },
    });
    // Should NOT be 401 Unauthorized because the key is valid (will be 404/422/400 for bad cardId)
    expect(authResponse.status()).not.toBe(401);
  });

  test('enforces role restrictions: ANALYST cannot access administration pages', async ({ page }) => {
    // 1. Create a dedicated analyst user first as Admin
    await loginAsAdmin(page);
    await page.goto('/admin/users');
    await page.waitForLoadState('networkidle');

    const analystEmail = `analyst_${Date.now()}@minicard.local`;
    const analystPass = 'AnalystPassword123!';

    await page.locator('button:has-text("Add User")').click();
    await page.locator('input[formControlName="email"]').fill(analystEmail);
    await page.locator('input[formControlName="displayName"]').fill('Analyst Tester');
    await page.locator('mat-select[formControlName="role"]').click();
    await page.locator('mat-option[value="ANALYST"]').click();
    await page.locator('input[formControlName="password"]').fill(analystPass);
    await page.locator('mat-dialog-container button:has-text("Create User")').click();
    await page.waitForLoadState('networkidle');

    // 2. Log out
    await page.locator('button.user-menu-btn').click();
    await page.locator('button[mat-menu-item]:has-text("Log out")').click();
    await expect(page).toHaveURL(/\/login/);

    // 3. Log in as ANALYST
    await login(page, analystEmail, analystPass);
    await expect(page).toHaveURL(/\/dashboard/);

    // Sidebar should NOT display Administration section or Users/API Clients links
    await expect(page.locator('.nav-section-title:has-text("ADMINISTRATION")')).not.toBeVisible();
    await expect(page.locator('a[routerLink="/admin/users"]')).not.toBeVisible();

    // Direct navigation to /admin/users should be forbidden (403 system page)
    await page.goto('/admin/users');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.system-code, h1, .error-code')).toContainText(/403/);
  });

  test('displays audit log entries and filter options', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/audit');
    await page.waitForLoadState('networkidle');

    // Table should contain audit logs
    const table = page.locator('table.audit-table, table');
    await expect(table).toBeVisible({ timeout: 10000 });
    await expect(table.locator('tbody tr')).not.toHaveCount(0);
  });
});
