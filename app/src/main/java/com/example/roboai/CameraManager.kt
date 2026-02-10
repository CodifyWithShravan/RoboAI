package com.example.roboai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX setup and lifecycle for camera preview and image analysis.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val frameListener: FrameListener
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private val rotationMatrix = Matrix()

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: run {
            Log.e(TAG, "Camera initialization failed")
            return
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        // Use FILL_CENTER - this fills the view and centers the image
        // This is the default for PreviewView and works well with front camera
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        
        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }

        // Image analysis - let CameraX handle rotation automatically
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, FrameAnalyzer())
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            Log.d(TAG, "Camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed: ${e.message}")
        }
    }

    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            var bitmap = imageProxy.toBitmap()
            
            // Apply rotation and mirror for front camera
            rotationMatrix.reset()
            rotationMatrix.postRotate(rotationDegrees.toFloat())
            // Mirror after rotation for front camera selfie view
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                rotationMatrix.postScale(-1f, 1f, bitmap.height / 2f, bitmap.width / 2f)
            } else {
                rotationMatrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
            
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true
            )
            
            frameListener.onFrame(
                rotatedBitmap, 
                imageProxy.imageInfo.timestamp,
                rotatedBitmap.width,
                rotatedBitmap.height
            )
            
            imageProxy.close()
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }

    interface FrameListener {
        fun onFrame(bitmap: Bitmap, timestampMs: Long, width: Int, height: Int)
    }
}
