package com.payudon.monitor.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import com.payudon.monitor.R
import com.payudon.monitor.databinding.FragmentHome2Binding
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.rtmp.RtmpCamera1
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class HomeFragment2 : Fragment(),ConnectCheckerRtmp {

    private var _binding: FragmentHome2Binding? = null
    private val binding get() = _binding!!
    private val TAG = "HomeFragment"
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHome2Binding.inflate(inflater, container, false)
        binding.imageCaptureButton.setOnClickListener { takePhoto() }
        binding.videoCaptureButton.setOnClickListener { captureVideo() }
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
        return binding.root
    }

    private fun startCamera() {
        // 用于将相机的生命周期绑定到生命周期所有者(MainActivity)。 这消除了打开和关闭相机的任务，因为 CameraX 具有生命周期感知能力。
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        // 向 cameraProviderFuture 添加监听器。添加 Runnable 作为一个参数。我们会在稍后填写它。添加 ContextCompat.getMainExecutor() 作为第二个参数。这将返回一个在主线程上运行的 Executor。
        cameraProviderFuture.addListener({
            // 将相机的生命周期绑定到应用进程中的 LifecycleOwner。
            val cameraProvider = cameraProviderFuture.get()
            //预览
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(binding.container.surfaceProvider) }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            //拍照
            imageCapture = ImageCapture.Builder().build()
            val recorder =
                Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build()
            //摄像
            videoCapture = VideoCapture.withOutput(recorder)
            try {
                cameraProvider.unbindAll() // Unbind use cases before rebinding
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc) // 有多种原因可能会导致此代码失败，例如应用不再获得焦点。在此记录日志。
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("SimpleDateFormat")
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        // 存图路径和参数（时间、文件类型）
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS").format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        // 我们希望将输出保存在 MediaStore 中，以便其他应用可以显示它
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireActivity().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
        // 拍照后的回调函数
        imageCapture.takePicture(outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${outputFileResults.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }

    @SuppressLint("SimpleDateFormat")
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return
        binding.videoCaptureButton.isEnabled = false
        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }
        /*val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS").format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }
        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            requireActivity().contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()*/
        val rtmpCamera = RtmpCamera1(requireContext(),this)
        rtmpCamera.setReTries(10)
        rtmpCamera.setAuthorization("your_user_name", "your_password")
        rtmpCamera.startStream("rtmp://your_streaming_server_address")
        val fileOutputOptions = FileOutputOptions.Builder(File("")).build()

        recording =
            videoCapture.output.prepareRecording(requireContext(), fileOutputOptions).apply {
                if (PermissionChecker.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.RECORD_AUDIO
                    ) == PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }.start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg =
                                "Video capture succeeded: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                        }
                        binding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onAuthErrorRtmp() {
        TODO("Not yet implemented")
    }

    override fun onAuthSuccessRtmp() {
        TODO("Not yet implemented")
    }

    override fun onConnectionFailedRtmp(reason: String) {
        TODO("Not yet implemented")
    }

    override fun onConnectionStartedRtmp(rtmpUrl: String) {
        TODO("Not yet implemented")
    }

    override fun onConnectionSuccessRtmp() {
        TODO("Not yet implemented")
    }

    override fun onDisconnectRtmp() {
        TODO("Not yet implemented")
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
        TODO("Not yet implemented")
    }
}