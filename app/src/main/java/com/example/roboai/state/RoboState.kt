package com.example.roboai.state

/**
 * Finite State Machine states for the Robo AI.
 */
sealed class RoboState(
    val displayName: String,
    val emoji: String,
    val color: Long // ARGB color as Long for Compose Color
) {
    data object Idle : RoboState("Idle", "😐", 0xFF9E9E9E)
    data object Curious : RoboState("Curious", "🤔", 0xFF2196F3)
    data object Happy : RoboState("Happy", "😊", 0xFF4CAF50)
    data object Angry : RoboState("Angry", "😠", 0xFFF44336)
    data object Surprised : RoboState("Surprised", "😮", 0xFFFF9800)
    data object Sad : RoboState("Sad", "😢", 0xFF607D8B) // Added Sad
    data object Sleep : RoboState("Sleep", "😴", 0xFF673AB7)
    data object Listening : RoboState("Listening...", "👂", 0xFF00E5FF)
}
