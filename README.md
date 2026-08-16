# Blurt

> **Blurt listens. Blurt organizes.**

Blurt is a native Android voice assistant that lives life with you — a quiet,
dark-first companion built with Kotlin and Jetpack Compose. Tap the orb and
just talk. Blurt hears you, understands what matters, replies out loud, and
keeps only the things that deserve keeping.

- Say *"meeting with Sarah tomorrow at 3"* and Blurt saves a reminder and
  tells you out loud.
- Vent about your boss and Blurt acknowledges you — then **deliberately
  forgets**. Blurt is the one assistant that forgets on purpose.

## Features

- **Talk to Blurt (companion loop)** — tap the orb, speak, and one AI call
  returns the analysis *and* a natural spoken reply, voiced by Google's own
  TTS via your (free, bring-your-own) Gemini key. **Auto-saves by default**:
  what the assistant decides is worth keeping is saved immediately, reminders
  included — no confirm screen in the way. Say *"don't save this"* (or
  "forget it", "just venting"…) and Blurt acknowledges, saves nothing, and
  drops the transcript entirely.
- **Capture** — voice or text, whichever you reach for. Links are detected by
  rule; everything else is classified by AI into a fixed intent and category
  list, and mentioned times become reminders.
- **Smart classification** — a single call extracts intent (Note / Task /
  Idea / Reminder), category (Work, Personal, Health, Finance, Travel,
  Ideas, Learning, Home, Fitness, Social, Shopping, Other), and any time
  mentioned. Prefers **Groq** (fast, ~14k free requests/day) and silently
  falls back to **Gemini**. With no key, blurts save unclassified — capture
  never blocks.
- **Reminders** — blurts that mention a time become **priority Blurt
  notifications** (heads-up banner, own high-importance channel, optional
  daily/weekly recurrence). Tapping the notification deep-links to the
  exact blurt. Alarms survive reboots and are cancelled on delete.
- **Library** — every blurt, newest first, with clean filter chips per
  collection (All / Reminders / Tasks / Ideas / Important / Archived) and
  category.
- **Semantic search** — meaning-based search via Gemini's free embedding
  tier: *"trip to the beach"* finds a note about *"vacation in Goa"* even
  though no word matches. Live keyword search is the always-on fallback.
- **Bring your own key (BYOK) privacy** — the APK ships **zero secrets**.
  Keys are pasted in-app, stored **encrypted in the Android Keystore**
  (AES-256-GCM), and never leave the device.
- **Google Sign-In** — Firebase Auth sessions persist between launches; the
  login screen never flashes on restore.
- **Data isolation** — every blurt is owned by the signed-in user's UID and
  scoped at the database layer; one user can never read another's data.
- **Cross-device sync** — Firebase Realtime Database (free Spark plan, no
  billing). Deletes are tombstoned so a local delete always wins over a
  stale remote copy; conflicts resolve last-write-wins.

## How it works

- **UI** — Jetpack Compose, dark-first, Apple-inspired system design. A
  violet **orb** is the heart of the product with four states: idle,
  listening, processing, complete.
- **Local data** — Room database on-device. Blurts, embeddings, and sync
  state all live locally; the app works fully offline.
- **AI layer** — a pluggable analyzer chain (Groq preferred → Gemini
  fallback) classifies blurts; Gemini embeddings power semantic search;
  Gemini TTS (with device TTS fallback) voices the companion reply. Every
  provider is brought by the user.
- **Sync** — a sync engine pushes pending blurts to `users/{uid}/captures`
  and merges other devices' changes back in, resolving conflicts by
  `updatedAt` with un-uploaded local edits always winning.

## Requirements

- JDK 17
- Android SDK (platform 35, build-tools 35.0.0). The build locates it via
  `local.properties` (`sdk.dir=...`) or the `ANDROID_HOME` environment
  variable.

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run the unit tests (includes repository, data-isolation, migration, analyzer,
parser, and sync tests):

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

## License

Released under the [MIT License](LICENSE).

## Setup — provider-side

The app builds and CI is green **without** any Firebase configuration. To
turn on real sign-in, sync, and reminders you complete a one-time Firebase
setup; the app detects the missing config and shows a friendly "sign-in isn't
set up" message until then.

### 1. Firebase project + Google sign-in

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

### 3. Cross-device sync — checklist

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
so they propagate — and a local delete always beats a stale remote copy.
Conflicts resolve last-write-wins by `updatedAt`, with an un-uploaded local
edit always winning.

### 4. Bring your own key (BYOK) — the only key path

Blurt ships **zero keys**: there is no build-time key plumbing at all, so a
distributed APK contains no secret. Until a user brings their own keys in the
app, blurts save unclassified and search falls back to keywords:

- **First install**: the onboarding flow includes a **Bring your own keys**
  screen (shown once, after the value pages, before the mic permission) —
  paste keys there or skip and continue.
- Any time after: tap the **avatar** (Home, top right) → **AI keys**.
- **Groq — classification**: paste a free key from <https://console.groq.com>
  (no credit card) and tap **Save & Check** — the app probes the provider
  live and shows *Connected*, *rejected*, or *unreachable*. Groq's much
  larger free quota (~14,400 requests/day) and fast responses are why it is
  the preferred classifier.
- **Gemini — semantic search**: paste a free key from
  <https://aistudio.google.com/apikey> (no credit card) to activate the
  Gemini classification fallback **and** meaning-based search **and** the
  natural TTS voice. Keys come in two formats — the classic `AIza…` traffic
  key and the newer `AQ.…` authentication key Google now issues by default;
  both work, since Blurt calls Gemini's native API.
- Both keys are stored **encrypted in the Android Keystore** (AES-256-GCM,
  never leave the device) and take effect on the next analysis — no rebuild
  or restart needed. The **Remove** button clears a key from the device and
  restores the unclassified/keyword-search behavior.

**Semantic search, in detail** — when a Gemini key is active, blurts are
embedded once (`gemini-embedding-001`, batched 100 per call) and cached
locally in Room; new or edited blurts are embedded lazily on demand. Vectors
are scoped to the signed-in user, live in the app database, and are dropped on
sign-out. (Optionally restrict the key to this app's package and SHA-1 in
Google Cloud for an extra layer of safety.)

### 5. Talk to Blurt — the companion reply (v1)

Voice capture is a conversation. When a Groq key is active, the classifier
returns a short **spoken reply** along with the analysis — the assistant
acknowledges what you said and tells you what it did — voiced with **Google's
own TTS voice** via your saved Gemini key (free tier, natural voice, the same
family as ChatGPT voice mode). With no Gemini key, or offline, it falls back
to the device's built-in TTS so the reply is still spoken.

- **Auto-save by default**: whatever the assistant decides is worth keeping is
  saved immediately, reminders included — no confirm screen in the way.
- **Say "don't save this"** (or "forget it", "just venting"…) and Blurt
  acknowledges, saves nothing, and **drops the transcript entirely** — the
  one assistant that forgets on purpose.
- **Gemini-only / no key**: no reply is spoken and the classic review screen
  (Save Blurt / Edit) still runs, so nothing silently changes for keyless
  users.
- A tap anywhere on the reply skips the utterance and moves on.

### 6. AI capture — categories and reminders

Saving a blurt is a single action: type (or speak) anything and tap **Save
Blurt** (or let the voice loop save it for you).

**How a blurt is understood**

1. **Link detection (rules, no AI)**: if the content is a URL, it is saved as
   a Link blurt (keeps the link icon in lists) and skips the AI entirely.
2. **Classification (AI, one call)**: the AI picks an **intent** (Note /
   Task / Idea / Reminder) and a category from a **fixed list** — Work,
   Personal, Health, Finance, Travel, Ideas, Learning, Home, Fitness, Social,
   Shopping, Other. Neither list ever grows: the AI only ever chooses from
   them, so the Library can show one clean filter chip per collection and
   category. The classifier prefers **Groq** and falls back to **Gemini**;
   both providers receive the same prompt and return the same JSON shape.
3. **Time extraction (AI, same call)**: if the blurt mentions a time ("yoga
   class tomorrow at 6pm"), it is resolved to an absolute instant in the
   device's timezone.

**The confirm sheet**

In typed capture (and for keyless users), if a time was detected, a sheet
slides up before saving — *"Fitness · Remind me at Aug 13, 2026 · 6:00 PM"* —
with **Remind me** / **Just save**. One tap either way; no time detected
means the blurt saves instantly with no sheet. In the voice companion loop,
saving is automatic.

**The reminder**

- Scheduled as an `RTC_WAKEUP` alarm → a **priority notification** on
  Blurt's own high-importance channel (heads-up banner + sound), containing
  the blurt text. Optionally recurring daily or weekly.
- Tapping the notification deep-links to that exact blurt.
- Alarms are rescheduled after a reboot (`BOOT_COMPLETED` receiver), and
  cancelled when the blurt is deleted.
- On Android 13+, the first reminder prompts for the notification
  permission; blurts still save (and the reminder still shows in-app) if it
  is denied.

**Offline / no key**

If the analyzer is unreachable or no API key is configured, the blurt saves
instantly as uncategorized (no sheet, no reminder) — capture never blocks on
the network, mirroring the search fallback philosophy.

**Categories for existing blurts**

Old blurts are classified lazily in the background (a few per call, same
pattern as embeddings), so the Library's filter chips work across your whole
history without a manual step.

Data-wise this is a Room **v6 migration**: `category` and `reminderAt`
columns travel with the capture through the Realtime Database sync, so
categories and reminders stay consistent across devices.

### 7. Session behavior

- **Persistence**: Firebase restores the signed-in session on launch. The app
  observes the auth state with a live listener, so the login screen never
  flashes while the session is being restored.
- **Logout**: Home header → avatar → **Sign out**. This clears the Firebase
  session and returns to the login screen; the previous user's data cannot be
  reached through the new session (all queries are scoped to the signed-in
  user's UID at the database layer).
- **Legacy data**: blurts created before authentication existed are claimed by
  the first user who signs in on the device, so nothing the user saved is lost.
