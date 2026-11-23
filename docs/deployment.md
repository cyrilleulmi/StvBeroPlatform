# Deployment

The platform runs as hosted **test environments** on [Fly.io](https://fly.io) with a [Neon](https://neon.com) PostgreSQL database (E-83). Production with real data comes later, after the login (O-01, O-02).

| Environment | Address | Database | Deployed |
|---|---|---|---|
| Test environment | `https://<FLY_APP>.fly.dev` (default `stvbero`) | default branch of the Neon project | after every green CI run on `main` (`.github/workflows/deploy-test.yml`) |
| Preview of a pull request | `https://<FLY_APP>-pr-<nr>.fly.dev` | Neon branch `preview/pr-<nr>` | on every push to the pull request; removed when it is closed (`.github/workflows/preview.yml`, nightly safety net `preview-cleanup.yml`) |

Both run the image built with profile `preview` ([Dockerfile](../Dockerfile), [fly.toml](../fly.toml)):

- Seed data of [test-data.md](test-data.md); sign in at `/dev-login` without a password. The clock stands at the default «now» of the seed (Mon 16.11.2026 06:00) and can be moved with the test endpoints.
- E-mail, push, routing and swiss unihockey run through the simulators: nothing goes out.
- Every start empties the database and builds it again (Flyway clean + migrate, seed). Changes made by testers last until the next stop.
- The machine stops when nobody uses it and starts on the next request (a few seconds, plus a few hundred milliseconds for the database). Neon suspends the database after 5 minutes without queries.
- The responses carry `X-Robots-Tag: noindex`.

The pipeline agents keep testing locally in the cloud session (`scripts/start.sh`); the previews are for people.

## Setup (once, by Cyrille)

Until these values exist, the three workflows do nothing and only leave a notice.

1. **Neon:** create an account and a project, region *AWS Europe Central 1 (Frankfurt)*, Postgres 16. Note the project ID (Settings → General). Create an API key (Account settings → API keys).
2. **Fly.io:** create an account, add a payment method, then create an org token: `fly tokens create org` (the workflows create and destroy the preview apps, so a deploy token of one app is not enough).
3. **GitHub** (Settings → Secrets and variables → Actions):

   | Kind | Name | Value |
   |---|---|---|
   | Secret | `FLY_API_TOKEN` | the org token of Fly.io |
   | Secret | `NEON_API_KEY` | the API key of Neon |
   | Variable | `NEON_PROJECT_ID` | the project ID of Neon |
   | Variable (optional) | `FLY_APP` | app name if `stvbero` is taken on Fly.io (names are global) |
   | Variable (optional) | `FLY_ORG` | Fly.io organization, default `personal` |
   | Variable (optional) | `NEON_DATABASE`, `NEON_ROLE` | only if the project does not use `neondb` / `neondb_owner` |

4. **First deploy:** Actions → «Test environment» → «Run workflow». The script creates the Fly app if it does not exist yet.

## Operation

- Deploy the test environment by hand: Actions → «Test environment» → «Run workflow».
- A preview is missing or broken: push again, or close and reopen the pull request.
- The Neon free plan allows 10 branches per project: the default branch and at most 9 previews at the same time. If the limit is reached, the preview run fails with a message; close old pull requests or run «Preview cleanup».
- Logs: `fly logs --app <app>`. Status: `fly status --app <app>`.
- Locally without Docker the app runs as before with `scripts/start.sh`. With Docker: `docker build --build-arg PROFILE=preview -t stvbero . && docker run -p 8080:8080 -e STV_DB_URL=… -e STV_DB_USER=… -e STV_DB_PASSWORD=… -e STV_SESSION_COOKIE_SECURE=false stvbero` (the cookie flag only for plain HTTP on localhost).

## Costs

Fly.io bills machines per second while they run (shared CPU, 1 GB: about 6.70 USD per month if it ran around the clock); stopped machines cost almost nothing. The Neon free plan includes 100 compute hours per project and month, enough as long as the environments sleep when unused.

## Changing it

`Dockerfile`, `fly.toml`, `scripts/deploy.sh` and the workflows are changed only through an issue of their own, never as a side effect of a feature. Secrets never go into the repository.
