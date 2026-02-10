package com.example.roboai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Helper class that wraps MediaPipe FaceLandmarker for face detection and landmark extraction.
 * Handles initialization, configuration, and processing of camera frames.
 */
class FaceLandmarkerHelper(
    private val context: Context,
    private val listener: LandmarkerListener
) {
    companion object {
        private const val TAG = "FaceLandmarkerHelper"
        private const val MODEL_FILE = "face_landmarker.task"
        private const val NUM_FACES = 1
        private const val MIN_FACE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_FACE_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
    }

    private var faceLandmarker: FaceLandmarker? = null
    private var lastResultTime = 0L
    
    init {
        setupFaceLandmarker()
    }

    /**
     * Initialize the FaceLandmarker with optimal settings for real-time processing.
     */
    private fun setupFaceLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_FILE)
                .setDelegate(Delegate.CPU)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumFaces(NUM_FACES)
                .setMinFaceDetectionConfidence(MIN_FACE_DETECTION_CONFIDENCE)
                .setMinFacePresenceConfidence(MIN_FACE_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setOutputFaceBlendshapes(true)
                .setResultListener { result, input ->
                    val finishTimeMs = SystemClock.uptimeMillis()
                    val inferenceTime = finishTimeMs - lastResultTime
                    lastResultTime = finishTimeMs
                    listener.onResults(result, inferenceTime, input.width, input.height)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe error: ${error.message}")
                    listener.onError(error.message ?: "Unknown error")
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "FaceLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FaceLandmarker: ${e.message}")
            listener.onError("Failed to initialize face detector: ${e.message}")
        }
    }

    /**
     * Process a bitmap image for face detection.
     * @param bitmap The input image (already rotated and mirrored)
     * @param frameTimeMs The timestamp of the frame in milliseconds
     */
    fun detectAsync(bitmap: Bitmap, frameTimeMs: Long) {
        if (faceLandmarker == null) {
            Log.w(TAG, "FaceLandmarker not initialized")
            return
        }

        val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
        faceLandmarker?.detectAsync(mpImage, frameTimeMs)
    }

    /**
     * Release resources when no longer needed.
     */
    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    /**
     * Listener interface for receiving face landmark detection results.
     */
    interface LandmarkerListener {
        fun onResults(result: FaceLandmarkerResult, inferenceTimeMs: Long, imageWidth: Int, imageHeight: Int)
        fun onError(error: String)
    }
}
