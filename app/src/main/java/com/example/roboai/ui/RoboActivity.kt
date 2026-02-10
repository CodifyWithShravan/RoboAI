package com.example.roboai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.roboai.CameraManager
import com.example.roboai.EmotionAnalyzer
import com.example.roboai.FaceLandmarkerHelper
import com.example.roboai.ai.TFLiteClassifier
import com.example.roboai.sensors.AudioAnalyzer
import com.example.roboai.sensors.SensorFusionManager
import com.example.roboai.state.RoboSignal
import com.example.roboai.state.RoboViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Main Compose Activity hosting the RoboFace UI.
 * Integrates camera face detection, sensors, audio, and TFLite.
 * 
 * The robot face reacts to:
 * - Your facial expressions (Happy/Angry/Neutral via camera)
 * - Phone tilt (eye movement via accelerometer)
 * - Phone shake (Angry state)
 * - Proximity sensor (Sleep state)
 * - Audio amplitude (mouth bar animation)
 * - Loud sounds (Angry state)
 */
class RoboActivity : ComponentActivity(),
    FaceLandmarkerHelper.LandmarkerListener,
    CameraManager.FrameListener {

    companion object {
        private const val TAG = "RoboActivity"
    }

    private lateinit var viewModel: RoboViewModel
    private lateinit var sensorFusion: SensorFusionManager
    private lateinit var audioAnalyzer: AudioAnalyzer
    private lateinit var tfliteClassifier: TFLiteClassifier
    
    // Face detection components (from Task 1)
    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var cameraManager: CameraManager? = null
    private val emotionAnalyzer = EmotionAnalyzer()
    
    // Hidden PreviewView for camera (not shown on screen)
    private var previewView: PreviewView? = null
    
    // Track face presence for Curious/FaceLost signals
    private var isFaceDetected = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCameraAndDetection()
        }
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startAudioAnalysis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[RoboViewModel::class.java]

        // Initialize sensors
        sensorFusion = SensorFusionManager(this) { signal ->
            viewModel.onSignal(signal)
        }

        // Initialize audio
        audioAnalyzer = AudioAnalyzer(this) { signal ->
            viewModel.onSignal(signal)
        }

        // Initialize TFLite
        // Initialize TFLite Classifier (Task 6)
        tfliteClassifier = TFLiteClassifier(this)
        
        // Observe delegate changes
        // Observe delegate changes safely
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.delegateName.collect { delegate ->
                    tfliteClassifier.setDelegate(delegate)
                }
            }
        }
        
        // Observe state changes for Sleep optimization (Task: "pause work when sleep")
        lifecycleScope.launch {
            // We use standard collect here as this is logic, not UI
            viewModel.roboState.collect { state ->
                val shouldPause = (state is com.example.roboai.state.RoboState.Sleep)
                tfliteClassifier.setPaused(shouldPause)
            }
        }
        
        // Start background inference loop
        tfliteClassifier.startInferenceLoop(
            onSignal = { signal -> viewModel.onSignal(signal) },
            onPerformanceUpdate = { latencyMs, delegate ->
                viewModel.updatePerformanceMetrics(latencyMs, delegate)
            }
        )

        // Compose UI with hidden camera preview
        setContent {
            val roboState by viewModel.roboState.collectAsState()
            val audioAmplitude by viewModel.audioAmplitude.collectAsState()
            val eyeOffsetX by viewModel.eyeOffsetX.collectAsState()
            val eyeOffsetY by viewModel.eyeOffsetY.collectAsState()
            val headRoll by viewModel.headRoll.collectAsState()
            val inferenceLatencyMs by viewModel.inferenceLatencyMs.collectAsState()
            val userEmotion by viewModel.userEmotion.collectAsState()
            val userConfidence by viewModel.userConfidence.collectAsState()
            val faceLandmarks by viewModel.faceLandmarks.collectAsState()
            val delegateName by viewModel.delegateName.collectAsState()
            val availableDelegates = viewModel.availableDelegates

            Box(modifier = Modifier.fillMaxSize()) {
                // Hidden camera preview (1x1 dp, invisible but needed for CameraX)
                AndroidView(
                    factory = { context ->
                        PreviewView(context).also { preview ->
                            previewView = preview
                            // Start camera after preview is ready
                            preview.post { requestPermissions() }
                        }
                    },
                    modifier = Modifier.size(1.dp)
                )
                
                // Robot face UI on top
                RoboFaceScreen(
                    roboState = roboState,
                    audioAmplitude = audioAmplitude,
                    eyeOffsetX = eyeOffsetX,
                    eyeOffsetY = eyeOffsetY,
                    headRoll = headRoll,
                    inferenceLatencyMs = inferenceLatencyMs,
                    delegateName = delegateName,
                    userEmotion = userEmotion,
                    userConfidence = userConfidence,
                    faceLandmarks = faceLandmarks,
                    availableDelegates = availableDelegates,
                    onDelegateSelected = { viewModel.setDelegate(it) },
                    onTap = { viewModel.onSignal(RoboSignal.UserTap) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorFusion.start()
    }

    override fun onPause() {
        super.onPause()
        sensorFusion.stop()
        audioAnalyzer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.shutdown()
        faceLandmarkerHelper?.close()
        tfliteClassifier.stop()
    }

    // ===== Permissions =====

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        } else {
            startCameraAndDetection()
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        } else {
            startAudioAnalysis()
        }
        
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // ===== Camera + Face Detection (Task 1 Integration) =====

    private fun startCameraAndDetection() {
        val preview = previewView ?: return
        
        faceLandmarkerHelper = FaceLandmarkerHelper(
            context = this,
            listener = this
        )

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = preview,
            frameListener = this
        )

        cameraManager?.startCamera()
        Log.d(TAG, "Camera + face detection started")
    }

    // ===== CameraManager.FrameListener =====

    override fun onFrame(bitmap: Bitmap, timestampMs: Long, width: Int, height: Int) {
        faceLandmarkerHelper?.detectAsync(bitmap, timestampMs / 1_000_000)
    }

    // ===== FaceLandmarkerHelper.LandmarkerListener =====

    override fun onResults(
        result: FaceLandmarkerResult,
        inferenceTimeMs: Long,
        imageWidth: Int,
        imageHeight: Int
    ) {
        // Task 6: Pass landmarks to TFLite & UI
        // Task 6: Pass landmarks to TFLite & UI
        result.faceLandmarks().firstOrNull()?.let { landmarks ->
            // Update ViewModel for Overlay (Task 1 Refinement)
            viewModel.updateLandmarks(landmarks)
            
            // Update TFLite Classifier (Task 6)
            // Flatten landmarks: [x1, y1, x2, y2, ...]
            val flattened = FloatArray(landmarks.size * 2)
            landmarks.forEachIndexed { i, landmark: NormalizedLandmark ->
                flattened[i * 2] = landmark.x()
                flattened[i * 2 + 1] = landmark.y()
            }
            tfliteClassifier.updateInput(flattened)
            
        } ?: run {
            viewModel.updateLandmarks(null)
            tfliteClassifier.updateInput(null)
        }
        val emotionResult = emotionAnalyzer.analyze(result)
        
        runOnUiThread {
            if (emotionResult != null) {
                // Face detected → Curious
                if (!isFaceDetected) {
                    isFaceDetected = true
                    viewModel.onSignal(RoboSignal.FaceDetected)
                }
                
                // Map detected emotion to signal
                when (emotionResult.emotion) {
                    EmotionAnalyzer.Emotion.HAPPY -> viewModel.onSignal(RoboSignal.SmileDetected)
                    EmotionAnalyzer.Emotion.ANGRY -> viewModel.onSignal(RoboSignal.AngerDetected)
                    EmotionAnalyzer.Emotion.SURPRISED -> viewModel.onSignal(RoboSignal.SurpriseDetected)
                    EmotionAnalyzer.Emotion.NEUTRAL -> {
                        // Keep current state, just acknowledge face presence
                        viewModel.onSignal(RoboSignal.FaceDetected)
                    }
                }
                
                // Update debug info
                viewModel.updateUserData(emotionResult.emotion.name, emotionResult.confidence)
            } else {
                // No face detected
                if (isFaceDetected) {
                    isFaceDetected = false
                    viewModel.onSignal(RoboSignal.FaceLost)
                }
            }
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "Face detection error: $error")
        runOnUiThread {
            Toast.makeText(this, "Face detection: $error", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== Audio =====

    private fun startAudioAnalysis() {
        audioAnalyzer.start(lifecycleScope)
        Log.d(TAG, "Audio analysis started")
    }
}
