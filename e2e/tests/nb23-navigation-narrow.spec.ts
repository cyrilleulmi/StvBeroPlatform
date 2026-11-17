import { expect, login, reset, test } from './helpers';

/**
 * Follow-up fix #23 · The labels of the mobile main navigation are fully readable for admins (five entries)
 * at 375px and also at 320px, in light and dark, without horizontal scrolling
 * (docs/design-principles.md: mobile first, readable labels).
 */

const ADMIN_ENTRIES = ['Start', 'Termine', 'Teams', 'Profil', 'Verwaltung'];

test.beforeEach(async ({ request }) => {
  await reset(request);
});

for (const width of [375, 320]) {
  for (const scheme of ['light', 'dark'] as const) {
    test(`Follow-up fix #23 K1/K2/K3: Navigation for alex at ${width}px (${scheme}) fully readable`, async ({ page }, testInfo) => {
      test.skip(testInfo.project.name !== 'mobile', 'The bottom navigation exists only on mobile');
      await page.setViewportSize({ width: width, height: 812 });
      await page.emulateMedia({ colorScheme: scheme });
      await login(page, 'alex');
      await page.goto('/verwaltung');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Verwaltung');

      const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
      await expect(nav.getByRole('list').getByRole('link')).toHaveText(ADMIN_ENTRIES);

      for (const text of ADMIN_ENTRIES) {
        const link = nav.getByRole('link', { name: text });
        const label = link.getByText(text, { exact: true });
        await expect(label).toBeVisible();
        const measures = await label.evaluate((el) => {
          const s = el.getBoundingClientRect();
          const a = el.closest('a')!;
          const l = a.getBoundingClientRect();
          const style = getComputedStyle(a);
          // The text must lie in the content area of the entry, not in the padding up to the edge of the highlight.
          return {
            overflow: el.scrollWidth - el.clientWidth,
            left: s.left - (l.left + parseFloat(style.paddingLeft)),
            right: l.right - parseFloat(style.paddingRight) - s.right,
            leftInWindow: s.left,
            rightInWindow: window.innerWidth - s.right,
          };
        });
        expect(measures.overflow, `${text}: text overflows`).toBeLessThanOrEqual(0);
        expect(measures.left, `${text}: sticks out of the entry on the left`).toBeGreaterThanOrEqual(0);
        expect(measures.right, `${text}: sticks out of the entry on the right`).toBeGreaterThanOrEqual(0);
        expect(measures.leftInWindow, `${text}: outside the screen on the left`).toBeGreaterThanOrEqual(0);
        expect(measures.rightInWindow, `${text}: outside the screen on the right`).toBeGreaterThanOrEqual(0);

        const box = (await link.boundingBox())!;
        expect(box.width, `${text}: touch target width`).toBeGreaterThanOrEqual(44);
        expect(box.height, `${text}: touch target height`).toBeGreaterThanOrEqual(44);
      }

      // K2: no horizontal scrolling, neither on the page nor in the navigation.
      const scroll = await nav.evaluate((el) => ({
        page: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        navigation: el.scrollWidth - el.clientWidth,
      }));
      expect(scroll.page, 'Page scrolls horizontally').toBeLessThanOrEqual(0);
      expect(scroll.navigation, 'Navigation scrolls horizontally').toBeLessThanOrEqual(0);
    });
  }
}
