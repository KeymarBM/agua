package com.aguaalarma

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class ScanActivity : AppCompatActivity() {

    // Umbral del 40% para filtrar ruido sin ser excesivamente restrictivo
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.40f).build()
    )

    // Palabras clave específicas de vasos, tazas, botellas y agua/bebidas.
    // Se excluyeron explícitamente palabras genéricas como "container", "tableware", "dishware", "fluid"
    // que causaban falsos positivos inmediatos al encender la cámara.
    private val palabrasClave = listOf(
        "glass", "cup", "bottle", "water", "drink", "liquid", "mug",
        "beverage", "drinkware", "tumbler", "wine glass", "beer glass",
        "pitcher", "jug", "teacup", "coffee cup", "plastic bottle"
    )

    private var last = 0L
    private var hits = 0
    private var terminado = false
    private var isTorchOn = false
    private var camera: Camera? = null
    private var isTestMode = false

    private lateinit var status: TextView
    private lateinit var statusContainer: LinearLayout
    private lateinit var preview: PreviewView
    private lateinit var btnTorch: Button
    private lateinit var btnClose: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isTestMode = intent.getBooleanExtra("is_test", false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTestMode) finish()
            }
        })

        preview = PreviewView(this)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        // Guía visual en el centro (retícula para enfocar el vaso)
        val guideBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val guideBg = GradientDrawable().apply {
                setColor(Color.parseColor("#15FFFFFF"))
                setStroke(4, Color.parseColor("#80C8B6FF"))
                cornerRadius = 36f
            }
            background = guideBg
            setPadding(40, 40, 40, 40)
            addView(TextView(this@ScanActivity).apply {
                text = "🥛\nEnfoca tu vaso aquí"
                textSize = 15f
                setTextColor(Color.parseColor("#E0AAFF"))
                gravity = Gravity.CENTER
            })
        }
        val guideLp = FrameLayout.LayoutParams(540, 540, Gravity.CENTER)
        root.addView(guideBox, guideLp)

        // Barra superior con botones (Linterna y Apagar/Salir)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 70, 32, 16)
        }

        btnTorch = Button(this).apply {
            text = "🔦 Linterna"
            setTextColor(Color.WHITE)
            textSize = 13f
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#99240046"))
                setStroke(2, Color.parseColor("#7B2CBF"))
                cornerRadius = 24f
            }
            background = bg
            setPadding(32, 16, 32, 16)
            setOnClickListener { toggleTorch() }
        }
        val lpTorch = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 0f
        }
        topBar.addView(btnTorch, lpTorch)

        // Espacio flexible
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        btnClose = Button(this).apply {
            text = if (isTestMode) "✕ Salir" else "✕ Apagar alarma"
            setTextColor(Color.WHITE)
            textSize = 13f
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#CC9B1D20"))
                setStroke(2, Color.parseColor("#D90429"))
                cornerRadius = 24f
            }
            background = bg
            setPadding(32, 16, 32, 16)
            setOnClickListener { terminar(forzado = true) }
        }
        val lpClose = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 0f
        }
        topBar.addView(btnClose, lpClose)

        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Indicador de modo de prueba
        if (isTestMode) {
            val testBadge = TextView(this).apply {
                text = "🧪 MODO DE PRUEBA (Sin alarma sonora)"
                textSize = 13f
                setTextColor(Color.parseColor("#E0AAFF"))
                gravity = Gravity.CENTER
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#E6240046"))
                    setStroke(2, Color.parseColor("#9D4EDD"))
                    cornerRadius = 20f
                }
                background = bg
                setPadding(28, 12, 28, 12)
            }
            val lpBadge = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(0, 180, 0, 0)
            }
            root.addView(testBadge, lpBadge)
        }

        // Tarjeta inferior de estado con diseño elegante
        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 36, 40, 48)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F0140727"))
                setStroke(3, Color.parseColor("#5A189A"))
                cornerRadius = 32f
            }
            background = bg
        }

        status = TextView(this).apply {
            text = "💧 Apunta la cámara a un vaso con agua"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setLineSpacing(6f, 1f)
        }
        statusContainer.addView(status)

        val lpStatus = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(32, 0, 32, 48)
        }
        root.addView(statusContainer, lpStatus)

        setContentView(root)

        val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else status.text = "⚠️ Permiso de cámara requerido para escanear el vaso"
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ask.launch(Manifest.permission.CAMERA)
        }
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            btnTorch.text = "🚫 Sin flash"
            return
        }
        isTorchOn = !isTorchOn
        cam.cameraControl.enableTorch(isTorchOn)
        btnTorch.text = if (isTorchOn) "💡 Linterna: ON" else "🔦 Linterna"
    }

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            try {
                val cameraProvider = f.get()
                val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val an = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                an.setAnalyzer(ContextCompat.getMainExecutor(this)) { analyze(it) }

                cameraProvider.unbindAll()

                val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                camera = cameraProvider.bindToLifecycle(this, selector, pv, an)
            } catch (e: Exception) {
                e.printStackTrace()
                status.text = "⚠️ Error al iniciar la cámara: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val img = proxy.image
        val now = SystemClock.elapsedRealtime()

        if (img == null || terminado || now - last < 350) {
            proxy.close()
            return
        }
        last = now

        try {
            val inputImage = InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)
            labeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    if (terminado) return@addOnSuccessListener

                    val matches = labels.filter { label ->
                        val txt = label.text.lowercase()
                        palabrasClave.any { txt.contains(it) } && label.confidence >= 0.40f
                    }

                    if (matches.isNotEmpty()) {
                        hits++
                        val topMatch = matches.maxByOrNull { it.confidence } ?: matches.first()
                        val pct = (topMatch.confidence * 100).toInt()

                        if (hits < 2) {
                            status.text = "💧 ¡Vaso detectado! (${topMatch.text} $pct%)\nMantén enfocado... (1/2)"
                        } else {
                            terminado = true
                            // Éxito confirmado: actualizar UI a verde brillante
                            val successBg = GradientDrawable().apply {
                                setColor(Color.parseColor("#F01B4332"))
                                setStroke(3, Color.parseColor("#52B788"))
                                cornerRadius = 32f
                            }
                            statusContainer.background = successBg
                            status.text = if (isTestMode) {
                                "✅ ¡Vaso confirmado exitosamente! ($pct%)\nPrueba superada."
                            } else {
                                "✅ ¡Vaso confirmado! ($pct%)\nAlarma apagada. ¡Bien hecho!"
                            }

                            silenciarAlarma()

                            // Dar 1.2 segundos para que el usuario vea la confirmación visual de éxito
                            Handler(Looper.getMainLooper()).postDelayed({
                                finish()
                            }, 1200)
                        }
                    } else {
                        if (hits > 0) hits--
                        val viendo = labels.take(2).joinToString(", ") { "${it.text} (${(it.confidence * 100).toInt()}%)" }
                        status.text = if (viendo.isNotEmpty()) {
                            "💧 Apunta la cámara a un vaso con agua\n(Detectando: $viendo)"
                        } else {
                            "💧 Apunta la cámara a un vaso con agua"
                        }
                    }
                }
                .addOnCompleteListener {
                    proxy.close()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            proxy.close()
        }
    }

    private fun silenciarAlarma() {
        try {
            startService(Intent(this, AlarmService::class.java).setAction("STOP"))
            stopService(Intent(this, AlarmService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun terminar(forzado: Boolean = false) {
        if (terminado && !forzado) return
        terminado = true
        silenciarAlarma()
        finish()
    }

    override fun onDestroy() {
        try {
            if (isTorchOn) {
                camera?.cameraControl?.enableTorch(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
