# AI Smart Reply backend (not deployed)

Requires Node.js 20+, HTTPS reverse proxy, a private OPENAI_API_KEY, APP_ACCESS_TOKEN and rate limiting. The app sends a selected message to a configured HTTPS endpoint ending in /reply only after the user taps Generate AI Reply. It does not automatically upload all notifications. Never commit credentials or put the provider key inside the APK.

Run with environment variables OPENAI_API_KEY and APP_ACCESS_TOKEN set on the server, then `node backend/server.mjs`. Deploy behind HTTPS and restrict access. The token is a shared prototype secret and is **not suitable for a multi-user production service**. For production use individual user authentication, quotas, abuse controls, data retention policies and encrypted token storage. AI responses cost money; this is not a free AI service.
