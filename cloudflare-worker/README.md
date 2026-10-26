# AI Smart Reply Cloudflare Worker

Source: `cloudflare-worker/worker.js`. A GitHub commit does **not** automatically update the live Cloudflare Worker until Git deployment is connected.

Existing Worker name: `wandering-waterfall-20ai-smart-reply-backend`.

Configure on the existing Worker:
- Cloudflare **Secret** `GEMINI_API_KEY` (never commit the value)
- Cloudflare **Variable** `GOOGLE_WEB_CLIENT_ID` = `656908487206-i6u8rt2jck0a5k9cbbc9v6pbr5ee2cab.apps.googleusercontent.com`
- Optional variable `GEMINI_MODEL` = `gemini-2.5-flash`
- D1 binding `DB` with table `usage(bucket TEXT PRIMARY KEY, count INTEGER NOT NULL DEFAULT 0)`

Endpoints: `GET /health` checks that the Worker is running; `POST /reply` requires a valid signed Google ID token and JSON `{"message":"..."}`. A successful health check does not prove Gemini is configured.

For mobile-friendly deployment, connect the **existing** Worker to GitHub in Cloudflare > Workers & Pages > Worker > Settings > Builds > Connect. Use repository `hossainahmednumanbd/ai-smart-reply`, root directory `cloudflare-worker`, and ensure Wrangler's `name` matches the existing Worker. A Wrangler D1 configuration needs the actual database ID from Cloudflare; never guess it. Preserve the existing secrets and bindings.

Start with manual replies and test before enabling auto replies. Gemini free tier can have rate limits and the model may be unavailable for some accounts. Never expose API keys.
