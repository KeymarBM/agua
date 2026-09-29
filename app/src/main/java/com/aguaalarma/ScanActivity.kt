package com.aguaalarma

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class ScanActivity : AppCompatActivity() {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
    private val recipientes = setOf("glass", "cup", "drinkware", "bottle", "tableware", "mug", "water bottle")
    private val liquidos = setOf("water", "drink", "liquid", "fluid")
    private var last = 0L
    private var hits = 0
    private lateinit var status: TextView
    private lateinit var preview: PreviewView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // el botón atrás no hace nada: la alarma solo se apaga escaneando
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        preview = PreviewView(this)
        status = TextView(this).apply {
            text = "💧 Apunta la cámara a un vaso con agua"
            textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC7B2CBF")); setPadding(32, 48, 32, 48)
        }
        setContentView(FrameLayout(this).apply {
            addView(preview)
            addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        })
        val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startCamera() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera() else ask.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val an = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            an.setAnalyzer(ContextCompat.getMainExecutor(this)) { analyze(it) }
            f.get().unbindAll()
            f.get().bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pv, an)
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val img = proxy.image
        val now = SystemClock.elapsedRealtime()
        if (img == null || now - last < 600) { proxy.close(); return }
        last = now
        labeler.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { labels ->
                val names = labels.map { it.text.lowercase() }
                val ok = names.any { it in recipientes } && names.any { it in liquidos }
                hits = if (ok) hits + 1 else 0
                status.text = if (ok) "💧 ¡Vaso con agua detectado! ($hits/3)"
                              else "💧 Apunta la cámara a un vaso con agua"
                if (hits >= 3) terminar()
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun terminar() {
        startService(Intent(this, AlarmService::class.java).setAction("STOP"))
        finish()
    }
}
