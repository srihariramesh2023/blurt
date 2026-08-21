# Blurt

> **Blurt listens and keeps things organized.**

Blurt is a native Android voice assistant built with Kotlin and Jetpack Compose.
Tap the orb and talk. It can respond out loud, save useful information, and
leave out things you don't want to keep.

- Say *"meeting with Sarah tomorrow at 3"* and Blurt saves a reminder and tells you out loud.
- Vent about your boss and Blurt can acknowledge it without saving the conversation. If you say to forget it, the transcript is removed.

## Features

- **Talk to Blurt (companion loop)** — tap the orb and speak. One AI call returns the analysis and a spoken reply. The reply uses Google's TTS through the user's Gemini key. By default, items that the assistant decides to keep are saved immediately, including reminders. There is no confirmation screen. Say *"don't save this"* (or *"forget it"*, *"just venting"*…) and Blurt acknowledges it, saves nothing, and removes the transcript. When a reminder is saved, Blurt can ask *"Want me to remind you 15 minutes before?"*. If the answer is yes, it creates the extra notification locally without another AI call.
- **Capture** — voice or text, whichever you reach for. Links are detected by rule; everything else is classified by AI into a fixed intent and category list, and mentioned times become reminders.
- **Smart classification** — one AI call extracts the intent (Note / Task / Idea / Reminder) and category (Work, Personal, Health, Finance, Travel, Ideas, Learning, Home, Fitness, Social, Shopping, Other), along with any mentioned time. Blurt uses **Groq** first and falls back to **Gemini** if needed. With no key, blurts save unclassified.
- **Reminders** — blurts that mention a time become **priority Blurt notifications** (heads-up banner, own high-importance channel, optional daily/weekly recurrence). Tapping the notification opens the exact blurt. Alarms survive reboots and are cancelled on delete.
- **Library** — blurts are shown newest first, with filter chips for each collection (All / Reminders / Tasks / Ideas / Important / Archived) and category.
- **Semantic search** — searches by meaning using Gemini's free embedding tier: *"trip to the beach"* can find a note about *"vacation in Goa"* even when the words do not match. Live keyword search is the fallback.
- **Bring your own key (BYOK) privacy** — the APK contains **no API keys**. Keys are pasted in-app, stored **encrypted in the Android Keystore** (AES-256-GCM), and never leave the device.
- **Google Sign-In** — Firebase Auth sessions persist between launches; the login screen does not flash during session restoration.
- **Data isolation** — every blurt is owned by the signed-in user's UID and scoped at the database layer; one user cannot read another user's data.
- **Cross-device sync** — Firebase Realtime Database is used on the free Spark plan. Deletes are tombstoned so a local delete wins over a stale remote copy; conflicts resolve last-write-wins.

## How it works

- **UI** — Jetpack Compose with a dark interface. The main control is a violet **orb** with four states: idle, listening, processing, complete.
- **Local data** — Room database on-device. Blurts, embeddings, and sync state all live locally; the app works fully offline.
- **AI layer** — a pluggable analyzer chain (Groq preferred → Gemini fallback) classifies blurts; Gemini embeddings power semantic search; Gemini TTS (with device TTS fallback) voices the companion reply. Every provider is brought by the user.
- **Sync** — the sync engine pushes pending blurts to `users/{uid}/captures` and merges other devices' changes back in, resolving conflicts by `updatedAt` with un-uploaded local edits always winning.

## Requirements

- JDK 17
- Android SDK (platform 35, build-tools 35.0.0). The build locates it via `local.properties` (`sdk.dir=...`) or the `ANDROID_HOME` environment variable.

## Build

```bash
./gradlew assembleDebug
