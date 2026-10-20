import httpx
import logging
from datetime import datetime
from typing import List, Dict, Optional
from config import settings

logger = logging.getLogger("TelegramNotifier")

class TelegramNotifier:
    def __init__(self):
        self.bot_token = settings.TELEGRAM_BOT_TOKEN
        self.chat_id = settings.TELEGRAM_CHAT_ID

    async def send_message(self, text: str) -> bool:
        """Sends a markdown formatted message to the user's Telegram."""
        if not self.bot_token or not self.chat_id:
            logger.warning("Telegram Bot Token or Chat ID not configured. Skipping alert.")
            return False

        url = f"https://api.telegram.org/bot{self.bot_token}/sendMessage"
        payload = {
            "chat_id": self.chat_id,
            "text": text,
            "parse_mode": "Markdown"
        }

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                res = await client.post(url, json=payload)
                if res.status_code == 200:
                    logger.info("Telegram notification sent successfully.")
                    return True
                else:
                    logger.error(f"Telegram API error {res.status_code}: {res.text}")
                    # Try sending as plain text without Markdown in case of syntax escapes
                    payload.pop("parse_mode", None)
                    res2 = await client.post(url, json=payload)
                    return res2.status_code == 200
        except Exception as e:
            logger.error(f"Exception sending Telegram message: {e}")
            return False

    async def notify_call_summary(
        self,
        caller_number: str,
        summary_data: Dict[str, str],
        history: List[Dict[str, str]],
        duration_sec: Optional[int] = None
    ) -> bool:
        """Formats and delivers a comprehensive call summary to Telegram."""
        now_str = datetime.now().strftime("%I:%M %p, %d %b %Y")
        
        urgency = summary_data.get("urgency", "NORMAL").upper()
        if urgency == "EMERGENCY":
            urgency_badge = "🚨 *CRITICAL EMERGENCY*"
        elif urgency == "URGENT":
            urgency_badge = "⚠️ *URGENT MESSAGE*"
        else:
            urgency_badge = "🟢 *Normal Call*"

        caller_name = summary_data.get("caller_name", "Unknown Caller")
        summary_text = summary_data.get("summary", "No details provided.")
        action_required = summary_data.get("action_required", f"Call back {caller_number}")

        # Build transcript text
        transcript_lines = []
        for turn in history:
            speaker = "🤖 Agent" if turn["role"] == "agent" else "👤 Caller"
            transcript_lines.append(f"{speaker}: _{turn['content']}_")
        transcript_formatted = "\n".join(transcript_lines) if transcript_lines else "_No spoken transcript._"

        duration_info = f" ({duration_sec}s)" if duration_sec else ""

        message = (
            f"🚗 *Driving Call Agent — Call Completed*{duration_info}\n\n"
            f"📞 *From:* `{caller_number}`\n"
            f"👤 *Caller:* *{caller_name}*\n"
            f"🕒 *Time:* {now_str}\n"
            f"🏷️ *Status:* {urgency_badge}\n\n"
            f"📝 *Summary:*\n{summary_text}\n\n"
            f"🎯 *Suggested Action:*\n{action_required}\n\n"
            f"💬 *Conversation Transcript:*\n"
            f"{transcript_formatted}"
        )

        return await self.send_message(message)

telegram_notifier = TelegramNotifier()
