package com.example.drivingcallagent

import android.content.Context
import android.content.SharedPreferences

/**
 * SettingsStore is a simple helper class that wraps Android's SharedPreferences.
 *
 * It stores:
 * 1. Persistent settings: Driving Mode toggle, Telegram Bot Token, Telegram Chat ID.
 * 2. Temporary call-state flags: Used by CallEventReceiver and CallAudioSpeaker to
 *    track the state of an ongoing or incoming call across broadcast events.
 */
class SettingsStore(context: Context) {

    // MODE_PRIVATE means only this application can read and write this preference file
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("driving_call_agent_prefs", Context.MODE_PRIVATE)

    companion object {
        // Default credentials (configured via Settings UI)
        const val DEFAULT_BOT_TOKEN = ""
        const val DEFAULT_CHAT_ID = ""

        // Preference key names
        private const val KEY_DRIVING_MODE = "is_driving_mode_enabled"
        private const val KEY_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_CHAT_ID = "telegram_chat_id"

        // Temporary call-state keys
        private const val KEY_LAST_CALLER = "last_caller_number"
        private const val KEY_RINGING_ACTIVE = "ringing_active"
        private const val KEY_HANDLED_THIS_CALL = "handled_this_call"
        private const val KEY_SPOKE_THIS_CALL = "spoke_this_call"
    }

    // =========================================================================
    // 1. Persistent Settings (Saved across app restarts)
    // =========================================================================

    /**
     * Whether Driving Mode is turned ON.
     */
    var isDrivingModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DRIVING_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DRIVING_MODE, value).apply()

    /**
     * Whether to auto-answer calls locally onto the loudspeaker.
     * Default is false (auto-declines and dispatches 1-Tap Voice Bridge SMS so caller hears real AI).
     */
    var isAutoAnswerEnabled: Boolean
        get() = prefs.getBoolean("is_auto_answer_enabled", false)
        set(value) = prefs.edit().putBoolean("is_auto_answer_enabled", value).apply()

    /**
     * Telegram Bot API token obtained from @BotFather.
     */
    var botToken: String
        get() {
            val saved = prefs.getString(KEY_BOT_TOKEN, null)
            return if (saved.isNullOrBlank()) DEFAULT_BOT_TOKEN else saved
        }
        set(value) = prefs.edit().putString(KEY_BOT_TOKEN, value.trim()).apply()

    /**
     * Telegram Chat ID obtained from @userinfobot or your chat.
     */
    var chatId: String
        get() {
            val saved = prefs.getString(KEY_CHAT_ID, null)
            return if (saved.isNullOrBlank()) DEFAULT_CHAT_ID else saved
        }
        set(value) = prefs.edit().putString(KEY_CHAT_ID, value.trim()).apply()

    /**
     * The Cloud Telephony / Twilio virtual phone number that runs the Voice AI Agent.
     */
    var cloudAgentNumber: String
        get() = prefs.getString("cloud_agent_number", "9566036224").orEmpty()
        set(value) = prefs.edit().putString("cloud_agent_number", value.trim()).apply()


    /**
     * Whether Cloud Call Forwarding mode is preferred over local auto-answer.
     * When true, calls are answered by the Cloud AI voice agent (Twilio + Gemini),
     * allowing the agent to actually speak to the caller with zero echo cancellation issues.
     */
    var isCloudForwardingMode: Boolean
        get() = prefs.getBoolean("is_cloud_forwarding_mode", true)
        set(value) = prefs.edit().putBoolean("is_cloud_forwarding_mode", value).apply()

    /**
     * Whether Call Forwarding is currently active on the cellular carrier.
     */
    var isForwardingActive: Boolean
        get() = prefs.getBoolean("is_forwarding_active", false)
        set(value) = prefs.edit().putBoolean("is_forwarding_active", value).apply()

    /**
     * Whether Auto-SMS Fallback is enabled.
     * If an incoming call is missed or unanswered while driving,
     * an automated SMS is sent to the caller inviting them to leave a message.
     */
    var isAutoSmsFallbackEnabled: Boolean
        get() = prefs.getBoolean("is_auto_sms_enabled", true)
        set(value) = prefs.edit().putBoolean("is_auto_sms_enabled", value).apply()

    /**
     * The automated SMS message sent to callers who call while driving.
     */
    var autoSmsTemplate: String
        get() = prefs.getString(
            "auto_sms_template",
            "Hi! I am Java, an AI agent for Tharun. Sir cannot answer right now because he is driving. To speak with me directly with your voice, tap this link: {LINK} or reply to this SMS with your urgent message."
        ).orEmpty()
        set(value) = prefs.edit().putString("auto_sms_template", value.trim()).apply()

    /**
     * Web link for 1-Tap Voice Bridge where callers can speak directly to Java.
     */
    var voiceAgentWebUrl: String
        get() = prefs.getString("voice_agent_web_url", "").orEmpty()
        set(value) = prefs.edit().putString("voice_agent_web_url", value.trim()).apply()


    /**
     * Google Gemini API Key for on-device SMS AI summarization and smart reply.
     */
    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "").orEmpty()
        set(value) = prefs.edit().putString("gemini_api_key", value.trim()).apply()


    // =========================================================================
    // 2. Temporary Call-State Flags (Track call lifecycle across broadcasts)
    // =========================================================================

    /**
     * The phone number of the incoming caller currently being handled.
     */
    var lastCaller: String
        get() = prefs.getString(KEY_LAST_CALLER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_CALLER, value).apply()

    /**
     * Flag indicating that the phone is actively ringing.
     */
    var ringingActive: Boolean
        get() = prefs.getBoolean(KEY_RINGING_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_RINGING_ACTIVE, value).apply()

    /**
     * Flag indicating that our agent answered or decided to handle this specific call.
     * Prevents duplicate handling if multiple broadcast events arrive for the same call.
     */
    var handledThisCall: Boolean
        get() = prefs.getBoolean(KEY_HANDLED_THIS_CALL, false)
        set(value) = prefs.edit().putBoolean(KEY_HANDLED_THIS_CALL, value).apply()

    /**
     * Flag indicating that TextToSpeech successfully started or finished speaking.
     * Helps us distinguish between "Agent answered and spoke" vs "Caller hung up early".
     */
    var spokeThisCall: Boolean
        get() = prefs.getBoolean(KEY_SPOKE_THIS_CALL, false)
        set(value) = prefs.edit().putBoolean(KEY_SPOKE_THIS_CALL, value).apply()

    /**
     * Helper function to reset all call-state flags back to their default values
     * once a call is completed (when phone returns to IDLE state).
     */
    fun resetCallFlags() {
        prefs.edit()
            .remove(KEY_LAST_CALLER)
            .putBoolean(KEY_RINGING_ACTIVE, false)
            .putBoolean(KEY_HANDLED_THIS_CALL, false)
            .putBoolean(KEY_SPOKE_THIS_CALL, false)
            .apply()
    }
}
