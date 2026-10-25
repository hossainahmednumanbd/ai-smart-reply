# Cloudflare Worker for AI Smart Reply

This is a **new** Worker implementation, not a deployed service. Deploy it to Cloudflare and configure:

- D1 binding named `DB` pointing to the database with table `usage (bucket TEXT PRIMARY KEY, count INTEGER NOT NULL DEFAULT 0)`.
- Plain-text variable `GOOGLE_WEB_CLIENT_ID` = `656908487206-i6u8rt2jck0a5k9cbbc9v6pbr5ee2cab.apps.googleusercontent.com` (the **Web** OAuth client ID).
- Encrypted **Secret** `GEMINI_API_KEY` = the Gemini API key, entered in Cloudflare only. Never commit or share it.
- Optional plain-text `GEMINI_MODEL` = `gemini-2.5-flash-lite`.

Endpoints:
- `GET /health` -> `{"status":"ok"}`
- `POST /reply` with `Authorization: Bearer <Google ID token>` and JSON `{"message":"..."}` -> `{"reply":"..."}`.

The backend validates the Google-signed ID token against the Web client ID, checks expiry and issuer, and limits each verified Google user to 20 requests per UTC day using D1. Tokens expire and the Android app must refresh them. Google Login in the Android app is **not yet implemented** by this Worker commit.

Security: do not put Gemini keys in the Android app. This is a starter backend, not a complete production system: implement consent, privacy policy, retention, abuse protection, and billing/usage controls before public release. Free Gemini quotas may be lower than your user demand and can change. The existing Android app's static backend token configuration is not compatible with Google ID tokens until its login flow is updated.
