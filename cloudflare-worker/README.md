# AI Smart Reply Cloudflare Worker

Deployment entrypoint: `cloudflare-worker/src/index.js`.
Wrangler configuration: `wrangler.jsonc` at the repository root.

## Existing production Worker

- Worker: `wandering-waterfall-20ai-smart-reply-backend`
- D1 binding: `DB`, using the existing `ai-smart-reply-db` database
- Google login: retain the existing runtime `GOOGLE_WEB_CLIENT_ID`
- Gemini: retain the existing runtime secret `GEMINI_API_KEY`
- Optional model override: retain the existing runtime `GEMINI_MODEL`

The configuration uses `keep_vars: true` to preserve dashboard variables.
Do not copy secret values into this repository or into build variables.
The deploy does not run database migrations, create databases, or change Google OAuth settings.

## Cloudflare Builds

Connect the existing Worker to `hossainahmednumanbd/ai-smart-reply`:

- Production branch: `main`
- Root directory: `/`
- Build command: empty (None)
- Deploy command: `npx wrangler deploy`
- Disable builds: off, with changes saved

A new commit on the connected branch triggers a build when builds are enabled.
Saving the settings alone may not deploy an older commit.
Check the Cloudflare build logs and active deployment before assuming the code is live.
The Android GitHub Actions build is separate and does not deploy this Worker.

## Verification

- `GET /health` should return HTTP 200 and `{"status":"ok"}`.
- `POST /reply` requires a valid Google ID token and JSON `{"message":"..."}`.
- The existing D1 database must have a `usage` table with `bucket TEXT PRIMARY KEY`
  and `count INTEGER NOT NULL DEFAULT 0`.
- A health response alone does not verify Google login, D1 queries, or Gemini.
- After deploying, use the Android app to request one manual AI reply.
  If HTTP 502 persists, capture the safe error message. The entrypoint distinguishes
  Gemini quota, access, model, request, and provider failures without returning the API key.

The other Worker files in this repository are not used by the root deployment configuration.

## Reproducible Git builds and direct Actions deployment

The repository root now pins Wrangler 4.154.0 with a lockfile and requires Node 22+.
Run `npm ci`, `npm run build`, and `npm run test:worker` from the root.
`npm run deploy` targets the existing Worker through `wrangler.jsonc`.
The existing `npx wrangler deploy` Cloudflare build command also uses this pinned local Wrangler.

`.github/workflows/worker.yml` checks syntax, all backend tests, and a no-write Wrangler dry run.
On authorized pushes to main or the voice feature branch it checks whether the standard
`CLOUDFLARE_API_TOKEN` GitHub repository secret already exists. It reports presence only,
never the value. If present, it deploys the existing Worker in the account associated with
this project's Cloudflare build checks, preserving current runtime variables/secrets and D1.
No deployment runs from a pull request. No token is created, copied, or requested by the workflow.

If the token is absent, deployment steps are skipped and the Actions summary explicitly says
no production deployment occurred. A green validation job alone does not mean the server is live.
The Cloudflare Git integration has its own build credentials; those do not automatically become
GitHub Actions secrets. The separate Cloudflare check run must also be inspected for its outcome.

After a real deployment, `scripts/verify_worker_live.py` requires a health response containing
`voice_writer: true`, plus authenticated `/voice` and `/reply` route contracts (401 without a token).
That verifies rollout and retained authentication, not a successful real Gemini request.
Test with the signed-in Android app before claiming full voice integration is complete.
