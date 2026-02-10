package com.example.roboai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.example.roboai.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.Locale

/**
 * Main activity for face detection and emotion analysis.
 */
class MainActivity : AppCompatActivity(), 
    FaceLandmarkerHelper.LandmarkerListener,
    CameraManager.FrameListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var cameraManager: CameraManager? = null
    private val emotionAnalyzer = EmotionAnalyzer()
    
    private var landmarksVisible = true
    private var lastFpsTime = System.currentTimeMillis()
    private var frameCount = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraAndDetection()
        } else {
            showNoPermissionUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkCameraPermission()
    }

    private fun setupUI() {
        binding.toggleLandmarksBtn.setOnClickListener {
            landmarksVisible = binding.overlayView.toggleLandmarks()
            binding.toggleLandmarksBtn.text = if (landmarksVisible) "Hide Landmarks" else "Show Landmarks"
        }

        binding.requestPermissionBtn.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraAndDetection()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraAndDetection() {
        binding.noPermissionView.visibility = View.GONE
        
        faceLandmarkerHelper = FaceLandmarkerHelper(
            context = this,
            listener = this
        )

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView,
            frameListener = this
        )

        cameraManager?.startCamera()
    }

    private fun showNoPermissionUI() {
        binding.noPermissionView.visibility = View.VISIBLE
    }

    // ===== CameraManager.FrameListener =====

    override fun onFrame(bitmap: Bitmap, timestampMs: Long, width: Int, height: Int) {
        faceLandmarkerHelper?.detectAsync(bitmap, timestampMs / 1_000_000)
        updateFps()
    }

    // ===== FaceLandmarkerHelper.LandmarkerListener =====

    override fun onResults(
        result: FaceLandmarkerResult,
        inferenceTimeMs: Long,
        imageWidth: Int,
        imageHeight: Int
    ) {
        runOnUiThread {
            binding.overlayView.setResults(result, imageWidth, imageHeight)

            val emotionResult = emotionAnalyzer.analyze(result)
            
            if (emotionResult != null) {
                updateEmotionUI(emotionResult)
            } else {
                binding.emotionText.text = getString(R.string.no_face_detected)
                binding.confidenceText.text = getString(R.string.confidence_empty)
                binding.headPoseText.text = getString(R.string.head_pose_empty)
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Log.e(TAG, "Face detection error: $error")
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmotionUI(result: EmotionAnalyzer.EmotionResult) {
        val emotionStringRes = when (result.emotion) {
            EmotionAnalyzer.Emotion.HAPPY -> R.string.emotion_happy
            EmotionAnalyzer.Emotion.SURPRISED -> R.string.emotion_surprised
            EmotionAnalyzer.Emotion.ANGRY -> R.string.emotion_angry
            EmotionAnalyzer.Emotion.NEUTRAL -> R.string.emotion_neutral
        }
        binding.emotionText.text = getString(emotionStringRes)

        val confidencePercent = (result.confidence * 100).toInt()
        binding.confidenceText.text = getString(R.string.confidence_format, confidencePercent)

        val metrics = result.metrics
        binding.headPoseText.text = getString(
            R.string.head_pose_format,
            metrics.headYaw,
            metrics.headPitch
        )

        val emotionColor = when (result.emotion) {
            EmotionAnalyzer.Emotion.HAPPY -> "#4CAF50".toColorInt()
            EmotionAnalyzer.Emotion.SURPRISED -> "#FF9800".toColorInt()
            EmotionAnalyzer.Emotion.ANGRY -> "#F44336".toColorInt()
            EmotionAnalyzer.Emotion.NEUTRAL -> "#9E9E9E".toColorInt()
        }
        binding.emotionText.setTextColor(emotionColor)
    }

    private fun updateFps() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFpsTime
        
        if (elapsed >= 1000) {
            val fps = frameCount * 1000f / elapsed
            runOnUiThread {
                binding.fpsText.text = String.format(Locale.US, "FPS: %.1f", fps)
            }
            frameCount = 0
            lastFpsTime = currentTime
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.shutdown()
        faceLandmarkerHelper?.close()
    }
}
