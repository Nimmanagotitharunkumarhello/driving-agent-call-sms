# Driving Call Agent — Full Build Prompt Pack

How to use this pack:
- Paste PROMPT 0 first (the master prompt). It sets the agent's role and rules.
- Then do STEPS 1-10 in order. One step per session. Never skip ahead.
- After each step: build the project, verify it works, answer the quiz, THEN continue.
- If you cannot explain what a step did, stop and ask the agent to teach you before moving on.
- The 5 golden rules: (1) never run code you cannot explain, (2) ask for a HINT not a solution when stuck, (3) always ask "why this way", (4) break things on purpose, (5) explain back and make the agent correct you.

---

## PROMPT 0 — THE MASTER PROMPT (paste this first)

```
You are a senior Android developer AND a patient teacher. You will build an
Android app called "Driving Call Agent" WITH me, one step at a time.

WHAT THE APP DOES (final goal, do NOT build it all at once):
When "Driving Mode" is ON, every incoming call is auto-answered after ~2
seconds, a fixed message is spoken to the caller using TextToSpeech
("Hello! The person you are calling is driving right now. He will call you
back in a few minutes. Thank you!"), then the call ends. Every call is saved
in a local Room database and a Telegram message is sent to me using the
Telegram Bot API. The app has one screen: driving mode switch, Telegram bot
token + chat id fields, "send test message" button, call history list with
"Call back" buttons.

TECH STACK (fixed, do not change):
Kotlin, Jetpack Compose (Material3), Room, OkHttp, SharedPreferences,
BroadcastReceiver for PHONE_STATE, Android TextToSpeech.
minSdk 26, targetSdk 34, AGP 8.5.2, Kotlin 2.0.20, Gradle 8.7.
Planned files: CallEventReceiver.kt, CallAudioSpeaker.kt, TelegramSender.kt,
CallLogDb.kt, SettingsStore.kt, MainActivity.kt.

TEACHING RULES (obey strictly):
1. Work ONE STEP at a time. I will say "STEP N:". Never build ahead.
2. Before writing any code for a step, explain in 3-5 simple sentences what
   it does and WHY it is needed. Simple English, no jargon without explaining.
3. Write generous comments in every file, in simple English.
4. Keep code beginner-simple: no Hilt, no ViewModel layers, no interfaces
   unless needed. Readable over clever.
5. After each step, give me 3 quiz questions about the code you just wrote.
   Wait for my answers and grade them before continuing.
6. When I am stuck, give me a HINT first, not the full solution.

Confirm you understood these rules, then wait for my STEP 1 instruction.
```

---

## STEP 1 — Project skeleton
```
STEP 1: Create the Gradle project skeleton only. Files: settings.gradle.kts,
build.gradle.kts (root), gradle.properties, gradle/wrapper/gradle-wrapper.properties,
app/build.gradle.kts (with Compose, Room+KSP, OkHttp dependencies), an
AndroidManifest.xml with ONLY the INTERNET permission, strings.xml, and an
empty MainActivity that shows the text "Driving Call Agent".
Versions: AGP 8.5.2, Kotlin 2.0.20, compose plugin 2.0.20, KSP 2.0.20-1.0.25.
After generating, tell me exactly how to verify Gradle sync succeeds.
```
Verify before next step: project opens in Android Studio, sync passes, app runs and shows the text.
Learn afterward — ask: "Explain what Gradle, AGP and KSP each do, like I'm 10."

## STEP 2 — The notebook (settings)
```
STEP 2: Create SettingsStore.kt — a small SharedPreferences wrapper class
with: isDrivingModeEnabled (Boolean), botToken (String), chatId (String),
and 4 temporary call-state flags: lastCaller, ringingActive, handledThisCall,
spokeThisCall. Explain SharedPreferences vs a database before coding.
```
Verify: compiles. Learn: "When should I use SharedPreferences instead of Room?"

## STEP 3 — The memory (database)
```
STEP 3: Create CallLogDb.kt with Room: @Entity CallRecord (id, callerNumber,
timeEpochMs, status), @CallDao with insert / getAll (returning Flow) /
clearAll, and a singleton @Database. Explain @Entity, @Dao, @Database and
what a Flow is BEFORE writing code.
```
Verify: compiles after Gradle syncs KSP. Learn: "Why is insert suspend and getAll a Flow?"

## STEP 4 — The messenger (make something WORK today)
```
STEP 4: Create TelegramSender.kt — an object with send(context, message,
onResult: (Boolean) -> Unit). It reads token/chatId from SettingsStore and
POSTs JSON {chat_id, text} to https://api.telegram.org/bot<token>/sendMessage
using OkHttp on a background thread. Add a temporary Button to MainActivity
that sends a test message and shows a Toast with the result.
Also tell me: how do I create a bot with @BotFather, get my chat id from
@userinfobot, and why must I press START on my bot once?
```
Verify: you receive a real Telegram message on your phone. This is your first working feature — enjoy it.
Learn: "Explain this file line by line: URL, JSON, POST, response code."

## STEP 5 — The face (full UI)
```
STEP 5: Build the full MainActivity UI in Compose: headline, a Card with the
DRIVING MODE Switch (saves to SettingsStore), two OutlinedTextFields for bot
token and chat id (saving on change), the "Send test message" button (from
step 4), a call history LazyColumn fed by dao.getAll().collectAsState(),
each row showing number, time, status and a "Call back" TextButton that
opens ACTION_DIAL, and a "Clear" TextButton. Ask for the 3 dangerous
permissions on first launch with rememberLauncherForActivityResult.
Explain remember, mutableStateOf and collectAsState before coding.
```
Verify: UI works, toggle persists after killing the app.

## STEP 6 — The door (receiver, answer only)
```
STEP 6: Create CallEventReceiver.kt — a BroadcastReceiver listening to
PHONE_STATE. Register it in the manifest (exported=true) and add the
permissions READ_PHONE_STATE, READ_CALL_LOG, ANSWER_PHONE_CALLS.
For now: if Driving Mode is ON and state is RINGING, save the number,
wait 2 seconds, call TelecomManager.acceptRingingCall(); on OFFHOOK wait 3
seconds and endCall(); save record "ANSWERED_BY_AGENT"; reset flags on IDLE.
Use goAsync() + Handler like a pro. Explain what a BroadcastReceiver is,
who calls onReceive, and why goAsync() exists, before coding.
```
Verify: enable mode, call from second phone — phone answers itself and hangs up after 3s. Magic moment #2.

## STEP 7 — The mouth (TTS)
```
STEP 7: Create CallAudioSpeaker.kt. In OFFHOOK, instead of ending after 3s:
switch AudioManager to MODE_IN_CALL, turn the speakerphone ON, init
TextToSpeech, speak the fixed message on STREAM_VOICE_CALL (Bundle with
KEY_PARAM_STREAM), estimate speaking time (words * 420ms + 2500ms), then
stop/shutdown TTS, restore audio, endCall(). Add a 25-second safety timeout
and an AtomicBoolean finish-once guard. Explain WHY we must estimate the
duration and why speakerphone matters.
```
Verify: call yourself — hear the agent speak. Test how clearly the caller hears it on YOUR phone.

## STEP 8 — Full wiring + missed calls
```
STEP 8: Finish CallEventReceiver logic: in IDLE, if we handled a call but
never spoke (caller hung up early), save "MISSED" and send a Telegram
message. After a successful agent call, send the Telegram summary
(number, time, status). Reset all flags correctly in every path. Walk me
through the full state machine first with a diagram in text.
```
Verify: full end-to-end flow works: call -> agent speaks -> Telegram arrives -> history row appears. Also test missed path.

## STEP 9 — Polish + summary toast
```
STEP 9: When Driving Mode is switched OFF and calls exist, show a Toast:
"N call(s) came while you were driving. Call them back below." Review the
whole app for small bugs (double RINGING events, stale flags). Give me a
checklist of 5 device settings to check (battery optimization on Xiaomi/
Samsung etc.) so Android does not kill my receiver.
```

## STEP 10 — Ship it
```
STEP 10: Guide me to build the APK (Build > Build APKs), install it on my
phone, grant permissions, and run a final test. Then give me 5 things this
MVP cannot do (be honest) and a short preview of how Phase 2 (cloud STT +
LLM + TTS) would replace the fixed message with a real conversation.
```

---

## DEBUG PROMPTS (use when stuck)

```
DEBUG 1 (build fails): Gradle sync/build failed. Here is the full error:
[paste]. First explain in simple words what the error MEANS, then give me
the smallest possible fix. Do not rewrite files I did not ask about.
```
```
DEBUG 2 (answers but silent): The agent answers the call but the caller
hears nothing. Explain the possible Android audio-routing causes (MODE_IN_CALL,
STREAM_VOICE_CALL, speakerphone, OEM differences), ask me 3 diagnostic
questions, then propose ONE fix at a time.
```
```
DEBUG 3 (Telegram silent): The Telegram message never arrives. Walk me
through checking: bot token, chat id, did I press START on the bot, internet
permission, and logcat errors. Give me step-by-step checks, not guesses.
```
```
DEBUG 4 (does not answer on my phone): Auto-answer works on the emulator
but not my real phone (Xiaomi/Samsung/Oppo). Explain OEM background-killer
behavior and battery optimization, then give exact settings paths to fix it.
```
```
DEBUG 5 (stuck in general): I am stuck for 30 minutes. Do NOT give me the
solution. Ask me 3 questions to find where my understanding is wrong, then
give me a HINT only.
```

## LEARNING PROMPTS (after each step)

```
- "Explain the file you just wrote line by line. Do not skip any line."
- "Quiz me with 5 questions on this file, wait for my answers, grade them."
- "I will explain this file in my own words: [your explanation]. Correct me."
- "Show me 3 bugs a careless developer could introduce in this file."
- "What are the limits of this approach, and how do production apps do it?"
- "What happens if I change [X]? Predict before I test."
```

---

## Phase 2 preview prompt (do NOT use until Phase 1 is fully working)

```
PHASE 2 PLANNING: I have a working Phase 1 app (auto-answer + fixed TTS
message + Telegram). Now design the cloud AI upgrade: conditional call
forwarding (GSM codes **61*/**67*) to a Twilio number, a voice AI agent
(STT Deepgram + LLM + TTS ElevenLabs, e.g. using the Pipecat framework),
webhooks, and Telegram updates. Before any code: give me the full
architecture as a diagram in text, the running costs, and a 6-week learning
plan for Python/FastAPI. One concept at a time, same teaching rules.
```
