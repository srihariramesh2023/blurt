# Blurt

Blurt is a native Android application built with Kotlin and Jetpack Compose. It
is a quiet home for the things on your mind — text, ideas, links, and images —
with a dark-first, Apple-inspired design language.

## Features

- **Capture**: blurts of four types — Text, Idea, Link, Image — with a quick
  capture surface on Home.
- **Library**: every blurt, newest first.
- **Search**: live local keyword search across your blurts.
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

Images: the app also tries Cloud Storage for image sync (best effort — Storage
may require billing on some projects). If Storage is unavailable, capture
metadata still syncs and photos stay on the device that took them; nothing
blocks. If you do enable Cloud Storage, publish the rules in `storage.rules`
on its Rules tab.

How sync works: every capture is created with a device-generated UUID id and a
PENDING sync state. The sync engine (running while signed in) pushes PENDING
rows to `users/{uid}/captures`, uploads images to `users/{uid}/images` when
Storage is available, and merges changes from the user's other devices back
into the local database. Deletes are tombstoned (`deleted: true`) so they
propagate; conflicts resolve last-write-wins by `updatedAt`, with an
un-uploaded local edit always winning.

### 4. Session behavior

- **Persistence**: Firebase restores the signed-in session on launch. The app
  observes the auth state with a live listener, so the login screen never
  flashes while the session is being restored.
- **Logout**: Home header → avatar → **Sign out**. This clears the Firebase
  session and returns to the login screen; the previous user's data cannot be
  reached through the new session (all queries are scoped to the signed-in
  user's UID at the database layer).
- **Legacy data**: blurts created before authentication existed are claimed by
  the first user who signs in on the device, so nothing the user saved is lost.
