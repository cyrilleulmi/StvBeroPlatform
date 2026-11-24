# Deployment

The platform runs as hosted **test environments** on [Fly.io](https://fly.io) (E-91). Production with real data comes later, after the login (O-01, O-02).

| Environment | Address | Deployed |
|---|---|---|
| Test environment | `https://<FLY_APP>.fly.dev` (default `stvbero`) | after every green CI run on a push to `develop` (job «Test environment» in `ci.yml`, which calls `.github/workflows/deploy-test.yml`) |
| Preview of a pull request | `https://<FLY_APP>-pr-<nr>.fly.dev` | on every push to the pull request, link as a comment in the pull request; destroyed when it is closed (`.github/workflows/preview.yml`, nightly safety net `preview-cleanup.yml`) |

Both run the image built with profile `preview` ([Dockerfile](../Dockerfile), [fly.toml](../fly.toml)):

- A throwaway PostgreSQL runs inside the container ([docker/start.sh](../docker/start.sh)). There is no external database.
- The club data set of [test-data.md](test-data.md): the real teams of the club, live data from swiss unihockey (`https://wc.swissunihockey.ch`, club `STV_SU_CLUB_ID`) and real routes, with the running clock. The data loads in the background after the start. Sign in at `/dev-login` without a password; `POST /api/test/reset?dataset=TEST` switches to the fixed test seed with simulators.
- E-mail and push stay off (no SMTP or VAPID configuration).
- Every start begins with an empty database and loads the club data again. Changes made by testers last until the next stop.
- The machine stops when nobody uses it and starts on the next request (a few seconds, plus the loading of the club data).
- The responses carry `X-Robots-Tag: noindex`.

The pipeline agents keep testing locally in the cloud session (`scripts/start.sh`); the previews are for people.

## Setup (once, by Cyrille)

Until the secret exists, the three workflows do nothing and only leave a notice.

1. **Fly.io:** create an account, add a payment method, then create an org token: `fly tokens create org`. The workflows create and destroy apps, so a deploy token of one app (`fly tokens create deploy`) is not enough.
2. **GitHub** (Settings → Secrets and variables → Actions):

   | Kind | Name | Value |
   |---|---|---|
   | Secret | `FLY_API_TOKEN` | the org token of Fly.io |
   | Variable (optional) | `FLY_APP` | app name if `stvbero` is taken on Fly.io (names are global) |
   | Variable (optional) | `FLY_ORG` | Fly.io organization, default `personal` |

3. **First deploy:** happens with the merge into `develop`. The script creates the Fly app if it does not exist yet.

## Operation

- Deploy the test environment again: re-run the CI run of the last push to `develop`. «Run workflow» on «Test environment» and the nightly «Preview cleanup» only appear in GitHub once the workflows are on the default branch `main` (GitHub runs manual and scheduled workflows from there).
- A preview is missing or broken: push again, or close and reopen the pull request.
- Logs: `fly logs --app <app>`. Status: `fly status --app <app>`.
- Locally without Docker the app runs as before with `scripts/start.sh`. With Docker: `docker build --build-arg PROFILE=preview -t stvbero . && docker run -p 8080:8080 -e STV_SESSION_COOKIE_SECURE=false stvbero`, then http://localhost:8080/dev-login (the cookie flag only for plain HTTP on localhost).

## Costs

Fly.io bills machines per second while they run (shared CPU, 1 GB: about 6.70 USD per month if it ran around the clock); stopped machines cost almost nothing. App and database share the 1 GB (about 370 MB in use after the start).

## Production later

Production needs a database that keeps its data. `docker/start.sh` starts no PostgreSQL when `STV_DB_URL` is set, so the same image works with an external database (for example Neon or Fly.io Managed Postgres) and the profile `prod`.

## Changing it

`Dockerfile`, `docker/start.sh`, `fly.toml`, `scripts/deploy.sh` and the workflows are changed only through an issue of their own, never as a side effect of a feature. Secrets never go into the repository.
