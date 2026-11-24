import { checkAccessibility, expect, login, reset, test } from "./helpers";

/**
 * Calendar view (KAL-01 to KAL-04) and absences (ABS-01 to ABS-07) against the test data set of
 * docs/test-data.md. Default "now": Mon 16.11.2026 06:00. Lea plays in Team 1 and Team 2, Petra is coach of Team 1
 * and player in Team 2, Sara is on holiday 14.–20.12.2026.
 */

const TEAM_1 = "10000000-0000-4000-8000-000000000001";
const TEAM_2 = "10000000-0000-4000-8000-000000000002";
const TEAM_3 = "10000000-0000-4000-8000-000000000003";
/** Practice Tue 17.11.2026, Team 1 + Team 2. */
const T01 = "40000000-0000-4000-8000-000000000001";
/** Practice Thu 19.11.2026, Team 1. */
const T02 = "40000000-0000-4000-8000-000000000002";
/** Match day Sun 22.11.2026, Team 1. */
const T04 = "40000000-0000-4000-8000-000000000004";
/** Match day Sat 28.11.2026, Team 2. */
const T05 = "40000000-0000-4000-8000-000000000005";

test.beforeEach(async ({ request }) => {
  await reset(request);
});

// ------------------------------------------------------------------ Calendar (KAL)

test("KAL-01: the calendar shows the month with today and the events of the chosen day", async ({
  page,
}) => {
  await login(page, "anna");
  await page.goto("/termine");
  await page.getByTestId("calendar-link").click();
  await expect(page).toHaveURL(/\/termine\/kalender$/);
  await expect(
    page.getByRole("heading", { level: 1, name: "Kalender" }),
  ).toBeVisible();
  await expect(page.getByTestId("calendar-month")).toHaveAttribute(
    "data-month",
    "2026-11",
  );
  await expect(
    page.getByRole("heading", { name: "November 2026" }),
  ).toBeVisible();
  // Today (from the backend clock) is chosen
  await expect(page.getByTestId("calendar-day-2026-11-16")).toHaveAttribute(
    "aria-current",
    "date",
  );
  await expect(page.getByTestId("calendar-day-2026-11-22")).toHaveAttribute(
    "data-count",
    "1",
  );

  await page.getByTestId("calendar-day-2026-11-17").click();
  await expect(page).toHaveURL(/tag=2026-11-17/);
  const agenda = page.getByTestId("calendar-agenda");
  await expect(
    agenda.getByRole("heading", { name: "Di 17.11." }),
  ).toBeVisible();
  const entry = agenda.getByTestId(`calendar-event-${T01}`);
  await expect(entry).toContainText("Training");
  await expect(entry).toContainText("Team 1 + Team 2");
  await expect(entry.getByTestId("calendar-my-response")).toHaveAttribute(
    "data-response",
    "ATTENDING",
  );
  await entry.getByRole("link").click();
  await expect(page).toHaveURL(new RegExp(`/termine/${T01}$`));
});

test("KAL-01: month navigation and deep link", async ({ page }) => {
  await login(page, "anna");
  await page.goto("/termine/kalender?monat=2026-11");
  await page.getByTestId("calendar-next").click();
  await expect(page.getByTestId("calendar-month")).toHaveAttribute(
    "data-month",
    "2026-12",
  );
  await expect(
    page.getByRole("heading", { name: "Dezember 2026" }),
  ).toBeVisible();
  await expect(page).toHaveURL(/monat=2026-12/);
  await page.getByTestId("calendar-previous").click();
  await page.getByTestId("calendar-previous").click();
  await expect(page.getByTestId("calendar-month")).toHaveAttribute(
    "data-month",
    "2026-10",
  );
  await page.getByTestId("calendar-today").click();
  await expect(page.getByTestId("calendar-month")).toHaveAttribute(
    "data-month",
    "2026-11",
  );

  await page.goto("/termine/kalender?tag=2026-12-11");
  await expect(page.getByTestId("calendar-month")).toHaveAttribute(
    "data-month",
    "2026-12",
  );
  await expect(page.getByTestId("calendar-agenda")).toContainText("Teamessen");
  await checkAccessibility(page);
});

test("KAL-03: a player in two teams sees both together and can narrow it down", async ({
  page,
}) => {
  await login(page, "lea");
  await page.goto("/termine/kalender?monat=2026-11");
  const teams = page.getByTestId("calendar-teams");
  await expect(teams.getByRole("checkbox")).toHaveCount(2);
  await expect(teams.getByRole("checkbox", { name: "Team 1" })).toBeChecked();
  await expect(teams.getByRole("checkbox", { name: "Team 2" })).toBeChecked();
  await expect(page.getByTestId("calendar-all-teams")).toHaveCount(0);
  await expect(page.getByTestId("calendar-day-2026-11-22")).toHaveAttribute(
    "data-count",
    "1",
  );
  await expect(page.getByTestId("calendar-day-2026-11-28")).toHaveAttribute(
    "data-count",
    "1",
  );

  await teams.getByRole("checkbox", { name: "Team 1" }).uncheck();
  await expect(page).toHaveURL(new RegExp(`teams=${TEAM_2}`));
  await expect(page.getByTestId("calendar-day-2026-11-22")).toHaveAttribute(
    "data-count",
    "0",
  );
  await expect(page.getByTestId("calendar-day-2026-11-28")).toHaveAttribute(
    "data-count",
    "1",
  );
  // The last team stays chosen
  await expect(teams.getByRole("checkbox", { name: "Team 2" })).toBeDisabled();

  await page.getByTestId("calendar-my-teams").click();
  await expect(teams.getByRole("checkbox", { name: "Team 1" })).toBeChecked();
  await expect(page.getByTestId("calendar-day-2026-11-22")).toHaveAttribute(
    "data-count",
    "1",
  );
  await checkAccessibility(page);
});

test("KAL-04: a coach starts with her team and can show all teams", async ({
  page,
}) => {
  await login(page, "petra");
  await page.goto("/termine/kalender?monat=2026-11");
  const teams = page.getByTestId("calendar-teams");
  await expect(teams.getByRole("checkbox")).toHaveCount(3);
  await expect(teams.getByRole("checkbox", { name: "Team 1" })).toBeChecked();
  await expect(
    teams.getByRole("checkbox", { name: "Team 2" }),
  ).not.toBeChecked();
  await expect(
    teams.getByRole("checkbox", { name: "Team 3" }),
  ).not.toBeChecked();
  await expect(page.getByTestId("calendar-day-2026-11-22")).toHaveAttribute(
    "data-count",
    "1",
  );
  await expect(page.getByTestId("calendar-day-2026-11-28")).toHaveAttribute(
    "data-count",
    "0",
  );
  // Wednesday practices of Team 3 are not shown
  await expect(page.getByTestId("calendar-day-2026-11-18")).toHaveAttribute(
    "data-count",
    "0",
  );

  await page.getByTestId("calendar-all-teams").click();
  await expect(page).toHaveURL(/alle=ja/);
  for (const id of [TEAM_1, TEAM_2, TEAM_3]) {
    await expect(
      page.getByTestId(`calendar-team-${id}`).getByRole("checkbox"),
    ).toBeChecked();
  }
  await expect(page.getByTestId("calendar-day-2026-11-28")).toHaveAttribute(
    "data-count",
    "1",
  );
  await expect(page.getByTestId("calendar-day-2026-11-18")).toHaveAttribute(
    "data-count",
    "1",
  );

  await page.getByTestId("calendar-day-2026-11-28").click();
  const entry = page.getByTestId(`calendar-event-${T05}`);
  await expect(entry).toBeVisible();
  // Petra plays in Team 2: her own response is shown
  await expect(entry.getByTestId("calendar-my-response")).toBeVisible();

  await page.getByTestId("calendar-day-2026-11-18").click();
  // Team 3 practice: Petra is not a member, so no own response
  await expect(
    page.getByTestId("calendar-agenda").getByTestId("calendar-my-response"),
  ).toHaveCount(0);
  await checkAccessibility(page);
});

test.describe("forbidden teams", () => {
  test.use({ expectedConsoleErrors: [/403 \(Forbidden\)/] });

  test("KAL-04: a player cannot open other teams through the address", async ({
    page,
  }) => {
    await login(page, "anna");
    await page.goto(`/termine/kalender?teams=${TEAM_3}`);
    await expect(page.getByRole("alert")).toContainText("Keine Berechtigung");
    await page.goto("/termine/kalender?alle=ja");
    await expect(page.getByRole("alert")).toContainText("Keine Berechtigung");
  });
});

// ------------------------------------------------------------------ Absences (ABS)

test("ABS-01: enter an absence; practices in this time are declined with the reason", async ({
  page,
}) => {
  await login(page, "anna");
  await page.goto("/profil");
  await page.getByTestId("my-absences-link").click();
  await expect(page).toHaveURL(/\/profil\/abwesenheiten$/);
  await expect(
    page.getByRole("heading", { level: 1, name: "Abwesenheiten" }),
  ).toBeVisible();
  await expect(page.getByTestId("absences-empty")).toBeVisible();

  const form = page.getByTestId("absence-form");
  await form.getByLabel("Erster Tag").fill("2026-11-17");
  await form.getByLabel("Letzter Tag").fill("2026-11-20");
  await form.getByRole("radio", { name: "Verletzung" }).check();
  await form.getByLabel("Bemerkung (freiwillig)").fill("Knie");
  await page.getByTestId("absence-save").click();
  await expect(page.getByTestId("absence-feedback")).toHaveText(
    "Eingetragen. 2 Trainings sind abgesagt.",
  );
  const entry = page.getByRole("listitem", {
    name: "Verletzung 17.11.2026 – 20.11.2026",
  });
  await expect(entry).toContainText("Knie");
  await expect(entry.getByTestId("absence-declined")).toHaveText(
    "2 Trainings abgesagt",
  );
  await checkAccessibility(page);

  // Anna had answered «dabei» on 17.11.: now «nicht dabei» with the reason
  await page.goto(`/termine/${T01}`);
  await expect(page.getByTestId("my-response")).toContainText(
    "Abwesend: Verletzung · Knie",
  );
  await page.goto(`/termine/${T02}`);
  await expect(page.getByTestId("my-response")).toContainText(
    "Abwesend: Verletzung · Knie",
  );
  // The match day is not touched
  await page.goto(`/termine/${T04}`);
  await expect(page.getByTestId("my-response")).not.toContainText("Abwesend");

  // The calendar marks the days
  await page.goto("/termine/kalender?tag=2026-11-19");
  await expect(
    page.getByTestId("calendar-day-2026-11-19"),
  ).toHaveAccessibleName("Do 19.11.: 1 Termin, abwesend");
  await expect(page.getByTestId("calendar-absence")).toHaveText(
    "Du bist abwesend: Verletzung · Knie",
  );
});

test.describe("invalid absences", () => {
  test.use({ expectedConsoleErrors: [/status of 400/] });

  test("ABS-02: «Anderes» needs a note, the days must be in order", async ({
    page,
  }) => {
    await login(page, "anna");
    await page.goto("/profil/abwesenheiten");
    const form = page.getByTestId("absence-form");
    await page.getByTestId("absence-save").click();
    await expect(form.getByLabel("Erster Tag")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    await form.getByLabel("Erster Tag").fill("2026-11-25");
    await form.getByLabel("Letzter Tag").fill("2026-11-26");
    await form.getByRole("radio", { name: "Anderes" }).check();
    await page.getByTestId("absence-save").click();
    await expect(form.getByLabel("Bemerkung", { exact: true })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    await expect(form).toContainText(
      "Bei «Anderes» braucht es eine Bemerkung.",
    );

    await form.getByLabel("Bemerkung", { exact: true }).fill("Militär");
    await form.getByLabel("Letzter Tag").fill("2026-11-24");
    await page.getByTestId("absence-save").click();
    await expect(form.getByLabel("Letzter Tag")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    await expect(form).toContainText("Der letzte Tag liegt vor dem ersten.");
  });
});

test("ABS-05: change and delete an absence; the practices follow", async ({
  page,
}) => {
  await login(page, "sara");
  await page.goto("/profil/abwesenheiten");
  const entry = page.getByRole("listitem", {
    name: "Ferien 14.12.2026 – 20.12.2026",
  });
  await expect(entry).toContainText("Skiferien");
  await expect(entry.getByTestId("absence-declined")).toHaveText(
    "2 Trainings abgesagt",
  );

  await entry
    .getByRole("button", { name: "Ferien 14.12.2026 – 20.12.2026 bearbeiten" })
    .click();
  const form = page.getByTestId("absence-form");
  await expect(
    form.getByRole("heading", { name: "Abwesenheit bearbeiten" }),
  ).toBeVisible();
  await form.getByLabel("Letzter Tag").fill("2026-12-16");
  await page.getByTestId("absence-save").click();
  await expect(page.getByTestId("absence-feedback")).toHaveText(
    "Gespeichert. 1 Training ist abgesagt.",
  );
  const shorter = page.getByRole("listitem", {
    name: "Ferien 14.12.2026 – 16.12.2026",
  });
  await expect(shorter.getByTestId("absence-declined")).toHaveText(
    "1 Training abgesagt",
  );

  await shorter
    .getByRole("button", { name: "Ferien 14.12.2026 – 16.12.2026 löschen" })
    .click();
  const dialog = page.getByRole("dialog", { name: "Abwesenheit löschen?" });
  await expect(dialog).toBeVisible();
  await checkAccessibility(page);
  await dialog.getByRole("button", { name: "Löschen" }).click();
  await expect(page.getByTestId("absence-feedback")).toHaveText(
    "Die Abwesenheit ist gelöscht.",
  );
  await expect(page.getByTestId("absences-empty")).toBeVisible();

  await page.goto("/termine/kalender?tag=2026-12-15");
  await expect(page.getByTestId("calendar-absence")).toHaveCount(0);
  await expect(
    page.getByTestId("calendar-agenda").getByTestId("calendar-my-response"),
  ).toHaveAttribute("data-response", "PENDING");
});

test("ABS-03: the coach sees the reason in the overview", async ({ page }) => {
  await login(page, "petra");
  await page.goto("/termine/kalender?tag=2026-12-15");
  await page.getByTestId("calendar-agenda").getByRole("link").first().click();
  await expect(page.getByTestId(`overview-team-${TEAM_1}`)).toContainText(
    "Abwesend: Ferien · Skiferien",
  );
});
