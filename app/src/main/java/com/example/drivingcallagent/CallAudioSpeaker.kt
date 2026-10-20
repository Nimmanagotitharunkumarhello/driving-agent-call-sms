package com.example.drivingcallagent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CallAudioSpeaker manages:
 * 1. Audio routing with AudioAttributes.USAGE_VOICE_COMMUNICATION & STREAM_VOICE_CALL
 * 2. Speakerphone and communication device setup for in-call playback
 * 3. TextToSpeech playback of Java AI persona and clean hangup
 */
object CallAudioSpeaker {

    private const val TAG = "CallAudioSpeaker"

    // The fixed driving message spoken to the caller by Java AI
    const val DRIVING_MESSAGE =
        "Hi! I am Java, an AI agent for Tharun. Sir cannot attend the call right now because he is driving. When he finishes driving and parks safely, he will call you back. If you have any urgent queries or messages, please send a message right now and I will notify sir immediately. Thank you!"

    private var activeTts: TextToSpeech? = null
    private var activeHandler: Handler? = null
    private var activeRunnable: Runnable? = null
    private var activeAudioManager: AudioManager? = null
    private var originalMode: Int = AudioManager.MODE_NORMAL
    private var wasSpeakerOn: Boolean = false

    /**
     * Immediately stops TTS playback and restores audio settings.
     */
    fun stop(context: Context? = null) {
        Log.i(TAG, "Stopping CallAudioSpeaker immediately.")
        try {
            activeRunnable?.let { activeHandler?.removeCallbacks(it) }
            activeRunnable = null
            activeTts?.stop()
            activeTts?.shutdown()
            activeTts = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }

        try {
            activeAudioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = wasSpeakerOn
                }
                am.mode = originalMode
            }
            activeAudioManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring audio settings in stop()", e)
        }
    }

    /**
     * Speaks the driving message to the caller and hangs up when finished.
     */
    fun speakAndHangUp(
        context: Context,
        onComplete: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val settings = SettingsStore(appContext)

        activeAudioManager = audioManager
        originalMode = audioManager.mode
        wasSpeakerOn = audioManager.isSpeakerphoneOn
        val originalCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)

        val hasFinished = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        activeHandler = mainHandler

        var tts: TextToSpeech? = null

        // Teardown helper
        fun finishCall() {
            if (!hasFinished.compareAndSet(false, true)) {
                return
            }

            Log.d(TAG, "Finishing call: tearing down TTS and restoring audio state.")

            // 1. Shutdown TTS
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down TTS", e)
            }

            // 2. Restore audio settings
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = wasSpeakerOn
                }
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalCallVolume, 0)
                audioManager.mode = originalMode
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring audio settings", e)
            }

            // 3. End call
            endCall(appContext)

            // 4. Notify caller
            onComplete()
        }

        // 25-second absolute safety timeout
        val safetyTimeoutRunnable = Runnable {
            Log.w(TAG, "Safety 25-second timeout reached. Forcing call to end.")
            finishCall()
        }
        mainHandler.postDelayed(safetyTimeoutRunnable, 25000L)

        // Calculate speech duration
        val words = DRIVING_MESSAGE.split("\\s+".toRegex()).size
        val estimatedSpeechDurationMs = (words * 390L) + 2000L
        Log.d(TAG, "Word count: $words, estimated duration: ${estimatedSpeechDurationMs}ms")

        // Initialize TextToSpeech
        tts = TextToSpeech(appContext) { initStatus ->
            activeTts = tts
            if (initStatus != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TextToSpeech init failed. Ending call.")
                mainHandler.removeCallbacks(safetyTimeoutRunnable)
                finishCall()
                return@TextToSpeech
            }

            try {
                tts?.language = Locale.US

                // Modern AudioAttributes for in-call voice routing
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                // Set mode to MODE_IN_COMMUNICATION for standard in-call voice routing
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                // Enable speakerphone / communication device for high-power in-call audio
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }

                // Maximize voice call volume during the message
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)

                val utteranceId = "JavaAgentUtterance"

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started speaking Java persona message.")
                        settings.spokeThisCall = true
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS onDone callback received.")
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS onError callback received.")
                    }
                })

                // Bundle with explicit STREAM_VOICE_CALL parameter
                val ttsParams = Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }

                // Speak message into the in-call voice channel
                val speakResult = tts?.speak(
                    DRIVING_MESSAGE,
                    TextToSpeech.QUEUE_FLUSH,
                    ttsParams,
                    utteranceId
                )

                if (speakResult == TextToSpeech.ERROR) {
                    Log.e(TAG, "tts.speak returned ERROR. Ending call.")
                    mainHandler.removeCallbacks(safetyTimeoutRunnable)
                    finishCall()
                    return@TextToSpeech
                }

                settings.spokeThisCall = true

                // Schedule hangup based on estimated duration
                activeRunnable = Runnable {
                    mainHandler.removeCallbacks(safetyTimeoutRunnable)
                    finishCall()
                }
                mainHandler.postDelayed(activeRunnable!!, estimatedSpeechDurationMs)

            } catch (e: Exception) {
                Log.e(TAG, "Exception during TTS playback setup", e)
                mainHandler.removeCallbacks(safetyTimeoutRunnable)
                finishCall()
            }
        }
    }

    /**
     * Programmatically ends the phone call.
     */
    private fun endCall(context: Context) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                telecomManager.endCall()
                Log.i(TAG, "telecomManager.endCall() executed.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to end call", e)
            }
        }
    }
}
