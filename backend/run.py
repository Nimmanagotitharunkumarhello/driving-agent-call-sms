import uvicorn
from config import settings

if __name__ == "__main__":
    print("=" * 65)
    print("🚗 STARTING DRIVING CALL AGENT — CLOUD VOICE AI SERVER")
    print("=" * 65)
    print(f"👉 Local Dashboard: http://localhost:{settings.PORT}")
    print(f"👉 Gemini Model:    {settings.GEMINI_MODEL}")
    print(f"👉 Telegram Alerts: {settings.TELEGRAM_CHAT_ID}")
    print("-" * 65)
    print("To connect Twilio to your local server, run ngrok in another terminal:")
    print(f"   ngrok http {settings.PORT}")
    print("Then copy your ngrok URL (e.g. https://abc1234.ngrok-free.app) and set:")
    print("   Twilio Console -> Phone Numbers -> Active Numbers -> Configure")
    print("   A CALL COMES IN -> Webhook -> URL: https://<ngrok-url>/voice/incoming")
    print("   HTTP Method: POST")
    print("=" * 65)
    
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=True
    )
