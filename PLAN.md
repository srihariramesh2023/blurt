# Plan: v2, Milestone 1 — the follow-up question ("Want a heads-up before?")

**Summary:** Turn the one-shot companion loop into a *two-turn conversation*. After Blurt auto-saves a blurt that carries a reminder and speaks its reply, it asks one follow-up — **"Want me to remind you 15 minutes before?"** — listens for the answer, and when the user says yes, schedules a separate **heads-up alarm** a few minutes before the real reminder. This is the first concrete step toward the ChatGPT-voice-mode feel: Blurt keeps the conversation going until the user ends it.

## Context

- Voice flow: `SpeechRecognizer` → `VoiceViewModel` (`VoicePhase`: IDLE → LISTENING → ANALYZING → REPLYING → SAVED, plus ERROR/CONFIRM fallbacks) → `VoiceCaptureFlow` UI.
- Companion mode: one Groq call returns `reply` + `save` + `analyses`; `finishReply()` auto-saves the blurts (or drops the transcript), then SAVED. TTS (`BlurtTts`) drives the transitions.
- Reminders: `ReminderScheduler.schedule(captureId, content, atMillis)` arms a one-shot alarm; `BlurtReminderReceiver` posts the priority notification with Snooze/Done actions. Request code = captureId; auto-delete uses `captureId + 3_000_000`.
- `persist()` (in the ViewModel) already records `firstReminderAt` + the capture id of a saved reminder — exactly what the heads-up needs.
- The answer ("yes"/"no") is parsed **deterministically** — no extra AI call, no quota, instant, unit-testable. This is deliberate: the follow-up must never cost a second API round-trip.

## System Impact

- **Source of truth**: unchanged — blurts + keys. New transient state: `VoicePhase.FOLLOWUP` and a pending heads-up (captureId + reminder time + content).
- **Lifecycle**: REPLYING → (reply spoken) → if a reminder was saved → FOLLOWUP: speak the question, start listening → answer parsed → yes: `scheduleHeadsUp` + short spoken confirm → SAVED; no: SAVED. Recognizer error during FOLLOWUP → SAVED (never stall). Cancel during FOLLOWUP → reset (blurt is already saved; only the heads-up is dropped).
- **Alarms**: a heads-up is a **separate alarm** with its own request-code offset so it never collides with the reminder or the auto-delete. It posts a plain nudge notification (no Snooze/Done — the real reminder fires later with full actions).
- **Failure modes**: TTS missing → question still shows on screen, flow advances on a short delay. Answer unparseable → treat as "no" (safe default). Notification permission already handled by the reminder path.
- **Quota**: one extra TTS utterance per reminder save (the question). No extra AI calls.

## Approach

Deterministic two-turn loop, additive and quiet: the AI reply is untouched; the ViewModel appends the follow-up question only when the auto-save actually scheduled a reminder. Answer parsing is a pure, unit-tested function. The heads-up is a lightweight second alarm + a simple notification.

## Changes

- `PLAN.md` — this plan (overwrites the completed v1 plan).
- `app/src/main/java/com/blurt/app/ai/FollowUpAnswerParser.kt` — **new**: `parse(text): Boolean` — yes-phrases (yes/yeah/sure/ok/please/go ahead/do it…) → true; no-phrases (no/nah/nope/don't/skip/not needed…) → false; anything else → false (safe default). Pure + tested.
- `app/src/main/java/com/blurt/app/notifications/ReminderScheduler.kt` — add `scheduleHeadsUp(captureId, content, atMillis)` and `cancelHeadsUp(captureId)`, request code `captureId + HEADS_UP_OFFSET`.
- `app/src/main/java/com/blurt/app/notifications/BlurtReminderReceiver.kt` — handle `ACTION_HEADS_UP`: post a plain nudge notification (title "Coming up soon", content, tap opens the blurt), no Snooze/Done actions.
- `app/src/main/java/com/blurt/app/ui/voice/VoiceViewModel.kt` — add `VoicePhase.FOLLOWUP`; after the reply finishes and a reminder was saved, speak the question and `startFollowUpListening()` (transcript resets, phase stays FOLLOWUP); on speech end in FOLLOWUP parse the answer → yes: `scheduleHeadsUp` + speak a short confirm → SAVED; no: SAVED; recognizer error → SAVED; `cancel()` also handles FOLLOWUP.
- `app/src/main/java/com/blurt/app/ui/voice/VoiceCaptureFlow.kt` — map `FOLLOWUP` → `ListeningState` with an optional prompt line showing the question.
- `app/src/main/java/com/blurt/app/ui/voice/VoiceScreen.kt` — back button cancels during FOLLOWUP too.
- `app/src/test/java/com/blurt/app/ai/FollowUpAnswerParserTest.kt` — **new**: yes/no/ambiguous/case-insensitive/phrase coverage.
- `README.md` + `design/BLURT-DESIGN-STANDARD.md` — one line on the follow-up.

## Verification

1. `./gradlew :app:compileDebugKotlin` — compiles.
2. `./gradlew :app:testDebugUnitTest` — parser tests green, full suite green.
3. `./gradlew :app:assembleDebug` + `adb install` (if phone attached) — launch, speak "meeting with Sarah tomorrow at 3" → reply spoken → "Want me to remind you 15 minutes before?" → say "yes" → confirmation + SAVED, heads-up alarm armed (logcat `ACTION_HEADS_UP`).
4. Same capture, answer "no" → SAVED, no heads-up alarm.
5. Capture without a reminder ("buy milk") → no follow-up question at all (loop stays one-turn).
6. Edge: say nothing during FOLLOWUP → times out to SAVED; garbage answer → treated as no.
