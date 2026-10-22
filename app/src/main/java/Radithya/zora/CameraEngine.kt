package Radithya.zora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class CameraEngine(private val context: Context) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var isBound = false

    private val tempPhotoFile: File by lazy {
        File(context.cacheDir, "zora_capture.jpg")
    }

    suspend fun start(lifecycleOwner: LifecycleOwner): Boolean {
        return suspendCancellableCoroutine { cont ->
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    imageCapture = ImageCapture.Builder().build()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture!!)
                    isBound = true
                    cont.resume(true)
                } catch (e: Exception) {
                    Log.e("ZoraCamera", "CameraX bind failed", e)
                    cont.resume(false)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    suspend fun capturePhoto(): Bitmap? {
        if (!isBound || imageCapture == null) return null
        return suspendCancellableCoroutine { cont ->
            val file = tempPhotoFile
            if (file.exists()) file.delete()
            val outputOptions = OutputFileOptions.Builder(file).build()
            imageCapture!!.takePicture(outputOptions, executor, object :
                ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    val path = savedUri?.path ?: file.absolutePath
                    val bitmap = decodeAndFixRotation(path)
                    cont.resume(bitmap)
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("ZoraCamera", "Capture error", exception)
                    cont.resume(null)
                }
            })
        }
    }

    private fun decodeAndFixRotation(filePath: String): Bitmap? {
        var bitmap: Bitmap? = null
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            val rotation = getRotation(filePath)
            var inSampleSize = 1
            val maxDim = 1280
            if (origWidth > maxDim || origHeight > maxDim) {
                val halfW = origWidth / 2
                val halfH = origHeight / 2
                while (halfW / inSampleSize >= maxDim && halfH / inSampleSize >= maxDim) {
                    inSampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            bitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null
            if (rotation != 0f && bitmap != null) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }
        } catch (e: Exception) {
            Log.e("ZoraCamera", "Decode failed", e)
        }
        return bitmap
    }

    private fun getRotation(filePath: String): Float {
        return try {
            ExifInterface(filePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL).let { orientation ->
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        } catch (e: Exception) {
            0f
        }
    }

    fun stop() {
        imageCapture = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        isBound = false
    }
}