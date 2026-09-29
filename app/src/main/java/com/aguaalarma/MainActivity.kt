package com.aguaalarma

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusBadge: TextView
    private lateinit var statusNextTime: TextView
    private lateinit var statusDesc: TextView
    private lateinit var btnToggle: Button
    private lateinit var alarmsContainer: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        val prefs = getSharedPreferences(Scheduler.PREFS_NAME, Context.MODE_PRIVATE)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#120726"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 72)
        }
        scroll.addView(root)

        // ===== 1. ENCABEZADO MODERNO =====
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val appTitle = TextView(this).apply {
            text = "💧 AguaAlarma"
            textSize = 28f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
        }
        headerLayout.addView(appTitle)

        val appSubtitle = TextView(this).apply {
            text = "Recordatorio inteligente de hidratación"
            textSize = 14f
            setTextColor(Color.parseColor("#C8B6FF"))
            setPadding(0, 4, 0, 14)
        }
        headerLayout.addView(appSubtitle)

        // Chip de meta diaria
        val metaChip = TextView(this).apply {
            text = "🎯 Meta diaria: 9 vasos de agua (aprox. 2.25 L)"
            textSize = 12f
            setTextColor(Color.parseColor("#E0AAFF"))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#2B124C"))
                setStroke(2, Color.parseColor("#5A189A"))
                cornerRadius = 24f
            }
            background = bg
            setPadding(28, 12, 28, 12)
        }
        headerLayout.addView(metaChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(headerLayout)

        // ===== 2. TARJETA PRINCIPAL DE ESTADO =====
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 36, 40, 36)
            val bg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#2B124C"), Color.parseColor("#1A0733"))
            ).apply {
                cornerRadius = 32f
                setStroke(3, Color.parseColor("#5A189A"))
            }
            background = bg
        }

        statusBadge = TextView(this).apply {
            textSize = 13f
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, 12)
        }
        statusCard.addView(statusBadge)

        val lblProxima = TextView(this).apply {
            text = "⏰ Próxima alarma programada:"
            textSize = 13f
            setTextColor(Color.parseColor("#C8B6FF"))
        }
        statusCard.addView(lblProxima)

        statusNextTime = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            setPadding(0, 4, 0, 12)
        }
        statusCard.addView(statusNextTime)

        statusDesc = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#C8B6FF"))
            setLineSpacing(4f, 1f)
            setPadding(0, 0, 0, 20)
        }
        statusCard.addView(statusDesc)

        btnToggle = Button(this).apply {
            textSize = 14f
            paint.isFakeBoldText = true
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)
        }
        statusCard.addView(btnToggle, LinearLayout.LayoutParams(-1, -2))

        val lpStatusCard = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 12
            bottomMargin = 36
        }
        root.addView(statusCard, lpStatusCard)

        // ===== 3. SECCIÓN DE HORARIOS (9 VASOS EDITABLES) =====
        val lblHorariosTitle = TextView(this).apply {
            text = "📅 Horarios de hidratación (9 vasos)"
            textSize = 18f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            setPadding(0, 8, 0, 4)
        }
        root.addView(lblHorariosTitle)

        val lblHorariosSub = TextView(this).apply {
            text = "Toca cualquier horario para personalizar la hora:"
            textSize = 13f
            setTextColor(Color.parseColor("#C8B6FF"))
            setPadding(0, 0, 0, 18)
        }
        root.addView(lblHorariosSub)

        alarmsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(alarmsContainer)

        // Botón para restablecer los 9 horarios a los valores predeterminados
        val btnReset = Button(this).apply {
            text = "🔄 Restablecer horarios por defecto"
            setTextColor(Color.parseColor("#E0AAFF"))
            textSize = 13f
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1F0A38"))
                setStroke(2, Color.parseColor("#5A189A"))
                cornerRadius = 24f
            }
            background = bg
            setPadding(32, 20, 32, 20)
            setOnClickListener {
                Scheduler.resetToDefaults(this@MainActivity)
                val isActive = prefs.getBoolean("active", true)
                if (isActive) {
                    try { Scheduler.scheduleAll(this@MainActivity) } catch (e: Exception) { e.printStackTrace() }
                }
                renderAlarmsList()
                updateStatusUI(isActive)
                Toast.makeText(this@MainActivity, "✅ Horarios restablecidos a los 9 predeterminados", Toast.LENGTH_SHORT).show()
            }
        }
        val lpReset = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 12
            bottomMargin = 36
        }
        root.addView(btnReset, lpReset)

        // ===== 4. ZONA DE PRUEBAS =====
        val testCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 32, 36, 32)
            val bg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#200B3B"), Color.parseColor("#150529"))
            ).apply {
                cornerRadius = 28f
                setStroke(2, Color.parseColor("#49177D"))
            }
            background = bg
        }

        val testTitle = TextView(this).apply {
            text = "🧪 Zona de pruebas"
            textSize = 16f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
        }
        testCard.addView(testTitle)

        val testDesc = TextView(this).apply {
            text = "Verifica que el escáner reconozca tu vaso o prueba la alarma sonora:"
            textSize = 12f
            setTextColor(Color.parseColor("#C8B6FF"))
            setPadding(0, 4, 0, 18)
        }
        testCard.addView(testDesc)

        // Botón: Probar escáner sin sonido de alarma
        val btnTestScanner = Button(this).apply {
            text = "📷 Probar escáner de vaso"
            setTextColor(Color.WHITE)
            textSize = 14f
            paint.isFakeBoldText = true
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#5A189A"))
                cornerRadius = 20f
            }
            background = bg
            setPadding(28, 22, 28, 22)
            setOnClickListener {
                val intent = Intent(this@MainActivity, ScanActivity::class.java).apply {
                    putExtra("is_test", true)
                }
                startActivity(intent)
            }
        }
        testCard.addView(btnTestScanner, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })

        // Botón: Probar alarma completa (sonido + escáner)
        val btnTestAlarm = Button(this).apply {
            text = "🔔 Probar alarma con sonido"
            setTextColor(Color.WHITE)
            textSize = 13f
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#381163"))
                setStroke(2, Color.parseColor("#7B2CBF"))
                cornerRadius = 20f
            }
            background = bg
            setPadding(28, 20, 28, 20)
            setOnClickListener {
                try {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, AlarmService::class.java)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        testCard.addView(btnTestAlarm, LinearLayout.LayoutParams(-1, -2))

        root.addView(testCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 })

        setContentView(scroll)

        // Toggle Activar / Desactivar alarmas
        btnToggle.setOnClickListener {
            val current = prefs.getBoolean("active", true)
            val newActive = !current
            prefs.edit().putBoolean("active", newActive).apply()
            if (newActive) {
                try { Scheduler.scheduleAll(this) } catch (e: Exception) { e.printStackTrace() }
                Toast.makeText(this, "✅ Alarmas activadas puntualmente.", Toast.LENGTH_SHORT).show()
            } else {
                try { Scheduler.cancelAll(this) } catch (e: Exception) { e.printStackTrace() }
                Toast.makeText(this, "Alarmas pausadas.", Toast.LENGTH_SHORT).show()
            }
            updateStatusUI(newActive)
        }

        // Estado inicial
        val initialActive = prefs.getBoolean("active", true)
        if (initialActive) {
            try { Scheduler.scheduleAll(this) } catch (e: Exception) { e.printStackTrace() }
        }
        updateStatusUI(initialActive)
        renderAlarmsList()

        // Permisos necesarios
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(android.net.Uri.parse("package:$packageName"))
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences(Scheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val active = prefs.getBoolean("active", true)
        updateStatusUI(active)
    }

    private fun updateStatusUI(active: Boolean) {
        if (active) {
            statusBadge.text = "🟢 ALARMAS ACTIVADAS"
            statusBadge.setTextColor(Color.parseColor("#52B788"))
            val nextTxt = Scheduler.getNextAlarmText(this)
            statusNextTime.text = if (nextTxt.isNotEmpty()) nextTxt else "Programadas diariamente"
            statusDesc.text = "Las 9 alarmas están activas. Sonarán puntualmente y se apagarán al escanear tu vaso con agua."
            btnToggle.text = "🔴 Pausar todas las alarmas"
            val bgBtn = GradientDrawable().apply {
                setColor(Color.parseColor("#999B1D20"))
                setStroke(2, Color.parseColor("#D90429"))
                cornerRadius = 20f
            }
            btnToggle.background = bgBtn
        } else {
            statusBadge.text = "⚪ ALARMAS PAUSADAS"
            statusBadge.setTextColor(Color.parseColor("#9E9E9E"))
            statusNextTime.text = "En pausa"
            statusDesc.text = "Presiona el botón para activar los recordatorios de agua diarios."
            btnToggle.text = "🟢 Activar las 9 alarmas"
            val bgBtn = GradientDrawable().apply {
                setColor(Color.parseColor("#2D6A4F"))
                cornerRadius = 20f
            }
            btnToggle.background = bgBtn
        }
    }

    private fun renderAlarmsList() {
        alarmsContainer.removeAllViews()
        val alarms = Scheduler.getAlarms(this)
        val prefs = getSharedPreferences(Scheduler.PREFS_NAME, Context.MODE_PRIVATE)

        alarms.forEachIndexed { idx, (h, m) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 24, 32, 24)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1B0A33"))
                    setStroke(2, Color.parseColor("#3B1469"))
                    cornerRadius = 20f
                }
                background = bg
            }

            // Indicador del vaso (ej: 💧 #1)
            val badgeNumber = TextView(this).apply {
                text = "💧 #${idx + 1}"
                textSize = 12f
                setTextColor(Color.parseColor("#E0AAFF"))
                paint.isFakeBoldText = true
                val badgeBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2D1152"))
                    cornerRadius = 14f
                }
                background = badgeBg
                setPadding(20, 10, 20, 10)
            }
            card.addView(badgeNumber)

            // Información de hora
            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 0, 0, 0)
            }

            val tvTime = TextView(this).apply {
                text = Scheduler.label(h, m)
                textSize = 18f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            }
            infoLayout.addView(tvTime)

            val tvVaso = TextView(this).apply {
                text = "1 vaso de agua"
                textSize = 12f
                setTextColor(Color.parseColor("#C8B6FF"))
            }
            infoLayout.addView(tvVaso)

            card.addView(infoLayout, LinearLayout.LayoutParams(0, -2, 1f))

            // Botón/Chip Editar
            val btnEdit = TextView(this).apply {
                text = "✏️ Cambiar"
                textSize = 12f
                setTextColor(Color.parseColor("#E0AAFF"))
                val editBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2F1254"))
                    setStroke(1, Color.parseColor("#5A189A"))
                    cornerRadius = 16f
                }
                background = editBg
                setPadding(24, 12, 24, 12)
            }
            card.addView(btnEdit)

            // Abrir selector de hora al presionar la tarjeta
            card.setOnClickListener {
                openTimePicker(idx, h, m)
            }

            val lpCard = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 14
            }
            alarmsContainer.addView(card, lpCard)
        }
    }

    private fun openTimePicker(index: Int, currentH: Int, currentM: Int) {
        val picker = TimePickerDialog(this, { _, selectedH, selectedM ->
            Scheduler.saveAlarm(this, index, selectedH, selectedM)
            val prefs = getSharedPreferences(Scheduler.PREFS_NAME, Context.MODE_PRIVATE)
            val isActive = prefs.getBoolean("active", true)
            if (isActive) {
                try {
                    Scheduler.schedule(this, index)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            renderAlarmsList()
            updateStatusUI(isActive)
            Toast.makeText(
                this,
                "⏰ Vaso #${index + 1} reprogramado para las ${Scheduler.label(selectedH, selectedM)}",
                Toast.LENGTH_SHORT
            ).show()
        }, currentH, currentM, false)

        picker.setTitle("Cambiar hora para Vaso #${index + 1}")
        picker.show()
    }
}
