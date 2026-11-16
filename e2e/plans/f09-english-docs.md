# Test plan · F-09 English: glossary, documentation and pipeline (NF-02, NF-40, E-68)

Issue #48. Documentation-only change: no application code, so most cases are checks on the repository and on `scripts/pipeline.sh`, not on the UI.
Starting point for every case: a fresh clone of the PR branch, `origin/main` fetched.

| Case | Steps | Expected |
|---|---|---|
| K1 Glossary | Open `docs/glossary.md`; compare with the table in #48; look up every domain term of `docs/domain-model.md` | Issue table complete, every domain term present; `AGENTS.md` lists the glossary under «Read first» and says code, API and documents use these terms |
| K2 No German prose | Scan all Markdown (outside `backend/src`, `frontend/src`, `e2e/tests`, `status/`, build output), `scripts/*`, `.github/**`, skills for German stop words, with text in «…», `code` and "…" removed | Hits only in the glossary's German column and the allowed exceptions |
| K3 Requirement IDs and status | Parse ID → status from `docs/anforderungen/*.md` on `origin/main` and from `docs/requirements/*.md`; map Entschieden/Angenommen/Annahme/Offen/Später to Decided/Accepted/Assumption/Open/Later | Every old ID present with the same (translated) status; only NF-40 is new; `README.md` legend explains the five values |
| K4 NF-02 and NF-40 | Read `docs/requirements/10-non-functional.md` | NF-02: UI German, everything else English. NF-40 lists all exceptions from #48 |
| K5 Links | `python3 scripts/check-links.py` | «All relative links OK.» |
| K6 Pipeline script | `bash -n scripts/*.sh`; compare the subcommand list of `scripts/pipeline.sh` with `origin/main`; run help, `laufend`, `naechstes-feature`, `voraussetzungen 49` and `voraussetzungen 54` with both versions | Same subcommands; same exit codes and same open prerequisites (`F-09`; `F-12 F-14`), so `Voraussetzung:` is still parsed |
| K7 Skills | Every skill keeps its `name:`; every file path and link in the four skills resolves | All four names unchanged, no missing file |
| K8 Tests and no app code | `scripts/test.sh`; `git diff --stat origin/main...HEAD -- backend/src frontend/src e2e/tests status` | All green; diff empty |
| K9 App unchanged (smoke) | Reset, `/dev-login` as `petra`, `/termine`, open an event, `/profil`; mobile (375×812) and desktop (1280×800), dark theme once | German UI as before, no console errors, no failed requests |
