package com.example.drivingcallagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * SmsEventReceiver: Listens for incoming SMS messages when Driving Mode is ON.
 *
 * 1. Captures reply SMS messages from callers.
 * 2. Uses Google Gemini API to analyze urgency & generate a summary.
 * 3. Delivers an instant alert to the user's Telegram Bot.
 * 4. Automatically texts a polite confirmation back to the sender.
 * 5. Saves the event to Room database.
 */
class SmsEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsEventReceiver"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val settings = SettingsStore(context)
        if (!settings.isDrivingModeEnabled) {
            Log.d(TAG, "Driving mode is OFF. Ignoring incoming SMS.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val sender = messages[0].displayOriginatingAddress ?: "Unknown Sender"
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }

        if (fullBody.isBlank()) {
            return
        }

        Log.i(TAG, "Driving Mode SMS received from: $sender: '$fullBody'")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processIncomingSms(context, settings, sender, fullBody)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processIncomingSms(
        context: Context,
        settings: SettingsStore,
        sender: String,
        body: String
    ) {
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(now))

        // 1. Analyze with Gemini API (or fallback if key not configured)
        val aiResult = analyzeWithGemini(settings.geminiApiKey, sender, body)
        val urgencyBadge = when (aiResult.urgency) {
            "CRITICAL" -> "🚨 *CRITICAL EMERGENCY*"
            "URGENT" -> "⚠️ *URGENT MESSAGE*"
            else -> "🟢 *Normal Message*"
        }

        // 2. Deliver Instant Alert to Telegram Bot
        val telegramMsg = """
            🚗 *Driving Call Agent — SMS Handled by AI*
            📞 *From:* `$sender`
            🕒 *Time:* $timeStr
            🏷️ *Urgency:* $urgencyBadge

            📝 *AI Summary:*
            ${aiResult.summary}

            💬 *Caller's Text:*
            _${body.trim()}_
        """.trimIndent()

        TelegramSender.send(context, telegramMsg)

        // 3. Send automated confirmation SMS back to sender
        if (aiResult.confirmationReply.isNotBlank()) {
            try {
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(aiResult.confirmationReply)
                smsManager.sendMultipartTextMessage(sender, null, parts, null, null)
                Log.i(TAG, "Confirmation SMS sent back to $sender: '${aiResult.confirmationReply}'")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send confirmation SMS to $sender", e)
            }
        }

        // 4. Record in Room database
        val record = CallRecord(
            callerNumber = "$sender (SMS)",
            timeEpochMs = now,
            status = "SMS: ${aiResult.urgency}"
        )
        try {
            AppDatabase.getDatabase(context).callDao().insert(record)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save call record", e)
        }
    }

    private data class AiAnalysis(
        val urgency: String,
        val summary: String,
        val confirmationReply: String
    )

    private fun analyzeWithGemini(apiKey: String, sender: String, body: String): AiAnalysis {
        if (apiKey.isBlank()) {
            return fallbackAnalysis(body)
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val prompt = """
                You are an AI assistant for Tharun while he is driving.
                A caller sent this SMS text:
                Sender: $sender
                Message: "$body"

                Respond in this EXACT format (no markdown code blocks, just raw lines):
                URGENCY: [CRITICAL or URGENT or NORMAL]
                SUMMARY: [1 short sentence summarizing what the sender needs]
                REPLY: [1 polite short sentence confirming to sender that Tharun was notified and will call back after parking]
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contents = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = httpClient.newCall(request).execute()
            val respString = response.body?.string() ?: ""

            if (response.isSuccessful && respString.isNotBlank()) {
                val respJson = JSONObject(respString)
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text") ?: ""

                    var urgency = "NORMAL"
                    var summary = body
                    var reply = "Thank you! I have notified Tharun. He will get back to you as soon as he parks safely."

                    text.lines().forEach { line ->
                        when {
                            line.startsWith("URGENCY:", ignoreCase = true) -> {
                                urgency = line.substringAfter(":").trim().uppercase()
                            }
                            line.startsWith("SUMMARY:", ignoreCase = true) -> {
                                summary = line.substringAfter(":").trim()
                            }
                            line.startsWith("REPLY:", ignoreCase = true) -> {
                                reply = line.substringAfter(":").trim()
                            }
                        }
                    }

                    return AiAnalysis(urgency, summary, reply)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call failed, falling back to rule-based analysis", e)
        }

        return fallbackAnalysis(body)
    }

    private fun fallbackAnalysis(body: String): AiAnalysis {
        val lower = body.lowercase()
        val isUrgent = lower.contains("urgent") || lower.contains("emergency") || lower.contains("asap") || lower.contains("hospital")
        val urgency = if (isUrgent) "URGENT" else "NORMAL"
        val summary = body.take(120)
        val reply = "Thank you! I have notified Tharun on his dashboard. He will get back to you as soon as he parks safely."
        return AiAnalysis(urgency, summary, reply)
    }
}
