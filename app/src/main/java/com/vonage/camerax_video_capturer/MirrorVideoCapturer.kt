package com.vonage.camerax_video_capturer

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import android.util.SparseArray
import android.util.SparseIntArray
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.opentok.android.BaseVideoCapturer
import com.opentok.android.BaseVideoCapturer.CaptureSwitch
import com.opentok.android.Publisher.CameraCaptureFrameRate
import com.opentok.android.Publisher.CameraCaptureResolution
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias ImageListener = (image: ImageProxy) -> Unit

class MirrorVideoCapturer(
    ctx: Context,
    resolution: CameraCaptureResolution,
    fps: CameraCaptureFrameRate
) : BaseVideoCapturer(), CaptureSwitch {

    private val context = ctx
    private var cameraFrame: ImageProxy? = null
    private val frameDimensions: Size
    private val desiredFps: Int
    private var isCaptureStarted = false
    private var isFrontCamera = true

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraExecutor: ExecutorService

    @OptIn(ExperimentalCamera2Interop::class) private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(frameDimensions, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            // Image Analysis
            val imageAnalyzerBuilder = ImageAnalysis.Builder()

            // user extender to adjust frame rate
            val ext: Camera2Interop.Extender<*> = Camera2Interop.Extender(imageAnalyzerBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(desiredFps,desiredFps))

            val imageAnalyzer = imageAnalyzerBuilder
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setResolutionSelector(resolutionSelector)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FrameAnalyzer { image ->
                        cameraFrame = image
                        provideBufferFramePlanar(
                            image.planes[0].buffer,
                            image.planes[1].buffer,
                            image.planes[2].buffer,
                            image.planes[0].pixelStride,
                            image.planes[0].rowStride,
                            image.planes[1].pixelStride,
                            image.planes[1].rowStride,
                            image.planes[2].pixelStride,
                            image.planes[2].rowStride,
                            image.width,
                            image.height,
                            image.imageInfo.rotationDegrees,
                            isFrontCamera
                        )
                    })
                }

            // Select back camera as a default
            val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    context as LifecycleOwner, cameraSelector, imageAnalyzer)
                isCaptureStarted = true

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private fun stopCamera() {
        cameraProvider.unbindAll()
        isCaptureStarted = false
    }


    private class FrameAnalyzer(private val listener: ImageListener) : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            listener(image)
            image.close()
        }
    }

    /* Constructors etc... */
    init {
        frameDimensions = resolutionTable[resolution.ordinal]
        desiredFps = frameRateTable[fps.ordinal]
    }

    /**
     * Initializes the video capturer.
     */
    @Synchronized
    override fun init() {
        Log.d(TAG, "init() enter")
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * Starts capturing video.
     */
    @Synchronized
    override fun startCapture(): Int {
        startCamera()
        return 0
    }

    /**
     * Stops capturing video.
     */
    @Synchronized
    override fun stopCapture(): Int {
        stopCamera()
        return 0
    }

    /**
     * Destroys the BaseVideoCapturer object.
     */
    @Synchronized
    override fun destroy() {
        Log.d(TAG, "destroy() enter")
    }

    /**
     * Whether video is being captured (true) or not (false).
     */
    override fun isCaptureStarted(): Boolean {
        return isCaptureStarted
    }

    /**
     * Returns the settings for the video capturer.
     */
    @Synchronized
    override fun getCaptureSettings(): CaptureSettings {
        val captureSettings = CaptureSettings()
        captureSettings.fps = desiredFps
        captureSettings.width = if (null != cameraFrame) cameraFrame!!.width else 0
        captureSettings.height = if (null != cameraFrame) cameraFrame!!.height else 0
        captureSettings.format = NV21
        captureSettings.expectedDelay = 0
        return captureSettings
    }

    /**
     * Call this method when the activity pauses. When you override this method, implement code
     * to respond to the activity being paused. For example, you may pause capturing audio or video.
     *
     * @see .onResume
     */
    @Synchronized
    override fun onPause() {
        // PublisherKit.onPause() already calls setPublishVideo(false), which stops the camera
        // Nothing to do here
    }

    /**
     * Call this method when the activity resumes. When you override this method, implement code
     * to respond to the activity being resumed. For example, you may resume capturing audio
     * or video.
     *
     * @see .onPause
     */
    override fun onResume() {
        // PublisherKit.onResume() already calls setPublishVideo(true), which resumes the camera
        // Nothing to do here
    }

    @Synchronized
    override fun cycleCamera() {
        isFrontCamera = !isFrontCamera
        startCamera()
    }

    override fun getCameraIndex(): Int {
        return if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
    }

    @Synchronized
    override fun swapCamera(cameraId: Int) {
        isFrontCamera = !isFrontCamera
        startCamera()
    }

    companion object {
        private val TAG = MirrorVideoCapturer::class.java.simpleName

        private val resolutionTable: SparseArray<Size> = object : SparseArray<Size>() {
            init {
                append(CameraCaptureResolution.LOW.ordinal, Size(352, 288))
                append(CameraCaptureResolution.MEDIUM.ordinal, Size(640, 480))
                append(CameraCaptureResolution.HIGH.ordinal, Size(1280, 720))
                append(CameraCaptureResolution.HIGH_1080P.ordinal, Size(1920, 1080))
            }
        }
        private val frameRateTable: SparseIntArray = object : SparseIntArray() {
            init {
                append(CameraCaptureFrameRate.FPS_1.ordinal, 1)
                append(CameraCaptureFrameRate.FPS_7.ordinal, 7)
                append(CameraCaptureFrameRate.FPS_15.ordinal, 15)
                append(CameraCaptureFrameRate.FPS_30.ordinal, 30)
            }
        }
    }
}

