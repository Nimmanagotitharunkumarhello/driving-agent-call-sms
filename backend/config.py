import os
from pathlib import Path
from dotenv import load_dotenv

# Load .env file
env_path = Path(__file__).resolve().parent / ".env"
if env_path.exists():
    load_dotenv(dotenv_path=env_path)
else:
    load_dotenv()

class Settings:
    # Google Gemini AI Settings
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "").strip()
    GEMINI_MODEL: str = os.getenv("GEMINI_MODEL", "gemini-2.5-flash").strip()

    # LiveKit Cloud Telephony Settings
    LIVEKIT_URL: str = os.getenv("LIVEKIT_URL", "").strip()
    LIVEKIT_API_KEY: str = os.getenv("LIVEKIT_API_KEY", "").strip()
    LIVEKIT_API_SECRET: str = os.getenv("LIVEKIT_API_SECRET", "").strip()
    LIVEKIT_PHONE_NUMBER: str = os.getenv("LIVEKIT_PHONE_NUMBER", "+14843989029").strip()

    # Telegram Bot Settings
    TELEGRAM_BOT_TOKEN: str = os.getenv("TELEGRAM_BOT_TOKEN", "").strip()
    TELEGRAM_CHAT_ID: str = os.getenv("TELEGRAM_CHAT_ID", "").strip()

    # Agent Persona Settings
    USER_NAME: str = os.getenv("USER_NAME", "Tharun").strip()
    VOICE_NAME: str = os.getenv("VOICE_NAME", "Puck").strip()
    VOICE_LANGUAGE: str = os.getenv("VOICE_LANGUAGE", "en-US").strip()

    # Server Settings
    PORT: int = int(os.getenv("PORT", "8000"))
    HOST: str = os.getenv("HOST", "0.0.0.0")

settings = Settings()
