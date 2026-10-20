# 🚗 Driving Call Agent — Cloud Voice AI Setup Guide

## Why the Caller Heard Silence Before (The Hardware Barrier)
On modern Android phones (especially OnePlus/Oppo with dual-microphone noise cancellation):
1. **Baseband Modem Isolation:** Android strictly prevents third-party apps from injecting synthesized digital audio directly into an ongoing cellular telephone call uplink.
2. **Hardware Acoustic Echo Cancellation (AEC):** Playing the voice loudly out of the phone's speakerphone causes the phone's dedicated hardware noise-cancellation DSP chip to identify it as speaker echo and **actively cancel it out** from the microphone feed.
- **Result:** You hear the voice clearly in your car, but the caller hears 100% silence.

---

## The Working Solution: Carrier Forwarding + Cloud Voice AI
By routing unanswered calls to a Cloud Voice AI Agent via standard carrier forwarding:
1. **The caller hears the AI speak directly over the telephone line** with crystal-clear audio and zero cancellation.
2. **Google Gemini AI** listens to what the caller says, understands their intent, answers their questions politely, and asks for a message.
3. When the call ends, **an instant Telegram alert** is delivered directly to your phone containing the caller's identity, message summary, urgency rating, and full conversation transcript!

---

## 3 Quick Steps to Run the Voice AI Agent

### Step 1: Get Your Free API Keys

1. **Google Gemini API Key (FREE):**
   - Go to [Google AI Studio](https://aistudio.google.com/app/apikey).
   - Click **Create API Key**.
   - Copy the key and paste it into `backend/.env`:
     ```env
     GEMINI_API_KEY=your_gemini_api_key_here
     ```

2. **Telegram Bot (Already Set Up):**
   - Your bot token and chat ID are already pre-configured in `backend/.env`!
   - You can test it anytime:
     ```bash
     .\venv\Scripts\python.exe -c "import asyncio; from telegram_notifier import telegram_notifier; asyncio.run(telegram_notifier.send_message('🚗 Test from Voice Agent!'))"
     ```

3. **Twilio Virtual Phone Number:**
   - Sign up at [Twilio](https://www.twilio.com) (comes with **$15 free trial balance**).
   - In the Twilio Console, go to **Phone Numbers &rarr; Manage &rarr; Buy a number** (choose any number that supports Voice).
   - Copy your Twilio number (e.g. `+12025550198`).

---

### Step 2: Start the Cloud Voice AI Server

1. Open a terminal in `d:/driver call agent/backend`:
   ```powershell
   ..\venv\Scripts\python.exe run.py
   ```
   Your server will start at `http://localhost:8000`.

2. To let Twilio communicate with your local machine, open another terminal and run **ngrok** (or localtunnel):
   ```bash
   ngrok http 8000
   ```
   Copy the `https://` forwarding URL provided by ngrok (e.g., `https://abc-123.ngrok-free.app`).

3. In your **Twilio Console**:
   - Go to **Phone Numbers &rarr; Active Numbers &rarr; click your number**.
   - Under **Voice Configuration** &rarr; **A CALL COMES IN**:
     - Select: **Webhook**
     - URL: `https://<your-ngrok-url>/voice/incoming`
     - HTTP: **HTTP POST**
   - Click **Save configuration**.

---

### Step 3: Connect Your Phone (One-Tap in App)

1. Open the updated **Driving Call Agent** Android app.
2. In the new **Cloud Voice AI Agent** card:
   - Enter your **Twilio Phone Number**.
   - Tap **Forward Calls**.
   - The system dialer will open with `**61*<TwilioNumber>*11*05#`. Press the **Call button**.
   - Your carrier will activate conditional call forwarding: if someone calls while you're driving and you don't answer within 5 seconds, the call automatically transfers to your AI Agent!
3. When you are done driving:
   - Tap **Cancel Forwarding** (dials `##61#`) and press Call to return your phone to normal.
