import logging
import time
from typing import Dict, Any, List
from fastapi import FastAPI, Form, Request, Response, BackgroundTasks
from fastapi.responses import HTMLResponse, JSONResponse
from twilio.twiml.voice_response import VoiceResponse, Gather

from config import settings
from gemini_agent import gemini_agent
from telegram_notifier import telegram_notifier

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("VoiceServer")

app = FastAPI(
    title="Driving Call Agent - Voice AI Server",
    description="Twilio Voice + Gemini Conversational Agent + Telegram Alert System",
    version="2.0.0"
)

# In-memory call sessions: CallSid -> session state
# { "from": "+1...", "history": [...], "start_time": 12345678, "notified": False }
call_sessions: Dict[str, Dict[str, Any]] = {}

def get_twiml_response(twiml_obj: VoiceResponse) -> Response:
    """Returns XML response required by Twilio."""
    return Response(content=str(twiml_obj), media_type="application/xml")

async def finalize_call_and_alert(call_sid: str):
    """Summarizes conversation and delivers instant Telegram notification."""
    session = call_sessions.get(call_sid)
    if not session:
        return
    if session.get("notified"):
        return

    session["notified"] = True
    caller_number = session.get("from", "Unknown Caller")
    history: List[Dict[str, str]] = session.get("history", [])
    duration_sec = int(time.time() - session.get("start_time", time.time()))

    logger.info(f"Finalizing call {call_sid} from {caller_number}. Duration: {duration_sec}s.")

    # Generate summary with Gemini
    summary_data = gemini_agent.summarize_conversation(history, caller_number)

    # Deliver alert to Telegram
    await telegram_notifier.notify_call_summary(
        caller_number=caller_number,
        summary_data=summary_data,
        history=history,
        duration_sec=duration_sec
    )

@app.get("/", response_class=HTMLResponse)
async def root():
    """Health and status dashboard."""
    gemini_status = "Connected" if settings.GEMINI_API_KEY else "Fallback mode (Add GEMINI_API_KEY in .env)"
    telegram_status = "Configured" if settings.TELEGRAM_BOT_TOKEN and settings.TELEGRAM_CHAT_ID else "Missing Token/Chat ID"
    
    html = f"""
    <!DOCTYPE html>
    <html>
    <head>
        <title>Driving Call Agent — Voice Server</title>
        <style>
            body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 40px; }}
            .card {{ background: #1e293b; border-radius: 12px; padding: 24px; max-width: 650px; margin: 0 auto; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }}
            h1 {{ color: #38bdf8; margin-top: 0; font-size: 24px; }}
            .status-badge {{ display: inline-block; padding: 4px 10px; border-radius: 6px; font-weight: bold; font-size: 13px; }}
            .ok {{ background: #166534; color: #86efac; }}
            .warn {{ background: #854d0e; color: #fef08a; }}
            .info-table {{ width: 100%; border-collapse: collapse; margin-top: 16px; }}
            .info-table td {{ padding: 10px 0; border-bottom: 1px solid #334155; }}
            .info-table td:first-child {{ color: #94a3b8; font-weight: 500; width: 40%; }}
            .code-block {{ background: #090d16; padding: 12px; border-radius: 8px; font-family: monospace; font-size: 13px; color: #a5f3fc; overflow-x: auto; }}
        </style>
    </head>
    <body>
        <div class="card">
            <h1>🚗 Driving Call Agent — Cloud Voice AI Server</h1>
            <p>Your backend is live and ready to receive phone calls forwarded from your phone!</p>
            
            <table class="info-table">
                <tr>
                    <td>Server Status</td>
                    <td><span class="status-badge ok">RUNNING</span></td>
                </tr>
                <tr>
                    <td>Gemini AI Engine</td>
                    <td><span class="status-badge {'ok' if settings.GEMINI_API_KEY else 'warn'}">{gemini_status}</span></td>
                </tr>
                <tr>
                    <td>Telegram Alerts</td>
                    <td><span class="status-badge ok">{telegram_status}</span></td>
                </tr>
                <tr>
                    <td>Active Call Sessions</td>
                    <td><b>{len(call_sessions)}</b></td>
                </tr>
                <tr>
                    <td>Voice Persona</td>
                    <td>{settings.VOICE_NAME} ({settings.USER_NAME}'s Agent)</td>
                </tr>
            </table>

            <h3 style="margin-top: 24px; color: #e2e8f0;">Twilio Webhook URL:</h3>
            <p style="color: #94a3b8; font-size: 14px;">Set this URL in your Twilio Console Phone Number &rarr; "A CALL COMES IN" &rarr; Webhook (HTTP POST):</p>
            <div class="code-block">https://&lt;your-public-url&gt;/voice/incoming</div>

            <h3 style="margin-top: 24px; color: #e2e8f0;">Quick Test:</h3>
            <p style="color: #94a3b8; font-size: 14px;">Forward your phone calls while driving using GSM code:</p>
            <div class="code-block">**61*&lt;TwilioPhoneNumber&gt;*11*05#</div>
        </div>
    </body>
    </html>
    """
    return HTMLResponse(content=html)

@app.post("/voice/incoming")
async def voice_incoming(
    request: Request,
    CallSid: str = Form(...),
    From: str = Form(default="Unknown"),
    To: str = Form(default=""),
    ForwardedFrom: str = Form(default=None)
):
    """
    Entry point when a forwarded call hits the Twilio Phone Number.
    Speaks initial greeting and opens live voice transcription gather.
    """
    caller_id = ForwardedFrom or From
    logger.info(f"Incoming call received: CallSid={CallSid}, Caller={caller_id}")

    # Initialize conversation state
    initial_greeting = f"Hello! {settings.USER_NAME} is currently driving right now and can't pick up. Who is calling, and how can I help you?"

    call_sessions[CallSid] = {
        "from": caller_id,
        "history": [
            {"role": "agent", "content": initial_greeting}
        ],
        "start_time": time.time(),
        "silence_count": 0,
        "notified": False
    }

    twiml = VoiceResponse()
    twiml.say(
        initial_greeting,
        voice=settings.VOICE_NAME,
        language=settings.VOICE_LANGUAGE
    )

    # Listen for caller speech
    gather = Gather(
        input="speech",
        action=f"/voice/turn?call_sid={CallSid}",
        speech_timeout="auto",
        timeout=5,
        method="POST",
        action_on_empty_result=True
    )
    twiml.append(gather)

    # Fallback if gather timeouts completely without hitting turn endpoint
    twiml.say(
        "I didn't hear anything. Please feel free to call back or send an SMS. Goodbye!",
        voice=settings.VOICE_NAME,
        language=settings.VOICE_LANGUAGE
    )
    twiml.hangup()

    return get_twiml_response(twiml)

@app.post("/voice/turn")
async def voice_turn(
    request: Request,
    background_tasks: BackgroundTasks,
    CallSid: str = Form(default=None),
    call_sid: str = None,
    SpeechResult: str = Form(default=""),
    Confidence: float = Form(default=0.0)
):
    """
    Handles each conversational turn as the caller speaks.
    Feeds the caller's speech to Gemini and streams back the spoken response.
    """
    active_sid = call_sid or CallSid
    session = call_sessions.get(active_sid)

    twiml = VoiceResponse()

    if not session:
        logger.warning(f"Session not found for CallSid: {active_sid}")
        twiml.say(f"Thanks for calling {settings.USER_NAME}. Goodbye!", voice=settings.VOICE_NAME)
        twiml.hangup()
        return get_twiml_response(twiml)

    caller_text = SpeechResult.strip()
    logger.info(f"Caller ({session['from']}) said: '{caller_text}' (Confidence: {Confidence})")

    # If nothing was heard (caller stayed silent)
    if not caller_text:
        session["silence_count"] = session.get("silence_count", 0) + 1
        if session["silence_count"] >= 2:
            # Two consecutive silences -> wrap up
            farewell = f"I couldn't hear you clearly, but I've notified {settings.USER_NAME} that you called. Goodbye!"
            session["history"].append({"role": "agent", "content": farewell})
            twiml.say(farewell, voice=settings.VOICE_NAME, language=settings.VOICE_LANGUAGE)
            twiml.hangup()
            background_tasks.add_task(finalize_call_and_alert, active_sid)
            return get_twiml_response(twiml)
        else:
            prompt = "Sorry, I didn't catch that. Could you please repeat who is calling?"
            session["history"].append({"role": "agent", "content": prompt})
            twiml.say(prompt, voice=settings.VOICE_NAME, language=settings.VOICE_LANGUAGE)
            gather = Gather(
                input="speech",
                action=f"/voice/turn?call_sid={active_sid}",
                speech_timeout="auto",
                timeout=5,
                method="POST",
                action_on_empty_result=True
            )
            twiml.append(gather)
            return get_twiml_response(twiml)

    # Reset silence counter since caller spoke
    session["silence_count"] = 0

    # Add caller message to history
    session["history"].append({"role": "caller", "content": caller_text})

    # Generate response from Gemini AI
    agent_reply, should_hang_up = gemini_agent.generate_reply(
        history=session["history"],
        caller_number=session["from"]
    )
    logger.info(f"Agent reply: '{agent_reply}' (HangUp: {should_hang_up})")

    # Record agent reply
    session["history"].append({"role": "agent", "content": agent_reply})

    twiml.say(agent_reply, voice=settings.VOICE_NAME, language=settings.VOICE_LANGUAGE)

    if should_hang_up:
        twiml.hangup()
        background_tasks.add_task(finalize_call_and_alert, active_sid)
    else:
        # Continue the conversation
        gather = Gather(
            input="speech",
            action=f"/voice/turn?call_sid={active_sid}",
            speech_timeout="auto",
            timeout=5,
            method="POST",
            action_on_empty_result=True
        )
        twiml.append(gather)
        # Safety fallback if user goes silent
        twiml.say(f"Thank you. I will notify {settings.USER_NAME} right away. Goodbye!", voice=settings.VOICE_NAME)
        twiml.hangup()
        background_tasks.add_task(finalize_call_and_alert, active_sid)

    return get_twiml_response(twiml)

@app.post("/voice/status")
async def voice_status(
    background_tasks: BackgroundTasks,
    CallSid: str = Form(...),
    CallStatus: str = Form(default="completed")
):
    """
    Twilio Call Status Webhook.
    Triggered when the call ends, is completed, or is disconnected.
    """
    logger.info(f"Call status callback: CallSid={CallSid}, Status={CallStatus}")
    if CallSid in call_sessions:
        background_tasks.add_task(finalize_call_and_alert, CallSid)

    return JSONResponse({"status": "received"})

@app.post("/test/alert")
async def test_alert():
    """Manual test endpoint to verify Telegram alerts."""
    test_history = [
        {"role": "agent", "content": "Hello! Tharun is driving. Who is this?"},
        {"role": "caller", "content": "Hey Tharun, this is Sarah from the engineering team. Just wanted to let you know the client demo is moved to 4 PM today."},
        {"role": "agent", "content": "Got it Sarah, I will notify Tharun right away. Goodbye!"}
    ]
    test_summary = {
        "caller_name": "Sarah (Engineering Team)",
        "summary": "Client demo rescheduled to 4:00 PM today.",
        "urgency": "NORMAL",
        "action_required": "Be prepared for 4 PM client demo"
    }
    success = await telegram_notifier.notify_call_summary(
        caller_number="+91 98765 43210",
        summary_data=test_summary,
        history=test_history,
        duration_sec=32
    )
    return {"success": success, "message": "Check your Telegram bot!"}
