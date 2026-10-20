# Driving Call Agent ("Java") 🚗🤖

An intelligent, hands-free Android and Cloud AI call handling assistant designed for drivers. When you're behind the wheel, "Java" attends incoming calls, interacts with callers, analyzes the urgency of messages with Google Gemini, and instantly notifies you on Telegram.

---

## 🌟 Key Features

1. **Driving Mode Detection:**
   - One-tap Driving Mode toggle or automatic motion/car connection.
   - Detects incoming cellular calls and manages call triage without driver distraction.

2. **1-Tap WebRTC Live Voice Bridge (Gemini Live 2.0):**
   - When a call arrives, an automated SMS invites the caller to speak directly to **Java** with two-way voice via a 1-tap browser link.
   - Powered by **LiveKit Agents** and **Google Gemini 2.0 Flash (Multimodal Live)**.

3. **Intelligent SMS Fallback & Urgency Analysis:**
   - If the caller replies via SMS, the message is sent directly to **Gemini API**.
   - Gemini classifies urgency into `CRITICAL`, `URGENT`, or `NORMAL`.
   - Sends a confirmation SMS back to the caller acknowledging receipt.

4. **Instant Telegram Alerts:**
   - Rich notifications dispatched immediately to your Telegram bot.
   - Includes caller number, timestamp, urgency level, concise summary, and verbatim transcript/message.

---

## 🏗️ Architecture

```
Incoming Call (While Driving)
         │
         ├──► Call Declined
         │
         └──► Auto-SMS sent to Caller:
              "Hi, I'm Java, Tharun's AI assistant. Tharun is driving.
               Tap this link to speak to me directly: [WebRTC Link]
               Or reply to this SMS with your urgent message."
                      │
         ┌────────────┴────────────────────────┐
         │                                     │
    [Taps Voice Link]                     [Replies via SMS]
         │                                     │
   LiveKit WebRTC Room                  Android BroadcastReceiver
         │                                     │
   Gemini Live 2.0 Agent                 Gemini API Analysis
   (Two-Way Realtime Voice)              (Urgency Classification)
         │                                     │
         └─────────────┬───────────────────────┘
                       │
             Instant Telegram Alert
             (Urgency + Summary + Action)
```

---

## 📱 Tech Stack

- **Android App:**
  - Modern Kotlin, Jetpack Compose, Material 3
  - TelephonyManager, BroadcastReceiver, TelecomManager APIs
  - OkHttp for lightweight Gemini API and Telegram Bot calls
- **Backend Voice Agent:**
  - Python 3.11+
  - LiveKit Agents SDK (`livekit-agents`, `livekit-plugins-google`)
  - Google Gemini Live 2.0 (`gemini-2.0-flash-exp`)
  - Telegram Bot API for real-time driver alerts

---

## 🚀 Setup & Installation

### Android App
1. Open the project in Android Studio.
2. Build and install on your device:
   ```bash
   ./gradlew installDebug
   ```
3. Grant required permissions (Phone, SMS, Call Log).
4. Enter your Gemini API key and Telegram Bot credentials in Settings.

### Python Voice Agent Backend
1. Create and activate a virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: .\venv\Scripts\activate
   ```
2. Install dependencies:
   ```bash
   pip install livekit-agents livekit-plugins-google google-genai python-dotenv
   ```
3. Configure `backend/.env` (see `backend/.env.example`).
4. Run the development worker:
   ```bash
   python backend/livekit_agent.py dev
   ```

---

## 📄 License
MIT License
