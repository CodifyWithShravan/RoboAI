package com.example.roboai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roboai.state.RoboState
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

/**
 * Main Robo Face composable.
 */
@Composable
fun RoboFaceScreen(
    roboState: RoboState,
    audioAmplitude: Float,
    eyeOffsetX: Float,
    eyeOffsetY: Float,
    headRoll: Float,
    inferenceLatencyMs: Long,
    delegateName: String,
    userEmotion: String,
    userConfidence: Float,
    faceLandmarks: List<NormalizedLandmark>?, // Task 1 Refinement
    availableDelegates: List<String> = listOf("CPU", "GPU", "NNAPI"), // Task 6
    onDelegateSelected: (String) -> Unit = {}, // Task 6
    onTap: () -> Unit
) {
    var showDebug by remember { mutableStateOf(false) }

    // Animation values based on state
    val glowIntensity by animateFloatAsState(
        targetValue = when (roboState) {
            is RoboState.Happy -> 1f
            is RoboState.Angry -> 0.8f
            is RoboState.Surprised -> 0.9f
            is RoboState.Curious -> 0.6f
            is RoboState.Idle -> 0.4f
            is RoboState.Sad -> 0.3f
            is RoboState.Listening -> 0.5f + audioAmplitude * 0.5f
            is RoboState.Sleep -> 0.1f
        },
        animationSpec = tween(600),
        label = "glow"
    )
    
    val eyeScale by animateFloatAsState(
        targetValue = when (roboState) {
            is RoboState.Happy -> 1.1f
            is RoboState.Angry -> 0.7f
            is RoboState.Surprised -> 1.35f
            is RoboState.Curious -> 1.2f
            is RoboState.Sad -> 0.9f
            is RoboState.Sleep -> 0.15f
            is RoboState.Listening -> 1.05f
            is RoboState.Idle -> 1f
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "eyeScale"
    )

    // Continuous idle pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )
    
    val curiousRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "curiousRotation"
    )
    
    val mouthBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mouthBounce"
    )
    
    val circuitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "circuitRotation"
    )
    
    val stateColor by animateColorAsState(
        targetValue = Color(roboState.color),
        animationSpec = tween(400),
        label = "stateColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A1A),
                        Color(0xFF0D1B2A),
                        Color(0xFF1B2838)
                    )
                )
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onTap() }
    ) {
        // Overlay Info (Top Left)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clickable { showDebug = !showDebug }
        ) {
            Text(
                text = "RoboAI",
                color = Color(0xFF00E5FF),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Inference: ${inferenceLatencyMs}ms | $delegateName",
                color = Color(0xFF66FFFF).copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            
            if (showDebug) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "User: $userEmotion (${(userConfidence * 100).toInt()}%)",
                    color = Color.Yellow.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // State Badge (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(
                    stateColor.copy(alpha = 0.2f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${roboState.emoji} ${roboState.displayName}",
                color = stateColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Main Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 80.dp)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val faceSize = min(size.width, size.height) * 0.8f

            drawRoboFace(
                centerX = centerX,
                centerY = centerY,
                faceSize = faceSize,
                glowIntensity = glowIntensity,
                eyeScale = eyeScale,
                eyeOffsetX = eyeOffsetX,
                eyeOffsetY = eyeOffsetY,
                pulse = pulse,
                mouthBounce = mouthBounce,
                audioAmplitude = audioAmplitude,
                roboState = roboState,
                stateColor = stateColor,
                circuitRotation = circuitRotation,
                curiousRotation = if (roboState is RoboState.Curious) curiousRotation else 0f
            )
                if (showDebug) {
                    drawLandmarks(faceLandmarks, size)
                }
            }
        
        // Debug Controls (Delegates) - Visible when showDebug is true
        if (showDebug) {
             Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                availableDelegates.forEach { delegate ->
                    Button(
                        onClick = { onDelegateSelected(delegate) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (delegate == delegateName) Color(0xFF00E5FF) else Color.Gray
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(36.dp)
                    ) {
                        Text(text = delegate, fontSize = 10.sp)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (showDebug) "DEBUG: Tap title to hide details" else "Tap title for debug info",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to wake • Shake for angry • Cover for sleep",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun DrawScope.drawRoboFace(
    centerX: Float,
    centerY: Float,
    faceSize: Float,
    glowIntensity: Float,
    eyeScale: Float,
    eyeOffsetX: Float,
    eyeOffsetY: Float,
    pulse: Float,
    mouthBounce: Float,
    audioAmplitude: Float,
    roboState: RoboState,
    stateColor: Color,
    circuitRotation: Float,
    curiousRotation: Float
) {
    drawFaceOutline(centerX, centerY, faceSize, glowIntensity, stateColor, pulse)
    
    val eyeSpacing = faceSize * 0.2f
    val eyeY = centerY - faceSize * 0.08f
    val eyeRadius = faceSize * 0.1f
    
    // Check if eyes should be open (Not sleeping)
    val eyesOpen = roboState !is RoboState.Sleep
    
    rotate(degrees = curiousRotation, pivot = Offset(centerX, centerY)) {
        drawEye(centerX - eyeSpacing, eyeY, eyeRadius, eyeScale, eyeOffsetX, eyeOffsetY, glowIntensity, stateColor, circuitRotation, eyesOpen)
        drawEye(centerX + eyeSpacing, eyeY, eyeRadius, eyeScale, eyeOffsetX, eyeOffsetY, glowIntensity, stateColor, circuitRotation, eyesOpen)
    }
    
    drawNose(centerX, centerY + faceSize * 0.04f, faceSize * 0.04f, pulse, glowIntensity, stateColor)
    
    drawMouth(centerX, centerY + faceSize * 0.17f, faceSize * 0.28f, faceSize * 0.04f,
              audioAmplitude, mouthBounce, glowIntensity, roboState, stateColor)
}

/**
 * Task 1 Refinement: Draw debug landmarks overlay
 */
private fun DrawScope.drawLandmarks(landmarks: List<NormalizedLandmark>?, size: Size) {
    landmarks?.forEach { landmark ->
        // Landmarks are normalized 0..1, map to screen size
        // Note: Camera stream might be mirrored or different aspect ratio, 
        // but this gives a rough idea of what is tracked.
        val x = landmark.x() * size.width
        val y = landmark.y() * size.height
        
        drawCircle(
            color = Color.Green.copy(alpha = 0.5f),
            radius = 2f,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawFaceOutline(
    cx: Float, cy: Float, faceSize: Float,
    glow: Float, color: Color, pulse: Float
) {
    val radius = faceSize * 0.42f
    
    drawCircle(
        color = color.copy(alpha = glow * 0.15f * (0.8f + pulse * 0.2f)),
        radius = radius + 20f,
        center = Offset(cx, cy),
        style = Stroke(width = 8f)
    )
    
    drawCircle(
        color = color.copy(alpha = glow * 0.5f),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 3f)
    )
    
    drawCircle(
        color = color.copy(alpha = glow * 0.1f),
        radius = radius - 10f,
        center = Offset(cx, cy),
        style = Stroke(width = 1f)
    )
}

private fun DrawScope.drawEye(
    cx: Float, cy: Float, radius: Float,
    eyeScale: Float, offsetX: Float, offsetY: Float,
    glow: Float, stateColor: Color,
    circuitRotation: Float,
    isOpen: Boolean
) {
    val r = radius * eyeScale
    val ox = cx + offsetX * radius * 0.3f
    val oy = cy + offsetY * radius * 0.3f
    
    if (!isOpen) {
        // Draw closed eye (Horizontal line)
        drawLine(
            color = stateColor.copy(alpha = 0.7f),
            start = Offset(ox - r, oy),
            end = Offset(ox + r, oy),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        return
    }
    
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = glow * 0.5f),
        radius = r * 1.4f,
        center = Offset(ox, oy),
        style = Stroke(width = 3f)
    )
    
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = glow * 0.08f),
        radius = r * 1.4f,
        center = Offset(ox, oy)
    )
    
    drawCircle(
        color = Color(0xFF2196F3).copy(alpha = glow * 0.7f),
        radius = r,
        center = Offset(ox, oy),
        style = Stroke(width = 4f)
    )
    
    rotate(degrees = circuitRotation, pivot = Offset(ox, oy)) {
        val circuitColor = Color(0xFF00E5FF).copy(alpha = glow * 0.4f)
        for (i in 0 until 8) {
            val angle = i * 45f
            rotate(degrees = angle, pivot = Offset(ox, oy)) {
                drawLine(
                    color = circuitColor,
                    start = Offset(ox + r * 0.6f, oy),
                    end = Offset(ox + r * 0.9f, oy),
                    strokeWidth = 2f
                )
                drawCircle(
                    color = circuitColor,
                    radius = 2f,
                    center = Offset(ox + r * 0.95f, oy)
                )
            }
        }
    }
    
    drawCircle(
        color = Color(0xFF2196F3).copy(alpha = glow * 0.15f),
        radius = r,
        center = Offset(ox, oy)
    )
    
    drawCircle(
        color = Color.White.copy(alpha = glow * 0.9f),
        radius = r * 0.45f,
        center = Offset(ox, oy)
    )
    
    drawCircle(
        color = Color.White,
        radius = r * 0.15f,
        center = Offset(ox - r * 0.15f, oy - r * 0.15f)
    )
}

private fun DrawScope.drawNose(
    cx: Float, cy: Float, noseSize: Float,
    pulse: Float, glow: Float, stateColor: Color
) {
    val pulsedSize = noseSize * (1f + pulse * 0.15f)
    
    val nosePath = Path().apply {
        moveTo(cx, cy - pulsedSize)
        lineTo(cx - pulsedSize * 0.7f, cy + pulsedSize * 0.5f)
        moveTo(cx, cy - pulsedSize)
        lineTo(cx + pulsedSize * 0.7f, cy + pulsedSize * 0.5f)
    }
    
    drawPath(
        path = nosePath,
        color = Color(0xFF00E5FF).copy(alpha = glow * 0.6f),
        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
    )
    
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = glow * (0.8f + pulse * 0.2f)),
        radius = noseSize * 0.15f,
        center = Offset(cx, cy + pulsedSize * 0.8f)
    )
}

private fun DrawScope.drawMouth(
    cx: Float, cy: Float,
    mouthWidth: Float, barHeight: Float,
    audioAmplitude: Float, bounce: Float,
    glow: Float, roboState: RoboState, stateColor: Color
) {
    val barCount = 9
    val barWidth = mouthWidth / barCount * 0.7f
    val barGap = mouthWidth / barCount * 0.3f
    val totalWidth = barCount * (barWidth + barGap) - barGap
    val startX = cx - totalWidth / 2
    
    for (i in 0 until barCount) {
        val x = startX + i * (barWidth + barGap)
        val distFromCenter = abs(i - barCount / 2).toFloat() / (barCount / 2)
        val baseH = barHeight * (1f - distFromCenter * 0.3f)
        
        val h = when (roboState) {
            is RoboState.Happy -> {
                val phase = sin((i * 0.8f + bounce * Math.PI * 2).toFloat())
                baseH * (1.5f + phase * 0.5f + audioAmplitude * 1.5f)
            }
            is RoboState.Angry -> {
                val jag = if (i % 2 == 0) 1.8f else 0.6f
                baseH * jag * (1f + audioAmplitude)
            }
            is RoboState.Surprised -> {
                val ovalFactor = sqrt(1f - distFromCenter * distFromCenter)
                baseH * 2.5f * ovalFactor
            }
            is RoboState.Curious -> {
                baseH * (1f + audioAmplitude * 1.2f + sin(bounce * Math.PI.toFloat() * 2 + i * 0.5f) * 0.3f)
            }
            is RoboState.Listening -> {
                baseH * (0.5f + audioAmplitude * 2.5f + sin(bounce * Math.PI.toFloat() * 4 + i * 0.8f) * 0.2f)
            }
            is RoboState.Sad -> { // Dim, flat mouth
                baseH * 0.3f * (1f + audioAmplitude * 0.2f)
            }
            is RoboState.Sleep -> baseH * 0.2f
            is RoboState.Idle -> baseH * (0.8f + audioAmplitude * 1.5f)
        }
        
        val barColor = when (roboState) {
            is RoboState.Happy -> Color(0xFF4CAF50)
            is RoboState.Angry -> Color(0xFFF44336)
            is RoboState.Surprised -> Color(0xFFFF9800)
            is RoboState.Curious -> Color(0xFF2196F3)
            is RoboState.Listening -> Color(0xFF00E5FF)
            is RoboState.Sad -> Color(0xFF607D8B)
            is RoboState.Sleep -> Color(0xFF673AB7)
            is RoboState.Idle -> Color(0xFF00E5FF)
        }.copy(alpha = glow * 0.9f)
        
        drawRoundRect(
            color = barColor,
            topLeft = Offset(x, cy - h / 2),
            size = Size(barWidth, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
        )
        
        drawRoundRect(
            color = barColor.copy(alpha = barColor.alpha * 0.3f),
            topLeft = Offset(x - 2, cy - h / 2 - 2),
            size = Size(barWidth + 4, h + 4),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
    }
}
