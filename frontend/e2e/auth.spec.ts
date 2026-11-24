import { test, expect } from '@playwright/test';
import { ADMIN_CREDENTIALS, login, loginAsAdmin, logout } from './helpers';

test.describe('Authentication & Session Management', () => {
  test('displays error message on invalid login credentials', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    await page.locator('input[formControlName="email"]').fill('nonexistent@example.com');
    await page.locator('input[formControlName="password"]').fill('WrongPassword123!');
    await page.locator('button[type="submit"]').click();

    const errorAlert = page.locator('.alert-danger');
    await expect(errorAlert).toBeVisible();
    await expect(errorAlert).toContainText(/incorrect|invalid|credentials|failed/i);
    await expect(page).toHaveURL(/\/login/);
  });

  test('logs in successfully and redirects to dashboard', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('.brand-name')).toContainText('ATM Card Processor');
  });

  test('restores session upon browser reload', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page).toHaveURL(/\/dashboard/);

    // Reload the page
    await page.reload();
    await page.waitForLoadState('networkidle');

    // User should remain logged in on /dashboard
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('.user-name')).toBeVisible();
  });

  test('logs out and redirects back to login page', async ({ page }) => {
    await loginAsAdmin(page);
    await logout(page);
    await expect(page).toHaveURL(/\/login/);

    // Attempting to visit /dashboard should redirect back to /login
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL(/\/login/);
  });
});
