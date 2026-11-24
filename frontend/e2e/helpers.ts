import { expect, Page } from '@playwright/test';

export const ADMIN_CREDENTIALS = {
  email: 'admin@minicard.local',
  password: process.env['ADMIN_PASSWORD'] || 'TestAdminPassword123!',
};

export async function loginAsAdmin(page: Page): Promise<void> {
  await login(page, ADMIN_CREDENTIALS.email, ADMIN_CREDENTIALS.password);
}

export async function login(page: Page, email: string, pass: string): Promise<void> {
  await page.goto('/login');
  await page.waitForLoadState('networkidle');

  // Fill credentials
  await page.locator('input[formControlName="email"]').fill(email);
  await page.locator('input[formControlName="password"]').fill(pass);

  // Submit
  await page.locator('button[type="submit"]').click();

  // If initial attempt fails with invalid credentials (e.g. dev password vs test password), try the alternative
  try {
    await expect(page).toHaveURL(/\/(dashboard|cards|transactions)/, { timeout: 3000 });
  } catch {
    if (email === ADMIN_CREDENTIALS.email) {
      const altPassword = pass === 'TestAdminPassword123!' ? 'AdminPassword123!' : 'TestAdminPassword123!';
      await page.locator('input[formControlName="password"]').fill(altPassword);
      await page.locator('button[type="submit"]').click();
      await expect(page).toHaveURL(/\/(dashboard|cards|transactions)/, { timeout: 8000 });
    } else {
      throw new Error(`Failed to login with user ${email}`);
    }
  }
  await page.waitForLoadState('networkidle');
}

export async function logout(page: Page): Promise<void> {
  // Click user profile/menu button
  const userMenuBtn = page.locator('button.user-menu-btn, button[aria-label="User menu"]');
  if (await userMenuBtn.isVisible()) {
    await userMenuBtn.click();
    await page.locator('button[mat-menu-item]:has-text("Log out")').click();
  }
  await expect(page).toHaveURL(/\/login/);
}
