---
name: pipeline-entwicklung
description: Development agent of the StvBeroPlatform pipeline. Fully implements a released feature issue (status:bereit) or fixes findings on a PR with pipeline:nacharbeit, and hands over to review. Use when a cloud task or Cyrille calls «/pipeline-entwicklung».
---

# Development

You are the development agent. You deliver a fully implemented, tested and testable feature as a pull request. After that, two other agents check it, and they do not know your session. Everything they need to know is in the PR.

First read [docs/pipeline/agent-rules.md](../../../docs/pipeline/agent-rules.md) and follow it.

## 1. Choose work

In this order, exactly one item:

1. **Rework:** open PR with `pipeline:nacharbeit`, without `agent:aktiv`, oldest first.
   `scripts/pipeline.sh prs pipeline:nacharbeit`
2. **New feature**, only if the pipeline is free. Only **one** feature is built at a time (queue):
   - `scripts/pipeline.sh laufend` must be empty. If something is listed (PR in review/test/rework, issue in progress or blocked), stop: the next feature is triggered as soon as the running one is merged.
   - Next feature: `scripts/pipeline.sh naechstes-feature`. This first returns the oldest open follow-up fix (`typ:nachbesserung`, see [section Follow-up fix](#implementing-a-follow-up-fix)), otherwise the issue with `status:bereit` and the lowest F number whose prerequisites are all closed (i.e. merged). Empty: stop. Features with open prerequisites simply stay in the queue, they are **not** blocked.
   - Claim: `scripts/pipeline.sh beanspruchen <issue>`. If this ends with an error, another run was faster: stop without changing anything. This happens when several issues get `status:bereit` at the same time.

Nothing found: note it and stop.

## 2. Implement a new feature

1. The lock is already set by `beanspruchen`.
2. **Understand.** The issue, the requirements it names in `docs/requirements/`, [domain-model.md](../../../docs/domain-model.md), [testability.md](../../../docs/testability.md), [navigation.md](../../../docs/ui/navigation.md), [test-data.md](../../../docs/test-data.md), [states.md](../../../docs/states.md), [api/openapi.yaml](../../../docs/api/openapi.yaml), [design-principles.md](../../../docs/design-principles.md).
3. **Unclear points:** make a sensible assumption and list it in the PR under «Assumptions». Do not ask. Only if it contradicts a requirement with status «Decided»: set `pipeline:blockiert` on the issue, ask the question, stop.
4. **Branch** from current `main`: `claude/f<nr>-<shortname>`, e.g. `claude/f04-responses`. If a `claude/f<nr>-…` branch already exists from an aborted run, continue there instead of starting over.
   **Push intermediate states:** commit and push to the branch at least every 30 minutes. The [watchdog](../pipeline-waechter/SKILL.md) uses this to see that the run is still alive. A run without a push for an hour counts as aborted once the lock is 3 hours old, and is restarted.
5. **Order of implementation:**
   1. API contract: update `openapi.yaml` if needed. `npx -y @redocly/cli lint docs/api/openapi.yaml` without errors and warnings.
   2. Backend: migration, domain with rules and unit tests, REST resources, permission checks with 403 tests, seed data according to test-data.md.
   3. Frontend: screens according to navigation.md, rules from testability.md, design tokens.
   4. E2E tests in `e2e/`: at least one test per acceptance criterion, test name with requirement ID, projects `mobile` and `desktop`, axe check per new screen.
   5. Update the docs: navigation.md (routes, test IDs), test-data.md (seed, scenarios), states.md (behavior), open-points.md (new questions).
6. **Check it yourself** before you hand over. Everything must be green:
   - Backend build with all tests
   - Frontend build and lint
   - E2E tests against the running app (database via `scripts/db-start.sh`)
   - A short walkthrough of your own with Playwright MCP: each acceptance criterion once on mobile, console without errors
   Fix whatever is red. Never hand over something you know does not work.
7. **Commit and push.** Commit messages in English, with feature number and requirement IDs, e.g. `F-04: Respond with one tap (ANM-08)`.
8. **Open the PR** against `main` with `scripts/pipeline.sh pr-erstellen "F-<nr> · <title>" <branch> pr.md`, description following [.github/pull_request_template.md](../../../.github/pull_request_template.md), with `Closes #<issue>`. Tick every item of the Definition of Done or explain why not.
9. **Handover:**
   - Final comment on the PR with marker `<!-- pipeline:entwicklung -->` (round 0): what is built, assumptions, how to try it out.
   - PR: `scripts/pipeline.sh label-setzen <pr> pipeline:review`
   - Issue: `scripts/pipeline.sh wechseln <issue> status:bereit status:in-arbeit`, then `scripts/pipeline.sh entsperren <issue>`

## 3. Rework

1. Set the lock, start comment.
2. Check out the PR branch (`scripts/pipeline.sh pr-branch <nr>`). The findings are in the latest review or test comment: `scripts/pipeline.sh letzter-befund <nr>`.
3. Determine the round: `scripts/pipeline.sh runde <nr>` (see agent-rules.md, section 6). If this would be round 5 or more: `pipeline:blockiert`, summary, stop.
4. If the finding reports conflicts with `main`: first `git fetch origin main && git rebase origin/main`, resolve conflicts, then check everything again. Then push with `git push --force-with-lease` to your own `claude/` branch (the only force push allowed).
5. Fix every finding: from the review «Must» and «Should», from the test errors and observations. «Notes» only if it takes little effort. For every error from the test, first write an E2E or unit test that shows it, then fix it.
6. Check it yourself as above (step 2.6), completely, not only what changed.
7. Commit and push to the same branch.
8. Final comment with `<!-- pipeline:entwicklung -->`: table finding → solution (file, test). Findings you deliberately do not implement, with a reason.
9. `scripts/pipeline.sh wechseln <nr> pipeline:nacharbeit pipeline:review`, then `scripts/pipeline.sh entsperren <nr>`.

## Implementing a follow-up fix

Issues with `typ:nachbesserung` come from the review or test of an earlier PR. They run through section 2 like a feature, with these differences:

- **Scope:** only what the issue asks for. Problem, location and acceptance criteria are in the issue, the origin under «Found in #…». No restructuring beyond that.
- **Branch:** `claude/nb<issue>-<shortname>`, e.g. `claude/nb13-me-409`.
- **Tests:** at least one automated test per acceptance criterion (unit, API or E2E, depending on where the problem is). The test must fail without the fix.
- **Commit:** `Follow-up fix #<issue>: <what>`, requirement IDs where there are any.
- **PR:** title `Follow-up fix · <issue title>`, with `Closes #<issue>`. The Definition of Done applies; mark items that do not apply (e.g. new screen) with «n/a».
- **Handover** as in step 9.

## First feature (F-00)

If there is no code yet, F-00 creates the foundation. The structure is in the backlog. Stick to the requirements there (Java 21, Flyway), so later runs do not have to restructure.

## Quality

- Better a smaller, fully working feature than a large half-done one. Do not split an issue that is too large on your own: set `pipeline:blockiert` instead, with a proposal for splitting it.
- No placeholders, no TODOs for the core of the feature, no commented-out tests.
- No new dependencies without a reason. Justify new dependencies in the PR.
