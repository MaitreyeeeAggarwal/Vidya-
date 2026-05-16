package com.vidya.core.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Low-memory CameraX capture engine optimised for handwritten-doubt photography.
 *
 * Configuration choices:
 *   - 720p (1280×720) target resolution → crisp text contours, ~150–300 KB per JPEG.
 *   - JPEG quality 75 → excellent OCR fidelity, minimal storage footprint.
 *   - CAPTURE_MODE_MINIMIZE_LATENCY → shutter-to-callback in <200 ms on mid-range SoCs.
 *
 * The engine does NOT block the UI thread. The [cameraExecutor] runs all I/O
 * on a dedicated background thread, and the caller receives results via callbacks.
 */
class VidyaCameraEngine(
    private val context: Context,
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
) {
    companion object {
        private const val TAG = "VidyaCamera"
        private const val TARGET_WIDTH = 1280
        private const val TARGET_HEIGHT = 720
        private const val JPEG_QUALITY = 75
    }

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Directory where all doubt images are stored */
    private val storageDir: File by lazy {
        File(context.filesDir, "doubt_images").apply { mkdirs() }
    }

    /**
     * Initialise the CameraX pipeline and bind it to the given [lifecycleOwner].
     *
     * @param lifecycleOwner  The Activity or Fragment that owns the camera lifecycle.
     * @param previewSurface  Optional [Preview.SurfaceProvider] for live viewfinder display.
     */
    fun initialize(lifecycleOwner: LifecycleOwner, previewSurface: Preview.SurfaceProvider? = null) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Build the capture use-case
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(TARGET_WIDTH, TARGET_HEIGHT))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(JPEG_QUALITY)
                .build()

            // Optional live preview (teacher can frame the shot)
            val preview = previewSurface?.let {
                Preview.Builder()
                    .setTargetResolution(Size(TARGET_WIDTH, TARGET_HEIGHT))
                    .build()
                    .also { p -> p.setSurfaceProvider(it) }
            }

            // Use the rear camera (better macro focus for notebook photos)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                if (preview != null) {
                    cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture!!)
                } else {
                    cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture!!)
                }
                Log.d(TAG, "CameraX bound → ${TARGET_WIDTH}x${TARGET_HEIGHT} @ JPEG $JPEG_QUALITY")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use-cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Snap a photo of the student's handwritten problem.
     *
     * The captured JPEG is written to internal storage and the callback
     * delivers the [File] reference. The UI thread is never blocked.
     *
     * @param targetDoubtId  Unique doubt ID used to name the output file.
     * @param onImageSaved   Called on the main thread with the saved JPEG.
     * @param onError        Called on the main thread if the capture fails.
     */
    fun captureHandwrittenProblem(
        targetDoubtId: String,
        onImageSaved: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("CameraX not initialised — call initialize() first"))
            return
        }

        val outputFile = File(storageDir, "IMG_${targetDoubtId}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    val size = outputFile.length()
                    Log.d(TAG, "Doubt image saved → ${outputFile.name} (${size / 1024} KB)")
                    // Deliver result on the main thread so the caller can update UI safely
                    ContextCompat.getMainExecutor(context).execute { onImageSaved(outputFile) }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed for doubt=$targetDoubtId", exception)
                    ContextCompat.getMainExecutor(context).execute { onError(exception) }
                }
            }
        )
    }

    /** Release camera resources. Call from onDestroy(). */
    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        Log.d(TAG, "Camera engine shut down")
    }
}
