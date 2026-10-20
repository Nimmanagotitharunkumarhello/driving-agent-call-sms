package com.example.drivingcallagent

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CallEventReceiver: Full state machine handling incoming calls, auto-answering,
 * audio delivery, Room database records, and Telegram notifications.
 */
class CallEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallEventReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val settings = SettingsStore(context)
        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        Log.d(TAG, "PHONE_STATE event: $stateStr, incomingNumber: $incomingNumber")

        when (stateStr) {
            // =================================================================
            // 1. STATE: RINGING
            // =================================================================
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!settings.isDrivingModeEnabled) {
                    Log.d(TAG, "Driving Mode is OFF. Ignoring.")
                    return
                }

                if (incomingNumber.isNotBlank()) {
                    settings.lastCaller = incomingNumber
                }


                // Guard against duplicate RINGING events
                if (settings.ringingActive) {
                    Log.d(TAG, "Duplicate RINGING event ignored.")
                    return
                }

                settings.ringingActive = true
                settings.handledThisCall = true


                Log.d(TAG, "Auto-answering incoming call in 2 seconds...")

                val pendingResult = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        answerCall(context)
                    } finally {
                        pendingResult.finish()
                    }
                }, 2000L)
            }

            // =================================================================
            // 2. STATE: OFFHOOK (Call Connected)
            // =================================================================
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                settings.ringingActive = false

                if (!settings.handledThisCall) {
                    return
                }

                // Consume handledThisCall immediately to prevent duplicate OFFHOOK triggers
                settings.handledThisCall = false

                Log.d(TAG, "Call is OFFHOOK. Speaking message to caller...")

                val pendingResult = goAsync()
                CallAudioSpeaker.speakAndHangUp(context) {
                    try {
                        val caller = settings.lastCaller.ifBlank { "Unknown Caller" }
                        val now = System.currentTimeMillis()
                        val timeStr = formatTimestamp(now)

                        // 1. Save "ANSWERED_BY_AGENT" in Room database
                        val record = CallRecord(
                            callerNumber = caller,
                            timeEpochMs = now,
                            status = "ANSWERED_BY_AGENT"
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            AppDatabase.getDatabase(context).callDao().insert(record)
                            Log.d(TAG, "Saved ANSWERED call record: $record")
                        }

                        // 2. Send Telegram notification summary
                        val telegramMsg = """
                            🚗 *Driving Call Agent — Call Handled*
                            📞 *From:* $caller
                            🕒 *Time:* $timeStr
                            ✅ *Action:* Auto-answered and driving message spoken.
                        """.trimIndent()

                        TelegramSender.send(context, telegramMsg)

                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            // =================================================================
            // 3. STATE: IDLE (Call Ended or Ringing Stopped)
            // =================================================================
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Phone is IDLE.")

                // If the agent didn't speak (call was missed, rejected, or unanswered)
                if (settings.isDrivingModeEnabled && !settings.spokeThisCall && settings.lastCaller.isNotBlank()) {
                    val caller = settings.lastCaller
                    val now = System.currentTimeMillis()
                    val timeStr = formatTimestamp(now)

                    // 1. Send Auto-SMS Fallback to the caller
                    if (settings.isAutoSmsFallbackEnabled && caller != "Unknown Caller") {
                        try {
                            val smsManager = android.telephony.SmsManager.getDefault()
                            val parts = smsManager.divideMessage(settings.autoSmsTemplate)
                            smsManager.sendMultipartTextMessage(caller, null, parts, null, null)
                            Log.i(TAG, "Sent Auto-SMS fallback to $caller: '${settings.autoSmsTemplate}'")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send Auto-SMS fallback to $caller", e)
                        }
                    }

                    // 2. Save MISSED in Room database
                    val record = CallRecord(
                        callerNumber = caller,
                        timeEpochMs = now,
                        status = "MISSED (SMS SENT)"
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        AppDatabase.getDatabase(context).callDao().insert(record)
                    }

                    // 3. Send Telegram missed call alert
                    val telegramMsg = """
                        ⚠️ *Driving Call Agent — Call Unanswered*
                        📞 *From:* $caller
                        🕒 *Time:* $timeStr
                        📩 *Action:* Auto-SMS sent inviting caller to leave a text message.
                    """.trimIndent()

                    TelegramSender.send(context, telegramMsg)
                }

                // Reset all temporary flags for the next call
                settings.resetCallFlags()

            }
        }
    }

    /**
     * Formats timestamp into user-friendly time string (e.g. "03:45 PM, 20 Oct").
     */
    private fun formatTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    /**
     * Answers the currently ringing call using TelecomManager.
     */
    private fun answerCall(context: Context) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                telecomManager.acceptRingingCall()
                Log.i(TAG, "acceptRingingCall() invoked successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in acceptRingingCall()", e)
            }
        }
    }
}
