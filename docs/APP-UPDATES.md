# In-app update notices

Version 0.4.4 (15) introduces a public GitHub Releases update check at cold launch (six-hour successful-check interval) and a manual Settings button. Original Home and reply behavior are unchanged. Users may defer a version for 24 hours. Offline/API failures are silent on automatic checks and actionable on manual checks.

Only published releases with uploaded assets named ai-smart-reply-<versionCode>.apk on this exact repository are considered. Numeric Android version codes prevent equal-version or downgrade prompts. Preview installs may receive prereleases; stable installs ignore them. Draft releases never appear. Downloads open in the browser and Android performs user-approved installation and signing-certificate checks. No install/storage permission is added. Play-installed apps are directed to Google Play.

Existing APKs cannot gain this feature remotely: users must install version 15 once, preserving their existing app data. Afterward published newer versions appear inside the app. This is an on-open notice, not a push notification while the app is closed.

Publishing: after review, a feature-branch push commit containing [publish-preview] lets the existing Android workflow run tests/lint and permanently signed builds, then publish the APK as a GitHub prerelease targeted at that exact feature commit. A separate publication job alone has contents:write. PR builds cannot publish. Never merge main merely to publish a preview. Verify real-device update/install/data preservation before stable release. If publication must be retried, inspect the existing release first; do not overwrite a version silently.
