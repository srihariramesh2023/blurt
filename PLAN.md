# Plan: v2, Milestone 2 — Continuous Voice Conversation

**Summary:** Transform Blurt from a one-shot voice-capture app into a continuous voice conversation. The orb stays on screen; a scrollable conversation thread appears below it. Every exchange (user speaks → Blurt replies → blurt saved) adds to the thread. No confirmation screens, no stops — the conversation keeps going until the user ends it.

## Context

**Current architecture (one-shot):**
- HomeScreen: orb → in-place VoiceCaptureFlow (no nav push)
- VoiceViewModel: VoicePhase enum (IDLE → LISTENING → ANALYZING → REPLYING → FOLLOWUP → SAVED, plus ERROR/CONFIRM)
- VoiceCaptureFlow: maps each phase to a full-screen UI state
- After REPLYING → autoSave → SAVED → dismiss → back to IDLE
- One capture per voice interaction. Flow ends. User must tap orb again.

**ChatGPT voice mode (the reference):**
- Voice lives inside the chat window — orb + transcript below
- Conversation persists across turns — no "tap to speak" per turn
- AI responds out loud, transcript shows simultaneously
- You can interrupt mid-sentence
- Long pauses are fine — it just waits
- Background mode: voice keeps working when you switch apps

**What Blurt needs (the gap):**
- Continuous voice — tap once, keep talking, Blurt keeps responding
- Conversation thread — scrollable list of exchanges below the orb
- Blurts appear as cards in the thread — not a separate screen
- No confirmation screen — Blurt auto-saves and shows the saved card
- Back to idle only when user explicitly ends the conversation

## System Impact

- **Source of truth:** unchanged — blurts in Room, keys in Keystore. New transient state: conversation thread (list of turns in ViewModel memory).
- **Data flow:** same pipeline (SpeechRecognizer → Gemini/Groq → Fish TTS), but it loops instead of ending at SAVED.
- **Lifecycle:** IDLE → tap orb → LISTENING → ANALYZING → REPLYING → (auto-save) → LISTENING (loop). User ends → IDLE.
- **UI:** HomeScreen gains a conversation thread below the orb. VoiceCaptureFlow becomes the thread renderer instead of a full-screen phase mapper.
- **Navigation:** unchanged — still in-place on Home, no new routes.
- **Backward compatibility:** existing blurts in Library/Detail are unaffected. The conversation thread is transient ( ViewModel memory ) — but each turn's blurt is persisted to Room immediately.

## Approach

**Extend, don't replace.** The VoiceViewModel already has the full pipeline. We add a conversation list and loop the flow instead of ending at SAVED. The VoiceCaptureFlow becomes a scrollable thread instead of a full-screen phase mapper. The orb stays on top.

**Smallest coherent first step:**
1. Add `ConversationTurn` data class and `_turns: MutableStateFlow<List<ConversationTurn>>` to VoiceViewModel
2. After REPLYING → autoSave → instead of going to SAVED, append the turn to `_turns` and restart LISTENING
3. Replace VoiceCaptureFlow's full-screen phase states with a thread + orb layout
4. Add "End conversation" button (back button or long silence → IDLE)

## Changes (completed)

### `app/src/main/java/com/blurt/app/ui/voice/VoiceViewModel.kt` ✅
- Added `ConversationTurn` data class
- Added `_turns` and `_conversationActive` state flows
- Modified `finishReply()` to loop in conversation mode
- Modified `persist()` to loop in conversation mode
- Modified `finishToSaved()` to loop in conversation mode
- Modified `reset()` to clear conversation state
- Added `endConversation()` method
- Modified `onMicTapped()` to activate conversation mode

### `app/src/main/java/com/blurt/app/ui/voice/VoiceCaptureFlow.kt` ✅
- Added `ConversationThread` and `ConversationTurnCard` composables
- Modified `VoiceCaptureFlow` to show thread when conversation is active
- Scrollable LazyColumn with auto-scroll to latest turn
- User bubbles (right) + Blurt replies (left) + saved blurt cards
- "End conversation" button at bottom

### `app/src/main/java/com/blurt/app/ui/home/HomeScreen.kt` ✅
- When conversation is active, shows smaller orb (100dp) + thread below
- When conversation ends, animates back to idle hero

### `app/src/main/java/com/blurt/app/ui/voice/VoiceScreen.kt`
- Kept one-shot flow for Library's "Blurt something" button (no regression)

### `app/src/main/java/com/blurt/app/ai/AnalysisPrompt.kt`
- TODO: extend prompt to include conversation history + recent blurts for context awareness

### `app/src/main/java/com/blurt/app/ai/FollowUpAnswerParser.kt`
- No changes needed

### `app/src/main/java/com/blurt/app/notifications/ReminderScheduler.kt`
- No changes needed

### `app/src/main/java/com/blurt/app/ui/components/BlurtOrb.kt`
- TODO: add CONVERSATION state or reuse IDLE with mic icon

## Verification (completed)

1. `./gradlew :app:compileDebugKotlin` — compiles ✅
2. `./gradlew :app:testDebugUnitTest` — full suite green ✅
3. `./gradlew :app:assembleDebug` + `adb install` — tap orb → speak → Blurt replies → blurt card appears in thread → speak again → second turn appears ✅
4. Say "done" or tap "End" → conversation ends, back to idle hero ✅
5. Library's "Blurt something" button → still works as one-shot (no regression) ✅

### Still TODO
6. Edge: long silence → auto-end conversation after timeout
7. Edge: error during conversation → show error in thread, conversation continues (or ends if fatal)
8. Edge: app backgrounded → conversation pauses, resumes on return
9. Context awareness: extend AnalysisPrompt to include conversation history + recent blurts

## Context Awareness (remembers everything important)

Blurt's core promise is remembering what matters. In a conversation, this means:

1. **Conversation history as context.** Each new turn sends the recent conversation history to the AI prompt, so Blurt can reference earlier exchanges: "You mentioned Sarah earlier — want me to set a reminder?"
2. **Recent blurts as context.** The AI prompt includes the user's last N blurts (from Room), so Blurt knows what's already saved: "You already have a dentist appointment Tuesday — want to reschedule?"
3. **Dedup.** If the user says something similar to a recent blurt, Blurt catches it: "I think you already saved that — want me to update it?"

This is architecturally clean because the existing `CaptureAnalyzer.analyzeWithReply()` already accepts a text string. We extend it to also accept conversation history + recent blurts. No new AI calls — just richer context in the same call.

## What This Does NOT Cover (future milestones)

- Full-duplex (interrupting Blurt mid-speech) — requires streaming audio models
- Background listening — requires foreground service + continuous audio
- Proactive check-ins ("your meeting is in 30 min") — requires scheduled AI calls
- Conversation persistence across app restarts — each turn's blurt is saved, but the thread itself is transient
