# Cloudflare BYOK Backend (Google Login)

Files: `cloudflare/worker.js` and `cloudflare/schema.sql`.

## Requirements
1. Create a Cloudflare D1 database and run the SQL from schema.sql in its console.
2. In Worker Settings > Bindings add a D1 binding named **DB** pointing to that database.
3. Set a **Secret** named **ENCRYPTION_SECRET** to a long random string (at least 32 characters). Do not share it. Back it up safely; losing it makes saved keys unreadable.
4. Configure **GOOGLE_CLIENT_ID** to the **Web OAuth client ID** from your Google Cloud/Firebase project. The Android Google Sign-In implementation must request ID tokens with this same Web client ID. Never accept arbitrary client IDs from requests.
5. Replace the Worker Hello World source with worker.js and Deploy.

## Endpoints
- GET /health — public readiness check.
- GET /account — Authorization: Bearer <Google ID token> — checks account/key.
- POST /key — Authorization: Bearer <Google ID token>; JSON {"provider":"gemini","apiKey":"..."} or provider "openai".
- DELETE /key — remove own key.
- POST /reply — Authorization: Bearer <Google ID token>; JSON {"message":"Hello"} — JSON {"reply":"..."}.
- Limit: 20 AI requests per user per hour (D1).
- Keys encrypted with AES-GCM before D1 storage. The Worker decrypts only for provider calls. Do not log secrets or incoming message bodies.

## IMPORTANT — NOT YET PRODUCTION READY
The Android app currently uses a generic backend bearer token, **not Google ID tokens**. Its Google Sign-In and key management screens must be implemented before connecting this Worker. Do not paste a provider API key into the old access token field. This Worker also needs abuse monitoring, retention policy, user consent/privacy notice, and testing before public rollout.

Google tokeninfo is used for initial server-side verification, with audience/issuer/expiry checks; for production consider verification with Google's public signing keys, stricter rate limiting, and operational safeguards. The free Cloudflare tier and Gemini API tier both have separate usage limits. No provider charges are paid by the app owner when individual users connect their own provider keys, but Worker/D1 usage may exceed free quotas.
