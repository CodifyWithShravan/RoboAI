package com.example.roboai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.min

/**
 * Custom View for drawing face mesh landmarks overlay on camera preview.
 * Coordinates are aligned with PreviewView's FILL_CENTER scaling.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: FaceLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var showLandmarks = true

    private val landmarkPaint = Paint().apply {
        color = Color.rgb(0, 255, 128)
        strokeWidth = 3f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val connectionPaint = Paint().apply {
        color = Color.rgb(100, 200, 255)
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        alpha = 200
        isAntiAlias = true
    }

    private val featurePaint = Paint().apply {
        color = Color.rgb(255, 100, 100)
        strokeWidth = 6f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val keyLandmarks = setOf(
        33, 133, 159, 145, 153, 144,
        263, 362, 386, 374, 380, 373,
        13, 14, 61, 291, 0, 17,
        105, 65, 334, 295,
        1, 4
    )

    private val connections = listOf(
        Pair(33, 133), Pair(133, 159), Pair(159, 145), Pair(145, 33),
        Pair(263, 362), Pair(362, 386), Pair(386, 374), Pair(374, 263),
        Pair(61, 146), Pair(146, 91), Pair(91, 181), Pair(181, 84),
        Pair(84, 17), Pair(17, 314), Pair(314, 405), Pair(405, 321),
        Pair(321, 375), Pair(375, 291), Pair(291, 61),
        Pair(10, 338), Pair(338, 297), Pair(297, 332), Pair(332, 284),
        Pair(284, 251), Pair(251, 389), Pair(389, 356), Pair(356, 454),
        Pair(454, 323), Pair(323, 361), Pair(361, 288), Pair(288, 397),
        Pair(397, 365), Pair(365, 379), Pair(379, 378), Pair(378, 400),
        Pair(400, 377), Pair(377, 152), Pair(152, 148), Pair(148, 176),
        Pair(176, 149), Pair(149, 150), Pair(150, 136), Pair(136, 172),
        Pair(172, 58), Pair(58, 132), Pair(132, 93), Pair(93, 234),
        Pair(234, 127), Pair(127, 162), Pair(162, 21), Pair(21, 54),
        Pair(54, 103), Pair(103, 67), Pair(67, 109), Pair(109, 10),
        Pair(168, 6), Pair(6, 197), Pair(197, 195), Pair(195, 5),
        Pair(70, 63), Pair(63, 105), Pair(105, 66), Pair(66, 107),
        Pair(300, 293), Pair(293, 334), Pair(334, 296), Pair(296, 336)
    )

    fun toggleLandmarks(): Boolean {
        showLandmarks = !showLandmarks
        invalidate()
        return showLandmarks
    }

    fun setShowLandmarks(show: Boolean) {
        showLandmarks = show
        invalidate()
    }

    fun setResults(result: FaceLandmarkerResult, imgWidth: Int, imgHeight: Int) {
        results = result
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    fun clear() {
        results = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!showLandmarks) return
        if (width == 0 || height == 0 || imageWidth == 0 || imageHeight == 0) return
        
        results?.let { result ->
            if (result.faceLandmarks().isEmpty()) return@let
            
            val landmarks = result.faceLandmarks()[0]
            val points = mutableListOf<PointF>()
            
            // Calculate FILL_CENTER scaling (same as PreviewView)
            // FILL_CENTER scales to fill the view while maintaining aspect ratio, then centers
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val imgWidth = imageWidth.toFloat()
            val imgHeight = imageHeight.toFloat()
            
            val viewAspect = viewWidth / viewHeight
            val imgAspect = imgWidth / imgHeight
            
            val scale: Float
            val offsetX: Float
            val offsetY: Float
            
            if (imgAspect > viewAspect) {
                // Image is wider - scale by height, crop width
                scale = viewHeight / imgHeight
                offsetX = (viewWidth - imgWidth * scale) / 2f
                offsetY = 0f
            } else {
                // Image is taller - scale by width, crop height
                scale = viewWidth / imgWidth
                offsetX = 0f
                offsetY = (viewHeight - imgHeight * scale) / 2f
            }
            
            // Convert normalized landmarks to screen coordinates
            for (landmark in landmarks) {
                val x = landmark.x() * imgWidth * scale + offsetX
                val y = landmark.y() * imgHeight * scale + offsetY
                points.add(PointF(x, y))
            }
            
            // Draw connections
            for ((start, end) in connections) {
                if (start < points.size && end < points.size) {
                    canvas.drawLine(
                        points[start].x, points[start].y,
                        points[end].x, points[end].y,
                        connectionPaint
                    )
                }
            }
            
            // Draw landmarks
            for ((index, point) in points.withIndex()) {
                val paint = if (index in keyLandmarks) featurePaint else landmarkPaint
                val radius = if (index in keyLandmarks) 4f else 2f
                canvas.drawCircle(point.x, point.y, radius, paint)
            }
        }
    }
}
