package com.example.roboai

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.atan2

/**
 * Emotion analyzer using MediaPipe blendshapes for accurate emotion detection.
 */
class EmotionAnalyzer {

    companion object {
        private const val TAG = "EmotionAnalyzer"
        
        // Reference landmarks for head pose
        private const val FOREHEAD = 10
        private const val CHIN = 152
        private const val LEFT_EAR = 234
        private const val RIGHT_EAR = 454
        private const val NOSE_TIP = 1
    }

    data class FaceMetrics(
        val smileScore: Float,
        val eyeWideScore: Float,
        val eyeSquintScore: Float,
        val mouthOpenScore: Float,
        val browDownScore: Float,
        val browUpScore: Float,
        val headYaw: Float,
        val headPitch: Float,
        val headRoll: Float
    )

    data class EmotionResult(
        val emotion: Emotion,
        val confidence: Float,
        val metrics: FaceMetrics
    )

    enum class Emotion {
        HAPPY, SURPRISED, ANGRY, NEUTRAL
    }

    fun analyze(result: FaceLandmarkerResult): EmotionResult? {
        if (result.faceLandmarks().isEmpty()) return null
        
        val landmarks = result.faceLandmarks()[0]
        val blendshapesOpt = result.faceBlendshapes()
        
        return if (blendshapesOpt.isPresent && blendshapesOpt.get().isNotEmpty()) {
            analyzeWithBlendshapes(blendshapesOpt.get()[0], landmarks)
        } else {
            analyzeWithLandmarks(landmarks)
        }
    }

    private fun analyzeWithBlendshapes(
        blendshapes: List<com.google.mediapipe.tasks.components.containers.Category>,
        landmarks: List<NormalizedLandmark>
    ): EmotionResult {
        // Build a map from category name to score
        val bs = mutableMapOf<String, Float>()
        for (category in blendshapes) {
            bs[category.categoryName()] = category.score()
        }
        
        // Extract key blendshape values
        val smileLeft = bs["mouthSmileLeft"] ?: 0f
        val smileRight = bs["mouthSmileRight"] ?: 0f
        val smileScore = (smileLeft + smileRight) / 2
        
        val jawOpen = bs["jawOpen"] ?: 0f
        val mouthOpenScore = jawOpen
        
        val eyeWideLeft = bs["eyeWideLeft"] ?: 0f
        val eyeWideRight = bs["eyeWideRight"] ?: 0f
        val eyeWideScore = (eyeWideLeft + eyeWideRight) / 2
        
        val eyeSquintLeft = bs["eyeSquintLeft"] ?: 0f
        val eyeSquintRight = bs["eyeSquintRight"] ?: 0f
        val eyeSquintScore = (eyeSquintLeft + eyeSquintRight) / 2
        
        val browDownLeft = bs["browDownLeft"] ?: 0f
        val browDownRight = bs["browDownRight"] ?: 0f
        val browDownScore = (browDownLeft + browDownRight) / 2
        
        val browInnerUp = bs["browInnerUp"] ?: 0f
        val browUpScore = browInnerUp
        
        val (yaw, pitch, roll) = extractHeadPose(landmarks)
        
        val metrics = FaceMetrics(
            smileScore, eyeWideScore, eyeSquintScore, mouthOpenScore,
            browDownScore, browUpScore, yaw, pitch, roll
        )
        
        // Classify emotion
        val (emotion, confidence) = classifyEmotionFromBlendshapes(metrics)
        
        Log.d(TAG, "Smile: ${"%.2f".format(smileScore)}, " +
              "EyeWide: ${"%.2f".format(eyeWideScore)}, " +
              "EyeSquint: ${"%.2f".format(eyeSquintScore)}, " +
              "MouthOpen: ${"%.2f".format(mouthOpenScore)}, " +
              "BrowDown: ${"%.2f".format(browDownScore)}, " +
              "BrowUp: ${"%.2f".format(browUpScore)} -> $emotion")
        
        return EmotionResult(emotion, confidence, metrics)
    }

    private fun classifyEmotionFromBlendshapes(m: FaceMetrics): Pair<Emotion, Float> {
        var happyScore = 0f
        var surprisedScore = 0f
        var angryScore = 0f
        var neutralScore = 0.3f
        
        // HAPPY: High smile score
        if (m.smileScore > 0.15f) happyScore += m.smileScore * 2f
        if (m.smileScore > 0.3f) happyScore += 0.3f
        if (m.smileScore > 0.5f) happyScore += 0.3f
        
        // SURPRISED: Wide eyes + open mouth + raised brows
        if (m.eyeWideScore > 0.15f) surprisedScore += m.eyeWideScore * 1.5f
        if (m.mouthOpenScore > 0.2f) surprisedScore += m.mouthOpenScore * 1.2f
        if (m.browUpScore > 0.15f) surprisedScore += m.browUpScore * 1.2f
        if (m.eyeWideScore > 0.25f && m.mouthOpenScore > 0.25f) {
            surprisedScore += 0.3f
        }
        
        // ANGRY: Squinted eyes + lowered brows + no smile
        if (m.eyeSquintScore > 0.15f && m.smileScore < 0.15f) {
            angryScore += m.eyeSquintScore * 1.5f
        }
        if (m.browDownScore > 0.15f) angryScore += m.browDownScore * 2f
        if (m.browDownScore > 0.25f && m.eyeSquintScore > 0.15f) {
            angryScore += 0.4f
        }
        if (m.mouthOpenScore < 0.1f && m.smileScore < 0.1f && m.browDownScore > 0.1f) {
            angryScore += 0.2f
        }
        
        // NEUTRAL: Low values across the board
        if (m.smileScore < 0.12f && m.eyeWideScore < 0.12f && 
            m.eyeSquintScore < 0.15f && m.browDownScore < 0.12f &&
            m.mouthOpenScore < 0.12f) {
            neutralScore += 0.4f
        }
        
        // Reduce neutral if other emotions are strong
        val emotionSum = happyScore + surprisedScore + angryScore
        if (emotionSum > 0.4f) neutralScore *= 0.5f
        
        val scores = listOf(
            Emotion.HAPPY to happyScore,
            Emotion.SURPRISED to surprisedScore,
            Emotion.ANGRY to angryScore,
            Emotion.NEUTRAL to neutralScore
        )
        
        val winner = scores.maxByOrNull { it.second }!!
        val total = scores.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.1f)
        val confidence = (winner.second / total).coerceIn(0.4f, 0.99f)
        
        return Pair(winner.first, confidence)
    }

    private fun analyzeWithLandmarks(landmarks: List<NormalizedLandmark>): EmotionResult {
        val (yaw, pitch, roll) = extractHeadPose(landmarks)
        val metrics = FaceMetrics(0f, 0f, 0f, 0f, 0f, 0f, yaw, pitch, roll)
        return EmotionResult(Emotion.NEUTRAL, 0.5f, metrics)
    }

    private fun extractHeadPose(landmarks: List<NormalizedLandmark>): Triple<Float, Float, Float> {
        val nose = landmarks[NOSE_TIP]
        val forehead = landmarks[FOREHEAD]
        val chin = landmarks[CHIN]
        val leftEar = landmarks[LEFT_EAR]
        val rightEar = landmarks[RIGHT_EAR]
        
        val earMidX = (leftEar.x() + rightEar.x()) / 2
        val yaw = (nose.x() - earMidX) * 100f
        
        val faceCenterY = (forehead.y() + chin.y()) / 2
        val pitch = (nose.y() - faceCenterY) * 100f
        
        val roll = atan2(
            (rightEar.y() - leftEar.y()).toDouble(),
            (rightEar.x() - leftEar.x()).toDouble()
        ).toFloat() * 57.3f
        
        return Triple(yaw, pitch, roll)
    }
}
