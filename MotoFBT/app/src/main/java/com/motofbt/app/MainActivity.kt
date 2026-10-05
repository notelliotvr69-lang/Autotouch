package com.motofbt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.motofbt.app.databinding.ActivityMainBinding
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val executor=Executors.newSingleThreadExecutor()
    private val solver=TrackerSolver()
    private val osc=OscSender()
    private var landmarker: PoseLandmarker?=null
    private var frames=0; private var lastFps=SystemClock.elapsedRealtime()
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ if(it) startCamera() else binding.status.text="Camera permission required" }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding=ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        setupLandmarker()
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera() else permission.launch(Manifest.permission.CAMERA)
    }
    private fun setupLandmarker(){
        val base=BaseOptions.builder().setModelAssetPath("pose_landmarker_full.task").build()
        val opts=PoseLandmarker.PoseLandmarkerOptions.builder().setBaseOptions(base).setRunningMode(RunningMode.LIVE_STREAM).setNumPoses(1)
            .setMinPoseDetectionConfidence(0.45f).setMinPosePresenceConfidence(0.45f).setMinTrackingConfidence(0.45f)
            .setResultListener { result: PoseLandmarkerResult, _ -> onPose(result) }.build()
        landmarker=PoseLandmarker.createFromOptions(this,opts)
    }
    private fun startCamera(){
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider=future.get()
            val preview=Preview.Builder().build().also{it.setSurfaceProvider(binding.preview.surfaceProvider)}
            val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            analysis.setAnalyzer(executor){ image -> process(image) }
            provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_FRONT_CAMERA,preview,analysis)
        },ContextCompat.getMainExecutor(this))
    }
    private fun process(image: ImageProxy){
        try {
            val bmp=Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
            image.planes[0].buffer.rewind(); bmp.copyPixelsFromBuffer(image.planes[0].buffer)
            val mp=BitmapImageBuilder(bmp).build()
            landmarker?.detectAsync(mp,SystemClock.uptimeMillis())
        } catch(_:Throwable) {} finally { image.close() }
    }
    private fun onPose(result: PoseLandmarkerResult){
        val list=result.landmarks().firstOrNull() ?: return
        val pts=list.map{P3(it.x(),it.y(),it.z())}; val trackers=solver.solve(pts)
        for((id,p) in trackers) osc.send("/tracking/trackers/$id/position",p.x,p.y,p.z)
        frames++; val now=SystemClock.elapsedRealtime()
        if(now-lastFps>=1000){ val f=frames; frames=0; lastFps=now; runOnUiThread{binding.status.text="MotoFBT 0.2 • ${f} FPS • ${trackers.size} trackers"}}
    }
    override fun onDestroy(){super.onDestroy(); landmarker?.close(); executor.shutdownNow(); osc.close()}
}
