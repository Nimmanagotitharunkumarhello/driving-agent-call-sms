package com.example.drivingcallagent

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * TelegramSender is a singleton object responsible for sending notification
 * messages to the user via the Telegram Bot HTTP API.
 *
 * It uses OkHttp to dispatch HTTP POST requests asynchronously in the background.
 */
object TelegramSender {

    private const val TAG = "TelegramSender"

    // Single OkHttpClient instance reused across all network calls
    private val client = OkHttpClient()

    // Main thread handler used to deliver callbacks safely back to the UI thread
    private val mainHandler = Handler(Looper.getMainLooper())

    // JSON media type header required by Telegram API
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Sends a message to Telegram.
     *
     * @param context Application context used to read saved bot token and chat id.
     * @param message Text message to send to the user's Telegram chat.
     * @param onResult Optional callback returning true if sent successfully, false otherwise.
     */
    fun send(
        context: Context,
        message: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        val settings = SettingsStore(context)
        val token = settings.botToken.ifBlank { SettingsStore.DEFAULT_BOT_TOKEN }
        val chatId = settings.chatId.ifBlank { SettingsStore.DEFAULT_CHAT_ID }

        // Safety check: ensure credentials exist before attempting network call
        if (token.isBlank() || chatId.isBlank()) {
            Log.w(TAG, "Cannot send Telegram message: Bot Token or Chat ID is empty.")
            mainHandler.post { onResult(false) }
            return
        }

        // Telegram Bot API endpoint for sending messages
        val url = "https://api.telegram.org/bot$token/sendMessage"

        // Build the JSON payload: {"chat_id": "...", "text": "..."}
        val jsonPayload = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
        }.toString()

        // Construct HTTP POST request
        val requestBody = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        // Enqueue dispatches the call on OkHttp's background thread pool
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network error sending Telegram message", e)
                mainHandler.post { onResult(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    // Telegram returns HTTP 200 on successful delivery
                    val success = resp.isSuccessful
                    if (!success) {
                        Log.e(TAG, "Telegram API error: HTTP code ${resp.code}, body: ${resp.body?.string()}")
                    } else {
                        Log.i(TAG, "Telegram message sent successfully!")
                    }
                    mainHandler.post { onResult(success) }
                }
            }
        })
    }
}
