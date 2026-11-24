import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

test.describe('Responsive Layout & Viewport Verification', () => {
  const viewports = [
    { name: 'Desktop (1280x800)', width: 1280, height: 800 },
    { name: 'Tablet (768x1024)', width: 768, height: 1024 },
  ];

  for (const vp of viewports) {
    test(`renders cleanly without horizontal overflow at ${vp.name}`, async ({ page }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await loginAsAdmin(page);

      const routes = ['/dashboard', '/cards', '/transactions', '/simulator', '/reports'];

      for (const route of routes) {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        // Verify documentElement does not have horizontal overflow
        const isOverflowing = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth;
        });

        expect(isOverflowing, `Route ${route} should not have horizontal overflow at ${vp.name}`).toBe(false);
      }
    });
  }
});
