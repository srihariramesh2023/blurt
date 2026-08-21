# Blurt

A voice assistant for Android. You talk to it, it saves what matters, and forgets the rest.

## What it does

Tap the orb. Say something. Blurt listens, saves it if it's worth saving, and replies out loud.

- Say "meeting with Sarah tomorrow at 3" — Blurt saves a reminder and tells you.
- Vent about your boss — Blurt listens, then forgets. No receipts.

## Features

**Voice conversation** — Tap the orb and talk. Blurt replies in a natural voice (Fish Audio, or your device voice as backup). The conversation keeps going until you say stop. No confirm screens.

**Auto-save** — Blurt decides what to keep. Reminders, tasks, notes — saved immediately. Say "don't save this" and it drops the transcript.

**Heads-up reminders** — When a reminder saves, Blurt asks "Want me to remind you 30 minutes before?" Say yes, it arms a separate early nudge.

**Classification** — One AI call picks an intent (Note / Task / Idea / Reminder) and a category (Work, Personal, Health, etc.). Uses Groq (fast, ~14k free requests/day) with Gemini as fallback. No key? Blurts save unclassified. Capture never blocks.

**Reminders** — Time-based blurts get priority notifications with heads-up banners. Optional daily/weekly recurrence. Alarms survive reboots.

**Library** — Every blurt, newest first. Filter chips: All, Reminders, Tasks, Ideas, Important, Archived. Plus category filters.

**Semantic search** — "trip to the beach" finds "vacation in Goa" even though no words match. Uses Gemini's free embedding tier. Keyword search is always-on as fallback.

**Your keys, your data** — The APK ships with zero API keys. You paste them in-app. Stored encrypted in the Android Keystore (AES-256-GCM). Never leave your device.

**Google Sign-In** — Firebase Auth. Sessions persist between launches.

**Cross-device sync** — Firebase Realtime Database (free Spark plan). Deletes propagate. Conflicts resolve by last-write-wins.

## How it works

- **UI** — Jetpack Compose, dark theme. A violet orb with four states: idle, listening, processing, complete.
- **Data** — Room database on-device. Everything works offline.
- **AI** — Groq preferred, Gemini fallback. Fish Audio for TTS, device TTS as backup. You bring all the keys.
- **Sync** — Pushes pending blurts to `users/{uid}/captures`, merges changes back. Local delete always wins.

## Build

JDK 17, Android SDK (platform 35, build-tools 35.0.0).

```bash
./gradlew assembleDebug
```

APK at `app/build/outputs/apk/debug/app-debug.apk`.

Tests:

```bash
./gradlew testDebugUnitTest
```

## CI

GitHub Actions builds the debug APK on every push to `main` and on PRs (`.github/workflows/build.yml`).

## Releases

Tag a version to build a signed release APK and attach it to a GitHub Release:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow (`.github/workflows/release.yml`) uses `BLURT_KEYSTORE_BASE64` plus password/alias secrets when set. Otherwise it generates a keystore and caches it.

For local release builds, create `keystore.properties` at the project root:

```properties
storeFile=my-keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Then `./gradlew assembleRelease`.

## License

[MIT](LICENSE)

## Setup

The app builds and CI passes without any Firebase config. To enable sign-in, sync, and reminders, do a one-time Firebase setup.

### 1. Firebase + Google Sign-In

1. [Firebase console](https://console.firebase.google.com/) → create project.
2. Add Android app, package `com.blurt.app`.
3. Register SHA-1 fingerprint:
   ```bash
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore \
     -storepass android -keypass android
   ```
4. Download `google-services.json` → `app/google-services.json` (gitignored).
5. Authentication → Sign-in method → enable Google.

Done. The Gradle plugin picks up the file. No code changes.

### 2. Email branding

In Firebase console → Authentication → Templates:
- Email verification: From name = `Blurt`, subject = `Verify your email for Blurt`
- Password reset: From name = `Blurt`, subject = `Reset your password for Blurt`
- Project settings → public-facing name = `Blurt`

### 3. Cross-device sync

Firebase console → Build → Realtime Database → Create database → pick region → locked mode (or paste `database.rules.json`).

Download `google-services.json` again (needs the Database URL).

How it works: each capture gets a UUID and PENDING state. The sync engine pushes PENDING rows up and merges other devices' changes back. Deletes are tombstoned. Local delete always wins over stale remote copy.

### 4. Bring your own key (BYOK)

Zero keys in the APK. Users paste them in-app.

- **First install**: onboarding shows a "Bring your own keys" screen (once, before mic permission). Skip or paste.
- **Any time**: avatar → AI keys.
- **Groq** (classification): free key from [console.groq.com](https://console.groq.com). No credit card. ~14,400 requests/day.
- **Gemini** (search + voice): free key from [aistudio.google.com/apikey](https://aistudio.google.com/apikey). Keys come as `AIza…` or `AQ.…` — both work.
- Keys stored in Android Keystore (AES-256-GCM). Take effect on next analysis. No restart needed.

Semantic search: blurts are embedded once (`gemini-embedding-001`, batched 100/call) and cached in Room. Vectors are scoped to the signed-in user.

### 5. Voice companion

When a Groq key is active, the classifier returns a spoken reply along with the analysis — voiced with Fish Audio (or device TTS as fallback).

- Auto-save by default.
- Follow-up: when a reminder saves, Blurt asks about the heads-up nudge. Parsed locally, no AI call.
- "Don't save this" → Blurt forgets. Transcript dropped.
- Tap anywhere on the reply to skip it.

### 6. Classification and reminders

1. **Links** — detected by rule, saved as Link blurts, skip AI.
2. **Everything else** — one AI call: intent (Note/Task/Idea/Reminder) + category (Work, Personal, Health, Finance, Travel, Ideas, Learning, Home, Fitness, Social, Shopping, Other) + time extraction.
3. **Confirm sheet** — typed capture with a detected time shows "Remind me / Just save". Voice saves automatically.

Reminders: `RTC_WAKEUP` alarm → priority notification (heads-up banner + sound). Optional daily/weekly. Deep-links to the blurt. Rescheduled after reboot. Cancelled on delete.

Offline/no key: saves instantly, unclassified. No network required.

Old blurts get classified lazily in the background.

### 7. Sessions

- Firebase restores the session on launch. Login screen never flashes.
- Sign out: avatar → Sign out. Clears Firebase session. Previous user's data unreachable.
- Legacy blurts (pre-auth) are claimed by the first user who signs in.
