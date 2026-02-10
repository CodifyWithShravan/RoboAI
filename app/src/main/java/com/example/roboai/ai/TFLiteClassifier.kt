package com.example.roboai.ai

import android.content.Context
import android.util.Log
import com.example.roboai.state.RoboSignal
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite classifier for real-time emotion classification.
 * 
 * Task 6 Requirements:
 * - Load .tflite model from assets
 * - Support CPU / GPU / NNAPI delegates
 * - Inference on background thread
 * - Map output to RoboSignal
 */
class TFLiteClassifier(
    private val context: Context
) {
    companion object {
        private const val TAG = "TFLiteClassifier"
        private const val MODEL_FILE = "robo_classifier.tflite"
        
        // Model Specifics (adjust based on actual model)
        private const val NUM_LANDMARKS = 478
        private const val INPUT_SIZE = NUM_LANDMARKS * 2 // x, y for each landmark
        private const val OUTPUT_CLASSES = 7 // [Angry, Disgust, Fear, Happy, Sad, Surprise, Neutral]
    }

    // Interpreters & Delegates
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null
    
    // State
    private var activeDelegate = "CPU"
    private var isModelLoaded = false
    private var useMockFallback = false
    private var isPaused = false
    private var mockCycleIndex = 0
    private var mockCycleTimer = 0L
    
    // Concurrency
    private var inferenceJob: Job? = null
    private val inferenceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Input Data Holder
    // We hold the latest landmarks to run inference on
    @Volatile
    private var currentLandmarks: FloatArray? = null

    init {
        // Initial load with CPU
        initializeInterpreter("CPU")
    }

    /**
     * Initialize or Re-initialize the interpreter with the specified delegate.
     */
    fun setDelegate(delegateName: String) {
        if (delegateName != activeDelegate) {
            activeDelegate = delegateName
            initializeInterpreter(delegateName)
        }
    }

    private fun initializeInterpreter(delegateName: String) {
        close() // Close existing resources
        
        activeDelegate = delegateName
        
        try {
            val modelBuffer = loadModelFile(MODEL_FILE)
            val options = Interpreter.Options()
            
            when (delegateName) {
                "CPU" -> {
                    options.setNumThreads(4)
                }
                "GPU" -> {
                    gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate)
                }
                "NNAPI" -> {
                    nnApiDelegate = NnApiDelegate()
                    options.addDelegate(nnApiDelegate)
                }
            }
            
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            useMockFallback = false
            Log.d(TAG, "Initialized TFLite with $delegateName delegate")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite ($delegateName): ${e.message}")
            // Fallback to mock if model file missing or init fails
            isModelLoaded = false
            useMockFallback = true
            Log.w(TAG, "Using Mock Inference Fallback")
        }
    }
    
    fun setPaused(paused: Boolean) {
        if (isPaused != paused) {
            isPaused = paused
            Log.d(TAG, "Inference paused: $paused")
        }
    }

    /**
     * Update the landmarks to be processed in the next inference cycle.
     * thread-safe.
     */
    /**
     * Update the landmarks to be processed in the next inference cycle.
     * thread-safe.
     */
    fun updateInput(landmarks: FloatArray?) {
        currentLandmarks = landmarks
    }

    fun startInferenceLoop(
        onSignal: (RoboSignal) -> Unit,
        onPerformanceUpdate: (latencyMs: Long, delegate: String) -> Unit
    ) {
        inferenceJob?.cancel()
        inferenceJob = inferenceScope.launch {
            while (isActive) {
                if (isPaused) {
                    delay(500) // Sleep while paused
                    continue
                }

                val startTime = System.nanoTime()
                
                var predictionIdx = -1
                var confidence = 0f
                
                // Get current input snapshot safely
                // Get current input snapshot safely
                val input = currentLandmarks
                
                if (input == null) {
                    // No face detected, skip inference
                    delay(100)
                    continue
                }
                
                if (useMockFallback) {
                    // Mock Logic: Simulated delay (30ms ~ 33FPS) to reduce jitter from 10ms
                    delay(30) 
                    // Even if input is null, we generate a mock result
                    val mockResult = runMockInference(input)
                    predictionIdx = mockResult.first
                    confidence = mockResult.second
                } else if (interpreter != null) {
                    val result = runRealInference(input)
                    predictionIdx = result.first
                    confidence = result.second
                }
                
                val latencyMs = (System.nanoTime() - startTime) / 1_000_000
                
                if (predictionIdx != -1) {
                    // Map result to signal (Standard FER-2013: 0=Angry, 1=Disgust, 2=Fear, 3=Happy, 4=Sad, 5=Surprise, 6=Neutral)
                    val signal = when(predictionIdx) {
                        0 -> RoboSignal.AngerDetected // Angry
                        1 -> RoboSignal.AngerDetected // Disgust -> Angry
                        2 -> RoboSignal.SurpriseDetected // Fear -> Surprised
                        3 -> RoboSignal.SmileDetected // Happy
                        4 -> RoboSignal.FaceDetected // Sad -> Neutral (for now, or add RoboSignal.Sadness)
                        5 -> RoboSignal.SurpriseDetected // Surprised
                        6 -> RoboSignal.FaceDetected // Neutral
                        else -> null
                    }
                    
                    withContext(Dispatchers.Main) {
                        signal?.let { onSignal(it) }
                        onPerformanceUpdate(latencyMs, if(useMockFallback) "MOCK" else activeDelegate)
                    }
                } else {
                     withContext(Dispatchers.Main) {
                        onPerformanceUpdate(latencyMs, if(useMockFallback) "MOCK" else activeDelegate)
                    }
                }
                
                if (!useMockFallback) {
                    delay(10) // 10ms delay for real inference to avoid hogging CPU
                }
            }
        }
    }

    private fun runRealInference(input: FloatArray): Pair<Int, Float> {
        // Output tensor shape: [1, 7]
        val output = Array(1) { FloatArray(OUTPUT_CLASSES) }
        
        try {
            interpreter?.run(input, output)
            
            val probs = output[0]
            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: -1
            return if (maxIdx != -1) maxIdx to probs[maxIdx] else -1 to 0f
            
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            return -1 to 0f
        }
    }

    private fun runMockInference(input: FloatArray): Pair<Int, Float> {
        // Mock Classification Logic: Cycle through emotions every 2 seconds
        val now = System.currentTimeMillis()
        if (now - mockCycleTimer > 2000) {
            mockCycleTimer = now
            mockCycleIndex = (mockCycleIndex + 1) % 7
        }
        
        // Return confident result for the current cycle index
        // 0:Angry, 1:Disgust(Angry), 2:Fear(Surprise), 3:Happy, 4:Sad, 5:Surprise, 6:Neutral
        return mockCycleIndex to 0.95f
    }
    
    fun stop() {
        inferenceJob?.cancel()
        close()
        Log.d(TAG, "Inference stopped and resources released")
    }

    private fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        nnApiDelegate?.close()
        interpreter = null
        gpuDelegate = null
        nnApiDelegate = null
    }

    private fun loadModelFile(filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
