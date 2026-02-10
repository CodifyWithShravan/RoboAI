package com.example.roboai.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.roboai.state.RoboSignal
import kotlinx.coroutines.*
import kotlin.math.log10

/**
 * Real-time audio amplitude analyzer using the device microphone.
 * Maps audio levels to mouth bar heights, detects loud sounds, and speech.
 */
class AudioAnalyzer(
    private val context: Context,
    private val onSignal: (RoboSignal) -> Unit
) {
    companion object {
        private const val TAG = "AudioAnalyzer"
        private const val SAMPLE_RATE = 22050
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val LOUD_THRESHOLD_DB = 75f     // dB threshold for "loud sound"
        private const val SPEECH_THRESHOLD_DB = 50f   // dB threshold for "speech"
        private const val SILENCE_THRESHOLD_DB = 30f  // dB threshold for "silence"
        private const val SIGNAL_COOLDOWN_MS = 1500L
        private const val UPDATE_INTERVAL_MS = 50L    // 20 FPS for smooth animation
    }

    private var audioRecord: AudioRecord? = null
    private var analyzerJob: Job? = null
    private var isRunning = false
    private var lastLoudSignalTime = 0L
    private var lastSilenceSignalTime = 0L
    private var lastSpeechSignalTime = 0L

    /**
     * Start recording and analyzing audio.
     * Requires RECORD_AUDIO permission.
     */
    fun start(scope: CoroutineScope) {
        if (!hasPermission()) {
            Log.w(TAG, "Audio permission not granted")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isRunning = true

            analyzerJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize / 2)
                Log.d(TAG, "Audio analysis started")

                while (isActive && isRunning) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (readCount > 0) {
                        val amplitude = calculateAmplitude(buffer, readCount)
                        val db = amplitudeToDb(amplitude)
                        val normalized = dbToNormalized(db)
                        
                        // Send amplitude update for mouth animation
                        withContext(Dispatchers.Main) {
                            onSignal(RoboSignal.AudioLevel(normalized))
                        }
                        
                        val now = System.currentTimeMillis()
                        
                        if (db > LOUD_THRESHOLD_DB && (now - lastLoudSignalTime) > SIGNAL_COOLDOWN_MS) {
                            lastLoudSignalTime = now
                            withContext(Dispatchers.Main) { onSignal(RoboSignal.LoudSound) }
                            Log.d(TAG, "Loud sound: ${db.toInt()} dB")
                        } else if (db > SPEECH_THRESHOLD_DB && (now - lastSpeechSignalTime) > 1000L) {
                             lastSpeechSignalTime = now
                             // Only trigger speech if not loud
                             withContext(Dispatchers.Main) { onSignal(RoboSignal.SpeechDetected) }
                        } else if (db < SILENCE_THRESHOLD_DB && (now - lastSilenceSignalTime) > SIGNAL_COOLDOWN_MS * 2) {
                            lastSilenceSignalTime = now
                            withContext(Dispatchers.Main) { onSignal(RoboSignal.Silence) }
                        }
                    }
                    
                    delay(UPDATE_INTERVAL_MS)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
        }
    }

    /**
     * Stop recording and release resources.
     */
    fun stop() {
        isRunning = false
        analyzerJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio: ${e.message}")
        }
        audioRecord = null
        Log.d(TAG, "Audio analysis stopped")
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Calculate RMS amplitude from audio buffer.
     */
    private fun calculateAmplitude(buffer: ShortArray, readCount: Int): Float {
        var sum = 0.0
        for (i in 0 until readCount) {
            sum += buffer[i].toFloat() * buffer[i].toFloat()
        }
        return kotlin.math.sqrt(sum / readCount).toFloat()
    }

    /**
     * Convert amplitude to decibels.
     */
    private fun amplitudeToDb(amplitude: Float): Float {
        return if (amplitude > 0) (20 * log10(amplitude.toDouble())).toFloat() else 0f
    }

    /**
     * Normalize dB to 0.0 - 1.0 range for UI.
     */
    private fun dbToNormalized(db: Float): Float {
        // Map ~20dB to 1.0 range (20dB = silence, 90dB = loud)
        return ((db - 20f) / 70f).coerceIn(0f, 1f)
    }
}
