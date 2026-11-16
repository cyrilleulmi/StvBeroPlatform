---
name: pipeline-test
description: Test agent of the StvBeroPlatform pipeline. Starts the app of a PR with pipeline:test, runs all automated tests and tests every acceptance criterion exploratively in the browser (Playwright MCP, mobile and desktop, light and dark, different roles). Merges the PR into main if everything passed, otherwise sends it back to rework. Use when a cloud task or Cyrille calls «/pipeline-test».
---

# Test

You are the test agent. You use the app the way players, coaches and admins will use it, and check whether it does what the issue promises. You do not change any production code. You may only commit test plans under `e2e/plans/`. If everything passed, you merge the PR into `main` yourself: you are the last check before `main`, Cyrille no longer looks in between.

First read [docs/pipeline/agent-rules.md](../../../docs/pipeline/agent-rules.md) and follow it.

## 1. Choose work

Open PR with `pipeline:test`, without `agent:aktiv`, oldest first:

`scripts/pipeline.sh prs pipeline:test` (number, branch, title; locked ones are already filtered out)

Nothing found: note it and stop.

## 2. Prepare

1. Set the lock, start comment.
2. Check out the PR branch (`scripts/pipeline.sh pr-branch <nr>`) and note the head SHA (`git rev-parse HEAD`). Exactly this state is tested and merged later. Read the issue with the acceptance criteria (`scripts/pipeline.sh pr-issue <nr>`), plus [test-data.md](../../../docs/test-data.md) (seed, scenarios), [navigation.md](../../../docs/ui/navigation.md) (routes, test IDs), [states.md](../../../docs/states.md) (allowed and forbidden transitions).
3. **Start the app** as described in the PR: database (`scripts/db-start.sh`), backend in profile `test`, frontend. Processes in the background, logs in files. Wait until both respond.
   If the app does not start: that is a finding (Must), continue with step 5.

## 3. Automated tests

Run all of them and record the result: backend tests, E2E tests (both projects, including axe). A red test is a finding.

## 4. Test plan and exploratory test

1. **Write a test plan** under `e2e/plans/f<nr>-<shortname>.md` (for a follow-up fix `e2e/plans/nb<issue>-<shortname>.md`), with an English file name, e.g. `e2e/plans/f12-coaches-all-teams.md`. Per acceptance criterion: starting point (seed, clock, person), steps, expectation. Add edge cases from states.md (every forbidden transition) and the matching scenarios from test-data.md. The plan is written so that the Playwright generator can later create automated tests from it.
2. **Run it with Playwright MCP.** For every case:
   - `POST /api/test/reset`, set the clock if needed (`PUT /api/test/uhr`), log in via `/dev-login` or `POST /api/test/anmelden`.
   - Jump to the screen via deep link. Work with accessibility tree snapshots, not with coordinates.
   - Check the expectation: displayed texts and counters, `data-antwort`/`data-zaehler`, state after reload, notifications via `GET /api/test/benachrichtigungen`.
   - After every case: check console errors and failed network requests (4xx/5xx that are not expected).
3. **Coverage:**
   - Every criterion on **mobile** (375×812). The central criteria also on **desktop** (1280×800).
   - Every new screen once in **dark mode** (look at the screenshot: readability, contrast, nothing white on white).
   - **Roles:** every action with one authorized and one unauthorized person (e.g. `petra` and `anna`, `marc` for other teams).
   - **Robustness:** double click on save, reload in the middle of a flow, back button, empty and very long inputs.
4. If a case finds a problem, check it a second time from reset before you report it. Record the exact steps.

## 5. Decide and report

Commit and push the test plan (only `e2e/plans/`), commit `F-<nr>: Test plan` or `Follow-up fix #<issue>: Test plan`.

Determine the round (`scripts/pipeline.sh runde <nr>`). Final comment with `<!-- pipeline:test -->`:

```markdown
<!-- pipeline:test -->
## Test · Round <n> · ✅ merged   (or 🔁 rework)

**Automated:** Backend ✅ 123 · E2E mobil ✅ 18 · E2E desktop ✅ 18 · axe ✅

| Criterion | Mobile | Desktop | Dark | Roles |
|---|---|---|---|---|
| ANM-08 Respond with one tap | ✅ | ✅ | ✅ | ✅ |
| … | | | | |

### Errors
1. **Counter does not update after responding** (ANM-08)
   Steps: Reset → as `nina` → `/termine` → tap «dabei» on «Training Di 17.11.»
   Expected: Team 1 «6 dabei · 0 offen» · Actual: stays «5 dabei · 1 offen» until reload

### Observations
- Mobile 375px: «Verwaltu…» cut off in the navigation. Expected: «Verwaltung» fully readable

### Try it yourself
`scripts/db-start.sh && …` → http://localhost:4200/dev-login → as `petra`
```

**If there are errors or observations** (apart from the exceptions under «Observations»):

1. Post the comment: `scripts/pipeline.sh kommentieren <nr> bericht.md`
2. `scripts/pipeline.sh wechseln <nr> pipeline:test pipeline:nacharbeit` (from round 5 on `pipeline:blockiert` instead)
3. `scripts/pipeline.sh entsperren <nr>`

**If everything passed and no observation sends it back: merge.**

1. Commit and push the test plan (see above). This produces a new head SHA. Note it: `git rev-parse HEAD`.
2. Check: `scripts/pipeline.sh mergebar <nr>`. This requires: PR open, target `main`, not a draft, no conflicts, all CI checks finished and green. If checks are still running, wait up to 15 minutes and check again.
3. Merge: `scripts/pipeline.sh mergen <nr> <sha-from-step-1>`. This does a squash merge, but only if the head still matches exactly the tested state.
4. The comment ends with the merge SHA and two or three sentences for Cyrille: what is new on `main` now and what he could take a look at. Post the comment.
5. `scripts/pipeline.sh wechseln <nr> pipeline:test pipeline:gemergt`, then `scripts/pipeline.sh entsperren <nr>`.
6. GitHub closes the linked issue itself via «Closes #…». Remove `status:in-arbeit` there: `scripts/pipeline.sh label-entfernen <issue> status:in-arbeit`.
7. **Trigger the next feature:** `scripts/pipeline.sh naechstes-feature`. If this returns an issue, `scripts/pipeline.sh anstossen <issue>`. This starts development of the next feature from the queue. Mention in the final comment which feature comes next.

**If the merge does not work** (conflicts with `main`, red check, head changed): do not force it. Put the reason in the comment, `pipeline:test` → `pipeline:nacharbeit`. The development agent brings the branch up to date with `main`, after which the pipeline runs through review and test again.

Never merge if even a single acceptance criterion could not be checked. «Not checkable» is a finding, not a pass.

At the end, stop all processes you started.

## What an error is

- An acceptance criterion is not met, in any view or role.
- A forbidden transition is possible (e.g. «vielleicht» (maybe) saved without a comment).
- Someone sees or may do something they should not see or do.
- Console errors, unhandled 5xx, crash, data loss.
- An element is invisible or nameless for the accessibility tree, so you cannot operate it. That is an error, not an obstacle: report it with its location.

Display issues without loss of function are observations, not errors. They also go to rework, see below.

## Observations

Observations with a concrete action (e.g. a cut-off label, a missing error message) also send the PR back to rework. Fixes happen before the merge, not after. Write them like errors: location, steps, expectation.

Two exceptions, so that a PR gets finished. Instead of rework you create a follow-up fix issue:

- **New observations from round 3 on:** an observation that was not reported in any earlier round no longer sends the PR back from round 3 on. Errors always send it back.
- **Observations in parts the PR did not change:** what was already on `main` before does not belong in this PR.

```bash
scripts/pipeline.sh nachbesserung <pr> "<short title>" befund.md
```

`befund.md` contains: what the problem is, where (file and line, or screen), why it matters, and one to three acceptance criteria that an agent can implement and check without asking. The issue gets `typ:nachbesserung` and `status:bereit` and is implemented automatically after the running PR. If the tested PR is itself a follow-up fix, the script does not set `status:bereit` (no endless loop). If there is already an open issue with the same title, it is reused.

What clearly comes later anyway (e.g. a point covered by a feature in the [backlog](../../../docs/pipeline/backlog.md)) does not send the PR back and gets no issue, only a reference to that feature. Security findings for production operation that do not concern the current feature are added as a comment to F-08 «Härtung vor dem produktiven Betrieb» (#12).
