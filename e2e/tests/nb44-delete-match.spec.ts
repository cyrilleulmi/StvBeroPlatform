import type { Page } from "@playwright/test";
import { anmelden, barrierefrei, expect, reset, test } from "./helpers";

/**
 * Nachbesserung #44 · Nicht mehr gelieferte Spiele aus swiss unihockey in der Oberfläche löschen (SUH-06,
 * docs/zustaende.md «Spiel aus swiss unihockey»: NichtMehrVorhanden → Coach löscht es).
 * Nach dem Reset liefert der Simulator für Team-ID 900001 Spiel 01 und 02 von Termin 04; hier nur noch Spiel 01.
 */

const TEAM_1 = "10000000-0000-4000-8000-000000000001";
const T04 = "40000000-0000-4000-8000-000000000004";

const SPIEL_01_SU = {
  suMatchId: 5000001,
  startTime: "2026-11-22T09:30:00+01:00",
  homeTeam: "Team 1",
  awayTeam: "UHC Musterdorf",
  hall: "Sporthalle Seefeld",
  location: "Musterdorf",
};

/** Spiele in der Liste «Spiele» der Termin-Detailseite, als zugängliche Namen. */
async function spiele(page: Page): Promise<(string | null)[]> {
  return page
    .getByTestId("spiele")
    .getByRole("listitem")
    .evaluateAll((li) => li.map((e) => e.getAttribute("aria-label")));
}

test.beforeEach(async ({ request }) => {
  await reset(request);
});

test("NB-44 K1 SUH-06: Nicht mehr geliefertes Spiel lässt sich im Formular entfernen", async ({
  page,
}) => {
  const sim = await page.request.put("/api/test/swiss-unihockey/matches", {
    data: { suTeamId: 900001, matches: [SPIEL_01_SU] },
  });
  expect(sim.status()).toBe(204);
  await anmelden(page, "alex");
  const abgleich = await page.request.post(
    `/api/teams/${TEAM_1}/swiss-unihockey/sync`,
  );
  expect(abgleich.ok()).toBe(true);

  await anmelden(page, "petra");
  await page.goto(`/termine/${T04}/bearbeiten`);
  const spiel1 = page.getByRole("group", { name: "Spiel 1" });
  const spiel2 = page.getByRole("group", { name: "Spiel 2" });
  await expect(spiel2).toContainText(
    "swiss unihockey liefert dieses Spiel nicht mehr.",
  );
  await expect(spiel2).not.toContainText("nicht änderbar");
  await expect(spiel1).toContainText("Von swiss unihockey, nicht änderbar.");
  await expect(
    page.getByRole("button", { name: "Spiel 1 entfernen" }),
  ).toHaveCount(0);
  await barrierefrei(page);

  await page.getByRole("button", { name: "Spiel 2 entfernen" }).click();
  await expect(page.getByRole("group", { name: "Spiel 2" })).toHaveCount(0);
  await page.getByRole("button", { name: "Änderungen speichern" }).click();
  await expect(page).toHaveURL(`/termine/${T04}`);
  await expect(page.getByRole("status")).toHaveText("Änderungen gespeichert.");
  await expect
    .poll(() => spiele(page))
    .toEqual(["09:30 Team 1 – UHC Musterdorf"]);

  await page.reload();
  await expect
    .poll(() => spiele(page))
    .toEqual(["09:30 Team 1 – UHC Musterdorf"]);
  const termin = await page.request.get(`/api/events/${T04}`);
  const daten = (await termin.json()) as { matches: { suMatchId: number }[] };
  expect(daten.matches.map((s) => s.suMatchId)).toEqual([5000001]);
});

test("NB-44 K2 SUH-06: Importierte Spiele, die swiss unihockey noch liefert, haben keinen Knopf zum Entfernen", async ({
  page,
}) => {
  await anmelden(page, "petra");
  await page.goto(`/termine/${T04}/bearbeiten`);
  for (const n of [1, 2]) {
    const spiel = page.getByRole("group", { name: `Spiel ${n}` });
    await expect(spiel).toContainText("Von swiss unihockey, nicht änderbar.");
    await expect(spiel).not.toContainText("nicht mehr");
    await expect(
      page.getByRole("button", { name: `Spiel ${n} entfernen` }),
    ).toHaveCount(0);
  }
});
