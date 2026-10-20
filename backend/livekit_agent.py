import asyncio
import logging
from typing import List, Dict
from dotenv import load_dotenv

from livekit import agents
from livekit.agents import JobContext, WorkerOptions, cli, Agent, AgentSession
from livekit.plugins import google, silero

from config import settings
from gemini_agent import gemini_agent
from telegram_notifier import telegram_notifier

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("LiveKitVoiceAgent")

SYSTEM_INSTRUCTION = f"""
You are Java, an intelligent, respectful AI phone agent answering calls for your sir, {settings.USER_NAME}.
{settings.USER_NAME} is currently driving his car and cannot attend phone calls safely.

Rules:
1. Introduce yourself: "Hi, I am Java, an AI agent for Tharun."
2. Explain: "Sir cannot attend the call right now because he is driving. When he parks and finishes driving, he will call you back."
3. Ask: "If you have any queries or an urgent message, please let me know and I will notify sir immediately."
4. Keep spoken responses short (1-2 sentences maximum).
5. If they give a message, acknowledge it respectfully: "Got it, I have recorded your message and notified sir."
6. Say goodbye and wrap up politely when they are done.
"""

async def entrypoint(ctx: JobContext):
    logger.info(f"Connecting to room: {ctx.room.name}")
    await ctx.connect()

    # Conversation history tracking for Telegram summary
    history: List[Dict[str, str]] = []
    caller_identity = "Unknown Caller"

    # Check caller identity from participant metadata or SIP trunk
    for participant in ctx.room.remote_participants.values():
        if participant.identity:
            caller_identity = participant.identity
            break

    initial_greeting = f"Hi! I am Java, an AI agent for {settings.USER_NAME}. Sir cannot attend the call right now because he is driving his car. When he parks, he will call you back. If you have any queries or messages, please tell me and I will notify sir immediately."
    history.append({"role": "agent", "content": initial_greeting})


    try:
        # Use Google Gemini Live Realtime Model
        model = google.beta.realtime.RealtimeModel(
            model="gemini-2.0-flash-exp",
            voice="Puck",
            api_key=settings.GEMINI_API_KEY
        )

        session = AgentSession(llm=model)
        assistant = Agent(instructions=SYSTEM_INSTRUCTION)

        # Send initial spoken greeting
        await session.start(ctx.room, assistant)
        logger.info(f"Agent session started with {caller_identity}")

    except Exception as e:
        logger.error(f"Error starting LiveKit Gemini Realtime session: {e}", exc_info=True)

    # Wait until the call completes / participant leaves
    @ctx.room.on("participant_disconnected")
    def on_participant_disconnected(participant):
        logger.info(f"Caller {participant.identity} disconnected.")

    # Cleanup and send Telegram notification when session finishes
    async def finalize_alert():
        summary_data = gemini_agent.summarize_conversation(history, caller_identity)
        await telegram_notifier.notify_call_summary(
            caller_number=caller_identity,
            summary_data=summary_data,
            history=history
        )
        logger.info(f"Sent Telegram call summary for {caller_identity}")

    ctx.add_shutdown_callback(finalize_alert)

if __name__ == "__main__":
    if not settings.LIVEKIT_URL:
        print("\n" + "=" * 65)
        print("❌ LIVEKIT_URL is missing in backend/.env!")
        print("Please open backend/.env and paste your 3 keys from cloud.livekit.io:")
        print("   LIVEKIT_URL=wss://javis-new-xxxx.livekit.cloud")
        print("   LIVEKIT_API_KEY=APIxxxx")
        print("   LIVEKIT_API_SECRET=xxxx")
        print("=" * 65 + "\n")
        exit(1)

    cli.run_app(
        WorkerOptions(
            entrypoint_fnc=entrypoint,
            ws_url=settings.LIVEKIT_URL,
            api_key=settings.LIVEKIT_API_KEY,
            api_secret=settings.LIVEKIT_API_SECRET
        )
    )

