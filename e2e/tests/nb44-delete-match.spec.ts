import type { Page } from "@playwright/test";
import { checkAccessibility, expect, login, reset, test } from "./helpers";

/**
 * Follow-up fix #44 · Delete matches no longer delivered by swiss unihockey in the UI (SUH-06,
 * docs/states.md "Match from swiss unihockey": no longer present → coach deletes it).
 * After the reset the simulator delivers match 01 and 02 of event 04 for team ID 900001; here only match 01.
 */

const TEAM_1 = "10000000-0000-4000-8000-000000000001";
const T04 = "40000000-0000-4000-8000-000000000004";

const MATCH_01_SU = {
  suMatchId: 5000001,
  startTime: "2026-11-22T09:30:00+01:00",
  homeTeam: "Team 1",
  awayTeam: "UHC Musterdorf",
  hall: "Sporthalle Seefeld",
  location: "Musterdorf",
};

/** Matches in the list «Spiele» of the event detail page, as accessible names. */
async function matches(page: Page): Promise<(string | null)[]> {
  return page
    .getByTestId("matches")
    .getByRole("listitem")
    .evaluateAll((li) => li.map((e) => e.getAttribute("aria-label")));
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test("NB-44 K1 SUH-06: A match no longer delivered can be removed in the form", { tag: '@desktop' }, async ({
  page,
}) => {
  const sim = await page.request.put("/api/test/swiss-unihockey/matches", {
    data: { suTeamId: 900001, matches: [MATCH_01_SU] },
  });
  expect(sim.status()).toBe(204);
  await login(page, "alex");
  const sync = await page.request.post(
    `/api/teams/${TEAM_1}/swiss-unihockey/sync`,
  );
  expect(sync.ok()).toBe(true);

  await login(page, "petra");
  await page.goto(`/termine/${T04}/bearbeiten`);
  const match1 = page.getByRole("group", { name: "Spiel 1" });
  const match2 = page.getByRole("group", { name: "Spiel 2" });
  await expect(match2).toContainText(
    "swiss unihockey liefert dieses Spiel nicht mehr.",
  );
  await expect(match2).not.toContainText("nicht änderbar");
  await expect(match1).toContainText("Von swiss unihockey, nicht änderbar.");
  await expect(
    page.getByRole("button", { name: "Spiel 1 entfernen" }),
  ).toHaveCount(0);
  await checkAccessibility(page);

  await page.getByRole("button", { name: "Spiel 2 entfernen" }).click();
  await expect(page.getByRole("group", { name: "Spiel 2" })).toHaveCount(0);
  await page.getByRole("button", { name: "Änderungen speichern" }).click();
  await expect(page).toHaveURL(`/termine/${T04}`);
  await expect(page.getByRole("status")).toHaveText("Änderungen gespeichert.");
  await expect
    .poll(() => matches(page))
    .toEqual(["09:30 Team 1 – UHC Musterdorf"]);

  await page.reload();
  await expect
    .poll(() => matches(page))
    .toEqual(["09:30 Team 1 – UHC Musterdorf"]);
  const event = await page.request.get(`/api/events/${T04}`);
  const data = (await event.json()) as { matches: { suMatchId: number }[] };
  expect(data.matches.map((s) => s.suMatchId)).toEqual([5000001]);
});

test("NB-44 K2 SUH-06: Imported matches that swiss unihockey still delivers have no remove button", async ({
  page,
}) => {
  await login(page, "petra");
  await page.goto(`/termine/${T04}/bearbeiten`);
  for (const n of [1, 2]) {
    const match = page.getByRole("group", { name: `Spiel ${n}` });
    await expect(match).toContainText("Von swiss unihockey, nicht änderbar.");
    await expect(match).not.toContainText("nicht mehr");
    await expect(
      page.getByRole("button", { name: `Spiel ${n} entfernen` }),
    ).toHaveCount(0);
  }
});
