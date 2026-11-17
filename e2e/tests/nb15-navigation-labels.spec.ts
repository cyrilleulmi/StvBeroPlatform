import { expect, login, reset, test } from './helpers';

/**
 * Follow-up fix #15 · On mobile all labels of the main navigation are fully readable,
 * also with five entries for admins. Touch targets stay at least 44px
 * (docs/testability.md, docs/design-principles.md).
 */

const ADMIN_ENTRIES = ['Start', 'Termine', 'Teams', 'Profil', 'Verwaltung'];

test.beforeEach(async ({ request }) => {
  await reset(request);
});

for (const [key, entries] of [
  ['alex', ADMIN_ENTRIES],
  ['anna', ADMIN_ENTRIES.slice(0, 4)],
] as const) {
  test(`Follow-up fix #15 K1/K2: Navigation for ${key} fully labelled, touch targets from 44px`, async ({ page }) => {
    await login(page, key);
    await page.goto('/');
    const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
    const links = nav.getByRole('list').getByRole('link');
    await expect(links).toHaveText([...entries]);

    for (const text of entries) {
      const link = nav.getByRole('link', { name: text });
      const label = link.getByText(text, { exact: true });
      await expect(label).toBeVisible();

      // K1: text not cut off (no overflow, no ellipsis) and fully inside the entry.
      const measures = await label.evaluate((el) => {
        const s = el.getBoundingClientRect();
        const l = el.closest('a')!.getBoundingClientRect();
        return {
          overflow: el.scrollWidth - el.clientWidth,
          left: s.left - l.left,
          right: l.right - s.right,
          rightInWindow: window.innerWidth - s.right,
        };
      });
      expect(measures.overflow, `${text}: text overflows`).toBeLessThanOrEqual(0);
      expect(measures.left, `${text}: sticks out on the left`).toBeGreaterThanOrEqual(0);
      expect(measures.right, `${text}: sticks out on the right`).toBeGreaterThanOrEqual(0);
      expect(measures.rightInWindow, `${text}: outside the screen`).toBeGreaterThanOrEqual(0);

      // K2: touch target at least 44 × 44px.
      const box = (await link.boundingBox())!;
      expect(box.width, `${text}: width`).toBeGreaterThanOrEqual(44);
      expect(box.height, `${text}: height`).toBeGreaterThanOrEqual(44);
    }
  });
}
