# 10 · Non-functional requirements

Status legend: see [README](../../README.md#requirement-status).

## Usability

| ID | Requirement | Status |
|---|---|---|
| NF-01 | The app can be used without instructions: few clicks, plain language, no jargon. | Decided |
| NF-02 | The user interface is German. Everything else in the repository is English (NF-40). | Decided |
| NF-03 | Mobile first: the app is built for the phone in the hall. On the desktop it is fully usable. | Decided |
| NF-04 | Touch targets are at least 44px. | Decided |
| NF-05 | The app can be installed as a PWA. | Decided |
| NF-06 | Light and dark mode are mandatory. The mode follows the system setting and can be switched by hand. | Decided |
| NF-07 | The basic design «Sporthalle» is binding for all tiles (see [design-principles.md](../design-principles.md), E-90). Shape, font, spacing and motion apply to all themes. | Decided |
| NF-08 | There are two themes: «Hell» and «Dunkel» (E-90, before «Hell», «Düne» and «GitHub Dark», E-67). They differ only in their colors and can be chosen in the profile, plus «System»: in light mode «Hell» applies, in dark mode «Dunkel». Without a choice the app is «Hell», also on a device in dark mode (E-98); «Dunkel» and «System» have to be chosen. The choice is stored in the browser. Colors and rules: [design-principles.md](../design-principles.md#themes). | Decided (selection), Accepted («System») |

## Structure

| ID | Requirement | Status |
|---|---|---|
| NF-10 | Every function is a self-contained tile. New tiles are added without changing existing ones. | Decided |
| NF-11 | A tile provides its metadata (title, icon, route, required role) and registers centrally. The dashboard renders the overview from it. | Decided |
| NF-12 | The dashboard shows the tiles depending on the role. | Decided |

## Quality and delivery

| ID | Requirement | Status |
|---|---|---|
| NF-20 | Every result runs and can be opened directly (URL or start command). | Decided |
| NF-21 | Every result is checked on desktop and mobile. | Decided |
| NF-22 | There is seed data to try things out. | Decided |
| NF-23 | Backend logic is covered by tests. | Decided |
| NF-24 | Schema changes to the database are stored as versioned migrations. | Decided |

## Data protection

| ID | Requirement | Status |
|---|---|---|
| NF-30 | Only the personal data needed for the functions is stored. | Assumption |
| NF-31 | Comments and reasons for declining are only visible to coaches and the person themselves ([ANM-07](04-responses.md)). | Accepted |
| NF-32 | Exactly which data is stored and who sees what (Swiss nFADP) is not fully clarified. The app is currently intended for adults. If minors are added later, the point must be reassessed. | Open |

## Language

| ID | Requirement | Status |
|---|---|---|
| NF-40 | The repository is English: code, API, database, documents, skills, scripts, tests, commit messages, PR descriptions and agent comments use the terms from [glossary.md](../glossary.md) (E-68). German stays: (1) all text users see in the app (labels, messages, e-mails, notifications) and the status page text, German (Switzerland) with «ss» instead of «ß»; (2) the browser addresses of the app (`/termine`, `/teams`, `/profil`, `/verwaltung`, `/benachrichtigungen`, …); (3) requirement, decision, open-point, scenario and feature IDs (`ANM-03`, `E-65`, `O-12`, `S19`, `F-04`); (4) GitHub labels (`status:bereit`, `pipeline:review`, `typ:nachbesserung`, …), skill names (`pipeline-entwicklung`, `pipeline-review`, `pipeline-test`, `pipeline-waechter`), the subcommands of `scripts/pipeline.sh` and the comment markers (`<!-- pipeline:entwicklung -->`, …); (5) the keyword `Voraussetzung:` in issue bodies; (6) seed person keys (`alex`, `petra`, `anna`, …) and seed names; (7) closed issues, merged PRs and git history, which are not rewritten. Proper names stay as they are. | Decided |
