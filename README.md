# Blurt

Blurt is a native Android application built with Kotlin and Jetpack Compose. It
is a quiet home for the things on your mind — text, ideas, and links —
with a dark-first, Apple-inspired design language.

## Features

- **Capture**: a unified editor — just type. Links are detected by rule;
  everything else is classified by Gemini into a fixed category list, and
  mentioned times become reminders.
- **Reminders**: blurts that mention a time can be turned into **priority
  Blurt notifications** (high-importance channel, heads-up banner) via a
  confirm sheet at save time — no third-party alarms.
- **Library**: every blurt, newest first.
- **Search**: semantic search by meaning (Gemini's free embedding tier),
  with live keyword search as the always-on fallback.
- **Authentication**: Google Sign-In only (Firebase Auth). Sessions persist
  between launches; the app never shows the login screen to a signed-in user.
- **Data isolation**: every blurt is owned by the signed-in user's UID and
  scoped at the database layer — one user can never read or mutate another
  user's data.

## Requirements

- JDK 17
- Android SDK (platform 35, build-tools 35.0.0). The build locates it via
  `local.properties` (`sdk.dir=...`) or the `ANDROID_HOME` environment variable.

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run the unit tests (includes repository, data-isolation, and migration tests):

```bash
./gradlew testDebugUnitTest
```

## CI

A GitHub Actions workflow (`.github/workflows/build.yml`) builds the debug APK
on every push to `main` and on pull requests, and uploads it as a build
artifact.

## Releases — zero-budget APK distribution

Tagging a version builds a **signed release APK** and attaches it to a GitHub
Release, so anyone can install Blurt straight from GitHub — no Play Store
account (and its $25 fee) required:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow (`.github/workflows/release.yml`) uses the `BLURT_KEYSTORE_BASE64`
secret plus `BLURT_KEYSTORE_PASSWORD` / `BLURT_KEY_ALIAS` /
`BLURT_KEY_PASSWORD` (base64 of a `.jks` keystore and its credentials, set in
**Settings → Secrets and variables → Actions**) when present. Until then it
falls back to generating a keystore once and caching it, so releases stay
installable — set the secrets before distributing widely so the signing key
never changes.

For local release builds, create a gitignored `keystore.properties` at the
project root:

```properties
storeFile=my-keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

and run `./gradlew assembleRelease`. Without any keystore the release build
stays unsigned (debug builds are unaffected).

## Authentication — provider-side setup

The app is built and CI is green **without** any Firebase configuration. To
turn on real sign-in you must complete a one-time Firebase setup; the app
detects the missing config and shows a friendly "sign-in isn't set up" message
until then.

### 1. Create the Firebase project and register the app

1. Go to the [Firebase console](https://console.firebase.google.com/) and
   create a project (or reuse one).
2. Add an **Android app** with package name `com.blurt.app`.
3. Register the app's **SHA-1** fingerprint (needed for Google Sign-In).
   Debug fingerprint:
   ```bash
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore \
     -storepass android -keypass android
   ```
4. Download the generated `google-services.json` and place it at
   `app/google-services.json` (this file is gitignored and must never be
   committed — it is per-developer/project secret config).
5. In **Authentication → Sign-in method**, enable **Google**.

That's it — the `google-services` Gradle plugin picks the file up
automatically on the next build, and the OAuth web client ID is read from it
at runtime. No code changes required.

### 2. Email branding (verification / password-reset emails) — checklist

Firebase sends sign-in related email on Blurt's behalf. To make it look like
an official Blurt communication (instead of a generic Firebase email),
complete this checklist in the Firebase console (all provider-side — the app
has no further configuration):

- [ ] **Firebase console → Authentication → Templates**
- [ ] For the **Email verification** template, click **Edit** and:
      - [ ] Set **From name** to `Blurt` (the visible sender name)
      - [ ] Set the **subject** to `Verify your email for Blurt`
      - [ ] Confirm the body explains *why* the email was sent (the default
            copy does) and includes the single verification action button
- [ ] For the **Password reset** template, click **Edit** and:
      - [ ] Set **From name** to `Blurt`
      - [ ] Set the **subject** to `Reset your password for Blurt`
- [ ] **Firebase console → Project settings** → set the **public-facing name**
      to `Blurt` so default email text refers to the product by name

For a fully custom HTML layout, you would need a backend email service (Cloud
Functions + SendGrid/Postmark) — beyond what the auth provider exposes and out
of scope for now.

### 3. Cross-device sync — provider-side setup (checklist)

Blurt syncs captures across the user's devices via the Firebase **Realtime
Database** — free on the Spark plan with **no billing account required**.
Captures live at `users/{uid}/captures`, scoped per user by the rules in
`database.rules.json`. The app runs fully local until the database exists:

- [ ] **Firebase console → Build → Databases and storage → Realtime Database**
      → **Create database** → pick a region → set the security rules to
      **locked mode** (or paste the contents of `database.rules.json` and
      Publish)
- [ ] **Project settings → Your apps → Download google-services.json** again
      (it must contain the Realtime Database URL) and replace
      `app/google-services.json`

How sync works: every capture is created with a device-generated UUID id and a
PENDING sync state. The sync engine (running while signed in) pushes PENDING
rows to `users/{uid}/captures` and merges changes from the user's other
devices back into the local database. Deletes are tombstoned (`deleted: true`)
so they propagate; conflicts resolve last-write-wins by `updatedAt`, with an
un-uploaded local edit always winning.

### 4. Semantic search — bring your own Gemini key

Search is **meaning-based** when a Gemini key is active: blurts are embedded
once and ranked by similarity, so "trip to the beach" finds a note that says
"vacation in Goa" even though no word matches. Without a key — or when
offline or rate-limited — search transparently falls back to plain keyword
matching, so search never breaks.

- [ ] Create a **free** API key at <https://aistudio.google.com/apikey> (no
      credit card)
- [ ] In the app: **avatar → AI keys → Gemini — semantic search → Save & Check**

How it works: on the first search, every capture is embedded (batched, 100 per
call) with `gemini-embedding-001` and cached locally in Room — new or edited
blurts are embedded lazily on demand. Vectors are scoped to the signed-in
user, live in the app database, and are dropped on sign-out. The key is stored
**encrypted in the Android Keystore** and never leaves the device — the APK
ships zero secrets. (Optionally restrict the key to this app's package and
SHA-1 in Google Cloud for an extra layer of safety.)

### 4a. Capture analysis — preferred provider (Groq)

Classification (intent + category + time extraction) prefers **Groq** — a much
larger free daily quota (~14,400 requests) and faster responses, which makes
the voice flow feel snappier. Paste a free key from <https://console.groq.com>
(no credit card) in **avatar → AI keys → Groq — classification → Save & Check**.

Groq is the primary analyzer; **Gemini** is the automatic fallback (a Groq
outage or rate limit silently rolls to Gemini, and vice versa). With neither
key active, blurts save unclassified — nothing breaks. Semantic-search
embeddings always stay on Gemini — Groq has no embedding models.

### 4b. Bring your own key (BYOK) — the only key path

Blurt ships **zero keys**: there is no build-time key plumbing at all, so a
distributed APK contains no secret. Until a user brings their own keys in the
app, blurts save unclassified and search falls back to keywords:

- Tap the **avatar** (Home, top right) → **AI keys**.
- **Groq — classification**: paste a free key from <https://console.groq.com>
  and tap **Save & Check** — the app probes the provider live and shows
  *Connected*, *rejected*, or *unreachable*.
- **Gemini — semantic search**: paste a free key from
  <https://aistudio.google.com/apikey> to activate the Gemini classification
  fallback **and** meaning-based search.
- Both keys are stored **encrypted in the Android Keystore** (AES-256-GCM,
  never leave the device) and take effect on the next analysis — no rebuild
  or restart needed. The **Remove** button clears a key from the device and
  restores the unclassified/keyword-search behavior.

### 5. AI capture — categories and reminders

The manual **Text / Idea / Link selector is gone**. Saving a blurt is now a
single action: type anything and tap **Save Blurt**.

**How a blurt is understood**

1. **Link detection (rules, no AI)**: if the content is a URL, it is saved as
   a Link blurt (keeps the link icon in lists) and skips the AI entirely.
2. **Classification (AI)**: everything else is read by the AI, which picks
   an **intent** (Note / Task / Idea / Reminder) and a category from a
   **fixed list** — Work, Personal, Health, Finance, Travel, Ideas, Learning,
   Home, Fitness, Social, Shopping, Other. Neither list ever grows: the AI
   only ever chooses from them, so the Library can show one clean filter chip
   per collection and category (All / Reminders / Tasks / Ideas / Important /
   Archived, plus All / Work / Health / …).

   The classifier prefers **Groq** (fast, and its free tier allows ~14k
   requests/day) and falls back to **Gemini** when no Groq key is set or a
   Groq call fails — see *§4a* below. Both providers receive the same prompt
   and return the same JSON shape.
3. **Time extraction (AI, same call)**: if the blurt mentions a time ("yoga
   class tomorrow at 6pm"), Gemini resolves it to an absolute instant in the
   device's timezone.

**The confirm sheet**

If a time was detected, a sheet slides up before saving — *"Fitness · Remind
me at Aug 13, 2026 · 6:00 PM"* — with **Remind me** / **Just save**. One tap
either way; no time detected means the blurt saves instantly with no sheet.

**The reminder**

- Scheduled as an `RTC_WAKEUP` alarm → a **priority notification** on
  Blurt's own high-importance channel (heads-up banner + sound), containing
  the blurt text.
- Tapping the notification deep-links to that exact blurt.
- Alarms are rescheduled after a reboot (`BOOT_COMPLETED` receiver), and
  cancelled when the blurt is deleted.
- On Android 13+, the first reminder prompts for the notification
  permission; blurts still save (and the reminder still shows in-app) if it
  is denied.

**Offline / no key**

If Gemini is unreachable or no API key is configured, the blurt saves
instantly as uncategorized (no sheet, no reminder) — capture never blocks on
the network, mirroring the search fallback philosophy.

**Categories for existing blurts**

Old blurts are classified lazily in the background (a few per call, same
pattern as embeddings), so the Library's filter chips work across your whole
history without a manual step.

Data-wise this is a Room **v6 migration**: `category` and `reminderAt`
columns travel with the capture through the Realtime Database sync, so
categories and reminders stay consistent across devices.

### 6. Session behavior

- **Persistence**: Firebase restores the signed-in session on launch. The app
  observes the auth state with a live listener, so the login screen never
  flashes while the session is being restored.
- **Logout**: Home header → avatar → **Sign out**. This clears the Firebase
  session and returns to the login screen; the previous user's data cannot be
  reached through the new session (all queries are scoped to the signed-in
  user's UID at the database layer).
- **Legacy data**: blurts created before authentication existed are claimed by
  the first user who signs in on the device, so nothing the user saved is lost.
