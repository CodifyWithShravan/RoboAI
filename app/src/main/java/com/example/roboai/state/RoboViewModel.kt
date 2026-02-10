package com.example.roboai.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing the Robo's Finite State Machine.
 */
class RoboViewModel : ViewModel() {

    companion object {
        private const val TAG = "RoboViewModel"
        private const val INACTIVITY_TIMEOUT_MS = 10_000L
        private const val STATE_HOLD_MS = 2_000L
    }

    private val _roboState = MutableStateFlow<RoboState>(RoboState.Idle)
    val roboState: StateFlow<RoboState> = _roboState.asStateFlow()
    
    // User emotion for Debug Overlay
    private val _userEmotion = MutableStateFlow("None")
    val userEmotion: StateFlow<String> = _userEmotion.asStateFlow()
    
    private val _userConfidence = MutableStateFlow(0f)
    val userConfidence: StateFlow<Float> = _userConfidence.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _eyeOffsetX = MutableStateFlow(0f)
    val eyeOffsetX: StateFlow<Float> = _eyeOffsetX.asStateFlow()

    private val _eyeOffsetY = MutableStateFlow(0f)
    val eyeOffsetY: StateFlow<Float> = _eyeOffsetY.asStateFlow()
    
    private val _headRoll = MutableStateFlow(0f)
    val headRoll: StateFlow<Float> = _headRoll.asStateFlow()

    private val _inferenceLatencyMs = MutableStateFlow(0L)
    val inferenceLatencyMs: StateFlow<Long> = _inferenceLatencyMs.asStateFlow()

    private val _delegateName = MutableStateFlow("CPU")
    val delegateName: StateFlow<String> = _delegateName.asStateFlow()
    
    // Task 1 Refinement: Landmarks for overlay
    private val _faceLandmarks = MutableStateFlow<List<NormalizedLandmark>?>(null)
    val faceLandmarks: StateFlow<List<NormalizedLandmark>?> = _faceLandmarks.asStateFlow()
    
    // Task 6: Delegate Options
    val availableDelegates = listOf("CPU", "GPU", "NNAPI")

    private var inactivityTimerJob: Job? = null
    private var stateHoldJob: Job? = null
    private var isStateHeld = false

    init {
        resetInactivityTimer()
    }

    // ===== Public API =====
    
    fun updateLandmarks(landmarks: List<NormalizedLandmark>?) {
        _faceLandmarks.value = landmarks
    }
    
    fun setDelegate(delegate: String) {
        _delegateName.value = delegate
        // Actual switching handled in Activity/Classifier based on this state change or explicit call
        // For now, we update state, Activity observes and re-inits classifier
    }

    fun updateUserData(emotion: String, confidence: Float) {
        _userEmotion.value = emotion
        _userConfidence.value = confidence
    }

    fun onSignal(signal: RoboSignal) {
        when (signal) {
            is RoboSignal.AudioLevel -> {
                _audioAmplitude.value = signal.amplitude.coerceIn(0f, 1f)
            }
            is RoboSignal.TiltUpdate -> {
                _eyeOffsetX.value = signal.x.coerceIn(-1f, 1f)
                _eyeOffsetY.value = signal.y.coerceIn(-1f, 1f)
            }
            is RoboSignal.HeadRollUpdate -> {
                _headRoll.value = signal.roll
            }
            else -> processStateTransition(signal)
        }
    }

    fun updatePerformanceMetrics(latencyMs: Long, delegate: String) {
        _inferenceLatencyMs.value = latencyMs
        _delegateName.value = delegate
    }

    private fun processStateTransition(signal: RoboSignal) {
        val currentState = _roboState.value
        val newState = computeNextState(currentState, signal)
        
        if (newState != currentState) {
            if (isStateHeld && !isStrongSignal(signal)) {
                return
            }
            
            _roboState.value = newState
            
            if (newState is RoboState.Happy || 
                newState is RoboState.Angry || 
                newState is RoboState.Surprised || 
                newState is RoboState.Listening ||
                newState is RoboState.Sad) {
                holdState()
            }
        }
        
        if (signal !is RoboSignal.InactivityTimeout && signal !is RoboSignal.Silence) {
            resetInactivityTimer()
        }
    }

    private fun computeNextState(current: RoboState, signal: RoboSignal): RoboState {
        return when (signal) {
            // Face
            RoboSignal.FaceDetected -> when (current) {
                is RoboState.Sleep, is RoboState.Idle, is RoboState.Sad -> RoboState.Curious
                is RoboState.Happy, is RoboState.Angry, is RoboState.Surprised, is RoboState.Listening -> current 
                is RoboState.Curious -> current
            }
            RoboSignal.FaceLost -> when (current) {
                is RoboState.Curious -> RoboState.Idle
                is RoboState.Happy, is RoboState.Angry, is RoboState.Surprised -> current
                else -> current
            }
            
            // Emotions
            RoboSignal.SmileDetected -> RoboState.Happy
            RoboSignal.AngerDetected -> RoboState.Angry
            RoboSignal.SurpriseDetected -> RoboState.Surprised
            
            // Audio
            // Task 4: Surprised on sudden loud noise
            RoboSignal.LoudSound -> RoboState.Surprised 
            RoboSignal.SpeechDetected -> when (current) {
                is RoboState.Idle, is RoboState.Curious, is RoboState.Sad -> RoboState.Listening
                else -> current
            }
            RoboSignal.Silence -> when (current) {
                is RoboState.Angry, is RoboState.Listening, is RoboState.Surprised -> RoboState.Idle
                else -> current
            }
            
            // Sensors
            RoboSignal.Shake -> RoboState.Angry
            RoboSignal.ProximityClose -> RoboState.Sleep
            RoboSignal.ProximityFar -> when (current) {
                is RoboState.Sleep -> RoboState.Idle
                else -> current
            }
            
            // System
            RoboSignal.InactivityTimeout -> RoboState.Sleep
            RoboSignal.UserTap -> when (current) {
                is RoboState.Sleep -> RoboState.Idle
                else -> current
            }
            
            is RoboSignal.AudioLevel, is RoboSignal.TiltUpdate, is RoboSignal.HeadRollUpdate -> current
        }
    }

    private fun isStrongSignal(signal: RoboSignal): Boolean {
        return signal is RoboSignal.Shake || 
               signal is RoboSignal.LoudSound ||
               signal is RoboSignal.ProximityClose ||
               signal is RoboSignal.UserTap
    }

    private fun holdState() {
        isStateHeld = true
        stateHoldJob?.cancel()
        stateHoldJob = viewModelScope.launch {
            delay(STATE_HOLD_MS)
            isStateHeld = false
        }
    }

    private fun resetInactivityTimer() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = viewModelScope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            onSignal(RoboSignal.InactivityTimeout)
        }
    }

    override fun onCleared() {
        super.onCleared()
        inactivityTimerJob?.cancel()
        stateHoldJob?.cancel()
    }
}
