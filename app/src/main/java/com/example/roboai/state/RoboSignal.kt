package com.example.roboai.state

/**
 * Input signals that drive state transitions in the Robo FSM.
 * Separates input events from state logic for clean architecture.
 */
sealed class RoboSignal {
    data object FaceDetected : RoboSignal()
    data object FaceLost : RoboSignal()
    
    // Emotions
    data object SmileDetected : RoboSignal() // Happy
    data object AngerDetected : RoboSignal() // Angry
    data object SurpriseDetected : RoboSignal() // Surprised (New)
    
    // Audio
    data object LoudSound : RoboSignal()
    data object SpeechDetected : RoboSignal()
    data object Silence : RoboSignal()
    
    // Sensors
    data object Shake : RoboSignal()
    data object ProximityClose : RoboSignal()
    data object ProximityFar : RoboSignal()
    
    // System
    data object InactivityTimeout : RoboSignal()
    data object UserTap : RoboSignal()
    
    // Data payloads
    data class AudioLevel(val amplitude: Float) : RoboSignal()
    data class TiltUpdate(val x: Float, val y: Float) : RoboSignal()
    data class HeadRollUpdate(val roll: Float) : RoboSignal()
}
