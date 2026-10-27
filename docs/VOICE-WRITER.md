# Floating Bengali Voice Writer preview

Continue development on `feature/floating-voice-writer`, Draft PR #1. Do not merge until device checks below pass.

## Use

1. Install the signed preview as an update. Do not uninstall the previous app: that would delete local settings and reply templates.
2. Home → Floating Voice Button চালু করুন → allow display over other apps. On Android 13+, allow notifications to keep the Stop control visible. If notifications are denied, use the Home stop button or Android's active-app control.
3. Drag the microphone bubble to move it; tap to open Voice Writer. The bubble itself never records audio. Stop it from the notification or Home.
4. Choose capture method:
   - **বাংলা Speech Recognition**: phone's installed speech service, language `bn-BD`. Original mode needs no Gemini key. Recognition sessions restart after final callbacks, with a brief gap. This method cannot guarantee uninterrupted capture. Use the audio method for longer speech.
   - **নিরবচ্ছিন্ন অডিও রেকর্ডিং + Gemini**: records AAC/MPEG-4 locally, capped at 10 minutes or 4.5 MB. A Gemini API key and network are required to turn this audio into Bengali text. Confirm the in-app disclosure before recording. The screen stays on during recording. Leaving the screen or locking the phone stops capture.
5. Original preserves recognized/transcribed text, Refine corrects spelling and punctuation, Learner rewrites in simpler Bengali. Names, numbers and meaning should be reviewed before use.
6. Finish waits for the last speech result before processing. A timeout preserves provisional text for review and does not silently copy it as confirmed. Duplicate or stale recognizer callbacks are ignored.
7. Completed text is copied only while the writer is in the foreground. Edit it and press the copy button again if needed, then paste into WhatsApp, Messenger or another app.
8. If Gemini fails, the original transcript and locally recorded audio remain. Use the retry or playback buttons. Clear the draft to explicitly delete the stored text and audio. A force-stop or process crash during an unfinished MP4 recording may leave an unplayable file; finalized recordings are retained for retry.

## Data and permissions

- `RECORD_AUDIO` is requested only on explicit capture. Denial is handled; permanent denial links to app settings.
- `SYSTEM_ALERT_WINDOW` is optional and separate from notification access for Auto Reply.
- The overlay uses a user-started `specialUse` foreground service with a persistent notification and Stop action. It has no microphone capture and no boot restart.
- Microphone capture runs only in the visible activity, so a microphone foreground service and background audio permission are not requested.
- Clipboard copy needs no Android permission and sends no message automatically.
- Gemini key storage uses Android Keystore AES-GCM. Only ciphertext is in private preferences. The key is excluded from view state/autofill and can be deleted. No embedded key, source-controlled secret or auto-reply backend is used for Voice Writer.
- Speech Recognition may send speech to the phone's selected speech provider. Gemini requests send text or recorded audio directly to Google with the user's key. Charges and quotas belong to that API account.
- Draft text and pending audio are in private no-backup app storage. App backup remains disabled. No recording is uploaded until the user requests text generation.

## Validation

Automated tests cover session completion, timeout and duplicate callbacks, provisional tails, Bengali text accumulation, Original without an API key, missing keys, incomplete Gemini responses, clipboard foreground access, draft recovery, and overlay enable/denial/Stop. Existing notification/reply and gateway tests must also pass. Robolectric tests model Android behavior; they do not replace physical microphone, OEM battery-management or real-provider validation.

Before merging:

- [ ] Install over 0.3.6 and confirm templates, login and Auto Reply settings survive.
- [ ] On Android 13–16, grant/deny/revoke overlay, notifications and microphone; test permanent microphone denial.
- [ ] Drag the bubble at screen edges, rotate the phone, reopen it, and stop it from Home and notification.
- [ ] Record 30 seconds, two minutes with pauses, and up to the recording limit; listen to the saved audio and compare names/numbers with text.
- [ ] Test unavailable speech service, busy microphone, silence, airplane mode, low connectivity and phone lock.
- [ ] Use a valid personal Gemini key for Original audio transcription, Refine and Learner. Test invalid key, quota failure and a retry. No live Gemini key is required by or embedded in the tests.
- [ ] Copy Bengali into WhatsApp/Messenger; confirm no messages are sent automatically by the writer.
- [ ] Receive messages from multiple contacts while the bubble is enabled; confirm the existing Auto Reply flow, template priority and duplicate protection still work.
- [ ] Review special-use foreground service and overlay declarations before Play Store submission.

References: [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [Gemini audio input](https://ai.google.dev/gemini-api/docs/audio).
