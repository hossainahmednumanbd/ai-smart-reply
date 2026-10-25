# AI Smart Reply backend

Node.js 20+ HTTPS-hosted API for the Android app. No API key belongs in the APK or GitHub.

## Environment variables (configure privately on hosting)
- OPENAI_API_KEY: OpenAI API key, never share or commit.
- APP_AUTH_TOKEN: unique random secret, 24+ characters. Enter this token in the app's AI settings.
- OPENAI_MODEL: optional, defaults to gpt-4.1-mini.
- BUSINESS_INFO: verified business description, current prices, delivery and service policies. Never include private customer data.
- PORT: optional, defaults to 3000.

Start: `node ai-backend/server.mjs`

Place behind an HTTPS reverse proxy (or use an HTTPS hosting provider). Example endpoint in the Android app: `https://your-domain.example/reply` and enter APP_AUTH_TOKEN in the token field.

## Privacy and safety
Messages are transmitted only after the app user explicitly approves the AI generation prompt. They are sent to the configured backend and then to OpenAI. The backend uses `store:false` and does not log message contents. Hosting and provider data policies still apply. Do not use the same token for untrusted public clients: a shared mobile token can be extracted, so add user authentication, per-user rate limits, and quotas before distributing to thousands of users. The endpoint is not ready for public multi-user deployment.

Start with manual approval. Automatic AI sending, moderation, and customer-specific knowledge require further development and testing. OpenAI API usage is billed separately from ChatGPT subscriptions.
