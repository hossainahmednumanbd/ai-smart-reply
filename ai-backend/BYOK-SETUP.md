# Personal Gemini / OpenAI gateway setup

The Android app now has an opt-in AI auto-reply switch. It sends messages to a user-configured HTTPS gateway, **not directly to Gemini or OpenAI**.

Each user must configure their own HTTPS gateway and bearer token in Settings. Their gateway is responsible for storing their own Gemini/OpenAI provider API key, enforcing quotas and forwarding POST /reply requests.

Request: {"message":"hello","language":"auto"}
Response: {"reply":"Hello! How can I help?"}

The existing OpenAI example is ai-backend/server.mjs. A Gemini implementation and hosted per-user credential management are not yet included. Do not paste Gemini/OpenAI keys into the Android app's access-token field.

AI auto-reply is limited to 20 generation attempts per day and one successful send per minute. It skips messages containing some sensitive terms, but this is not comprehensive. Android notification reply actions may not be present or may fail. Test carefully with trusted contacts before production.

Never publish provider API keys in source control or embed them in the APK. Each user is responsible for their provider usage charges and their own gateway hosting costs.
