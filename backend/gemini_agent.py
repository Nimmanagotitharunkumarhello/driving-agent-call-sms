import json
import logging
from typing import List, Dict, Tuple
from config import settings

logger = logging.getLogger("GeminiVoiceAgent")

SYSTEM_INSTRUCTION = f"""
You are an intelligent, polite voice call assistant answering on behalf of {settings.USER_NAME}.
{settings.USER_NAME} is currently driving his vehicle and cannot take phone calls safely.

Your goals:
1. Speak in natural, friendly, conversational spoken English.
2. Keep every response SHORT: 1 to 2 sentences maximum! (Remember: this is a live voice call, long paragraphs sound robotic and frustrate the caller).
3. Identify who is calling and what they need.
4. If they give you a message, acknowledge it clearly and reassure them that {settings.USER_NAME} will receive it right away.
5. If it's an emergency, tell them to send an urgent SMS or call emergency services if life-threatening.
6. When the caller says goodbye, confirms that's all, or thanks you, wrap up the call politely and say goodbye.

Output format rule:
Return ONLY the exact words you want to say out loud over the telephone line. Do not include markdown, asterisks, bullet points, stage directions, or emojis.
If you believe the conversation is finished and the call should now be hung up, append the special tag [HANGUP] at the very end of your response.
"""

SUMMARY_PROMPT = f"""
You are reviewing a telephone conversation between an AI Voice Agent and a caller who called while {settings.USER_NAME} was driving.
Analyze the transcript and provide a structured JSON summary with the following keys:
- "caller_name": (string, e.g. "Rahul", "Mom", "Amazon Delivery", or "Unknown")
- "summary": (concise 1-2 sentence summary of what the caller needed)
- "urgency": (one of "NORMAL", "URGENT", "EMERGENCY")
- "action_required": (what {settings.USER_NAME} should do, e.g. "Call back at 6 PM", "No action needed")

Return ONLY valid JSON.
"""

class GeminiVoiceAgent:
    def __init__(self):
        self.api_key = settings.GEMINI_API_KEY
        self.model = settings.GEMINI_MODEL
        self.client = None
        if self.api_key:
            try:
                from google import genai
                self.client = genai.Client(api_key=self.api_key)
                logger.info(f"Gemini client initialized with model: {self.model}")
            except Exception as e:
                logger.error(f"Failed to initialize Gemini client: {e}")

    def generate_reply(self, history: List[Dict[str, str]], caller_number: str) -> Tuple[str, bool]:
        """
        Takes conversation history: [{'role': 'agent'|'caller', 'content': '...'}]
        Returns: (spoken_reply, should_hang_up)
        """
        # If no Gemini key is provided yet, use an intelligent rule-based fallback
        if not self.client:
            return self._fallback_reply(history)

        try:
            # Build conversation turns for Gemini
            formatted_turns = []
            for item in history:
                role = "model" if item["role"] == "agent" else "user"
                formatted_turns.append(f"{role.upper()}: {item['content']}")

            prompt = f"{SYSTEM_INSTRUCTION}\n\nCaller Phone Number: {caller_number}\n\nConversation so far:\n" + "\n".join(formatted_turns) + "\n\nAI ASSISTANT RESPONSE:"

            response = self.client.models.generate_content(
                model=self.model,
                contents=prompt
            )

            reply = response.text.strip() if response and response.text else ""
            should_hang_up = False

            if "[HANGUP]" in reply:
                reply = reply.replace("[HANGUP]", "").strip()
                should_hang_up = True

            # If reply is empty, fallback
            if not reply:
                return "I understand. I will deliver this message to Tharun as soon as he finishes driving. Have a great day!", True

            # Auto-detect common farewell keywords if the caller clearly said bye
            last_caller_msg = history[-1]["content"].lower() if history and history[-1]["role"] == "caller" else ""
            if any(bye_word in last_caller_msg for bye_word in ["bye", "goodbye", "that is all", "that's all", "nothing else", "no that's it"]):
                should_hang_up = True

            return reply, should_hang_up

        except Exception as e:
            logger.error(f"Error calling Gemini API: {e}", exc_info=True)
            return self._fallback_reply(history)

    def _fallback_reply(self, history: List[Dict[str, str]]) -> Tuple[str, bool]:
        """Rule-based smart fallback when Gemini API key is not yet set."""
        turns = [h for h in history if h["role"] == "caller"]
        turn_count = len(turns)
        last_msg = turns[-1]["content"].lower() if turns else ""

        if any(w in last_msg for w in ["bye", "goodbye", "that's all", "that is all", "no that's it", "thanks"]):
            return f"Thank you for calling. I have recorded your message and notified {settings.USER_NAME}. Goodbye!", True

        if turn_count == 1:
            return f"Thanks for telling me. Could you please give me a quick message or let me know if it's urgent for {settings.USER_NAME}?", False
        elif turn_count >= 2:
            return f"Understood. I will pass your message directly to {settings.USER_NAME} as soon as he stops driving. Have a wonderful day!", True
        else:
            return f"Hello! {settings.USER_NAME} is currently driving. Who is calling and how can I help you?", False

    def summarize_conversation(self, history: List[Dict[str, str]], caller_number: str) -> Dict[str, str]:
        """Summarizes the dialogue into structured details for Telegram."""
        if not history:
            return {
                "caller_name": "Unknown",
                "summary": "Caller disconnected without speaking.",
                "urgency": "NORMAL",
                "action_required": f"Call back {caller_number}"
            }

        transcript_text = "\n".join([f"{h['role'].title()}: {h['content']}" for h in history])

        if self.client:
            try:
                prompt = f"{SUMMARY_PROMPT}\n\nCaller Phone: {caller_number}\n\nTranscript:\n{transcript_text}"
                response = self.client.models.generate_content(
                    model=self.model,
                    contents=prompt
                )
                text = response.text.strip()
                # Clean code blocks if present
                if text.startswith("```json"):
                    text = text[7:]
                if text.startswith("```"):
                    text = text[3:]
                if text.endswith("```"):
                    text = text[:-3]
                return json.loads(text.strip())
            except Exception as e:
                logger.error(f"Failed to generate AI summary: {e}")

        # Fallback summary
        caller_notes = " | ".join([h["content"] for h in history if h["role"] == "caller"])
        return {
            "caller_name": "Caller",
            "summary": caller_notes[:200] if caller_notes else "Spoke with AI Assistant",
            "urgency": "URGENT" if any(w in caller_notes.lower() for w in ["urgent", "emergency", "asap", "hospital"]) else "NORMAL",
            "action_required": f"Call back {caller_number}"
        }

gemini_agent = GeminiVoiceAgent()
