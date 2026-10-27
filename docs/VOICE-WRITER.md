# Voice Writer floating panel, 0.4.1 preview

This corrects the 0.4.0 prototype, which opened a separate full page and asked users for Gemini keys.

## Interaction

- Tap the floating microphone bubble to open a small rounded panel over the current app. Voice Writer has its own task affinity, a floating translucent theme and no Recents entry, so the main AI Smart Reply page is not brought forward behind it.
- After the first recording disclosure and microphone permission, tapping the bubble starts recording when there is no pending audio. The panel shows a recording timer and a large Stop button. There is no Gemini key form or capture-method menu.
- Stop finalizes continuous native AAC/M4A recording, transcribes it through the authenticated app server, then refines it. Refine is the default; Original and Learner remain available.
- The result is copied only while the panel is foreground. Review/edit or copy again, close the panel and paste in the messaging app. Voice Writer does not send a message.
- Failed requests keep local audio for replay/retry. Starting a new recording asks before replacing existing audio and text.
- Capture stops when leaving the panel or locking the phone. Limits: 10 minutes / 4.5 MB. A process crash during an unfinished MP4 recording can make it unplayable. Recognition and refinement must be checked against the source audio for mistakes; no guarantee of word-perfect transcription or fixed response time is made.

## Server rollout is required

The new app uses `POST /voice` on the existing Cloudflare Worker. It authenticates with the same Google ID token as the rest of the app. Gemini keys remain on the server; no user key is requested or embedded in the APK.

`cloudflare-worker/src/voice.js` handles bounded audio/text input, generation, incomplete-result rejection and an independent daily quota (40 voice operations per Google user, normally two operations per refined recording). The existing `/reply` prompt, output limits, model fallback and reply quota are unchanged. D1 uses a separate `YYYY-MM-DD:voice:user` bucket in the existing usage table; no schema migration is needed. Voice audio/transcripts are not stored in D1.

Deploy the updated Worker using the existing bindings and secrets. Preserve DB, GOOGLE_WEB_CLIENT_ID, GEMINI_API_KEY and GEMINI_MODEL. Optional VOICE_GEMINI_MODEL selects the voice model. `python3 scripts/bundle_voice_worker.py` generates the import-free dashboard module `cloudflare-worker/dist/worker-voice.mjs`; no keys are included.

Before giving this version to users, confirm `/voice` rejects unauthenticated requests with 401, not 404, then use an actual signed-in phone to test recording, transcription, refinement and paste. Merely compiling the APK does not validate the deployed route or microphone hardware. Keep PR #1 draft and do not merge or describe this as ready while deployment/device checks remain pending.

## Regression checks

- All existing Auto Reply listener, recovery and rule tests must pass.
- Backend tests cover existing reply behavior and voice authentication, separate quota, long text, input bounds, incomplete provider answers and safe errors.
- Android tests cover the floating theme/task affinity, absence of a key form, recording release on pause, audio retention on failure and foreground-only copy.
- Physical checks remain necessary for Android 13–16 permissions, Google token renewal, microphone quality, short/long recordings, phone lock, incoming calls, poor network, keeping the current messaging app behind the panel, and installation over 0.3.6/0.4.0 without losing templates/settings.
