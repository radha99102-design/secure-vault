package com.ankitsaini.securevault.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.ankitsaini.securevault.data.EventType
import com.ankitsaini.securevault.data.SecurityEvent
import com.ankitsaini.securevault.data.repository.SecurityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityRepository: SecurityRepository
) {
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    data class CameraResult(val success: Boolean, val photoPath: String? = null, val errorMessage: String? = null)

    suspend fun captureIntruderPhoto(packageName: String, onResult: (CameraResult) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onResult(CameraResult(false, errorMessage = "Permission Denied"))
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                val preview = Preview.Builder().build()
                
                val lifecycleOwner = object : LifecycleOwner {
                    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }
                    override val lifecycle: Lifecycle get() = registry
                }
                
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                
                val photoFile = File(context.filesDir, "security_photos").apply { if (!exists()) mkdirs() }
                    .let { File(it, "INTRUDER_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg") }
                
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture?.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        CoroutineScope(Dispatchers.Main).launch {
                            securityRepository.logFailedUnlock(packageName, "INTRUDER_PHOTO")
                            securityRepository.logEvent(SecurityEvent(packageName = packageName, eventType = EventType.INTRUDER_PHOTO_CAPTURED, photoPath = photoFile.absolutePath, wasSuccessful = true))
                            cameraProvider?.unbindAll()
                            onResult(CameraResult(true, photoPath = photoFile.absolutePath))
                        }
                    }
                    override fun onError(exc: ImageCaptureException) {
                        cameraProvider?.unbindAll()
                        onResult(CameraResult(false, errorMessage = exc.message))
                    }
                })
            } catch (e: Exception) {
                onResult(CameraResult(false, errorMessage = e.message))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun getIntruderPhotos(): List<File> = File(context.filesDir, "security_photos").let { if (it.exists()) it.listFiles()?.filter { f -> f.extension == "jpg" }?.sortedByDescending { f -> f.lastModified() } ?: emptyList() else emptyList() }
    fun deleteAllPhotos() = File(context.filesDir, "security_photos").apply { if (exists()) listFiles()?.forEach { it.delete() } }
}
