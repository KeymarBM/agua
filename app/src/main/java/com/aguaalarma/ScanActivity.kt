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
import android.content.res.ColorStateList
import android.widget.Button
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
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.25f).build())
    private val palabrasClave = listOf(
        "glass", "cup", "bottle", "water", "drink", "liquid", "mug", "fluid",
        "beverage", "tableware", "drinkware", "tumbler", "jar", "pitcher", "stemware",
        "tea", "coffee", "dishware", "barware", "plastic bottle", "container", "jug", "decanter"
    )
    private var last = 0L
    private var terminado = false
    private lateinit var status: TextView
    private lateinit var preview: PreviewView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        preview = PreviewView(this)
        status = TextView(this).apply {
            text = "💧 Apunta la cámara a un vaso con agua"
            textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E61A0B2E")); setPadding(32, 40, 32, 40)
        }
        val btnApagar = Button(this).apply {
            text = "✕ Apagar alarma"
            setTextColor(Color.WHITE)
            textSize = 13f
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B3D90429"))
            setOnClickListener { terminar() }
        }
        val lpBtn = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, 100, 32, 0)
        }
        setContentView(FrameLayout(this).apply {
            addView(preview)
            addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            addView(btnApagar, lpBtn)
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
        if (img == null || terminado || now - last < 350) { proxy.close(); return }
        last = now
        labeler.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { labels ->
                if (terminado) return@addOnSuccessListener
                val matches = labels.filter { label ->
                    val txt = label.text.lowercase()
                    palabrasClave.any { txt.contains(it) }
                }
                if (matches.isNotEmpty()) {
                    val topMatch = matches.first()
                    val pct = (topMatch.confidence * 100).toInt()
                    status.text = "💧 ¡Vaso detectado! (${topMatch.text} $pct%)\n¡Alarma apagada!"
                    terminar()
                } else {
                    val viendo = labels.take(2).map { it.text }.joinToString(", ")
                    status.text = if (viendo.isNotEmpty()) {
                        "💧 Apunta la cámara a un vaso con agua\n(Detectando: $viendo)"
                    } else {
                        "💧 Apunta la cámara a un vaso con agua"
                    }
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun terminar() {
        if (terminado) return
        terminado = true
        try {
            startService(Intent(this, AlarmService::class.java).setAction("STOP"))
            stopService(Intent(this, AlarmService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
    }
}
