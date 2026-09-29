package com.aguaalarma

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusTitle: TextView
    private lateinit var statusNext: TextView
    private lateinit var statusDesc: TextView
    private lateinit var btnToggle: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("agua_prefs", Context.MODE_PRIVATE)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A0B2E"))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 80, 56, 56)
        }
        scroll.addView(root)

        fun tv(t: String, size: Float, color: String) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.parseColor(color)); setPadding(0, 8, 0, 8)
        }
        root.addView(tv("💧 Alarma del agua", 28f, "#FFFFFF"))

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 28, 36, 28)
            setBackgroundColor(Color.parseColor("#33240046"))
        }
        statusTitle = TextView(this).apply { textSize = 16f }
        statusNext = TextView(this).apply { textSize = 14f; setTextColor(Color.WHITE); setPadding(0, 8, 0, 0) }
        statusDesc = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#C8B6FF")); setPadding(0, 8, 0, 0) }
        statusCard.addView(statusTitle)
        statusCard.addView(statusNext)
        statusCard.addView(statusDesc)
        root.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16; bottomMargin = 20 })

        btnToggle = Button(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        val lpBtn = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8; bottomMargin = 28 }
        root.addView(btnToggle, lpBtn)

        fun updateUI(active: Boolean) {
            if (active) {
                statusTitle.text = "🟢 ALARMAS ACTIVADAS"
                statusTitle.setTextColor(Color.parseColor("#52B788"))
                val nextTxt = Scheduler.getNextAlarmText()
                statusNext.text = if (nextTxt.isNotEmpty()) "⏰ Próxima alarma: $nextTxt" else "⏰ Programadas diariamente"
                statusNext.visibility = TextView.VISIBLE
                statusDesc.text = "Las alarmas están listas. Sonarán puntualmente en sus horas correspondientes."
                btnToggle.text = "🔴 Desactivar alarmas"
                btnToggle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#99D90429"))
            } else {
                statusTitle.text = "⚪ ALARMAS DESACTIVADAS"
                statusTitle.setTextColor(Color.parseColor("#AAAAAA"))
                statusNext.visibility = TextView.GONE
                statusDesc.text = "Presiona el botón de abajo para activar los recordatorios de agua."
                btnToggle.text = "🟢 ACTIVAR ALARMAS"
                btnToggle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2D6A4F"))
            }
        }

        btnToggle.setOnClickListener {
            val current = prefs.getBoolean("active", true)
            val newActive = !current
            prefs.edit().putBoolean("active", newActive).apply()
            if (newActive) {
                try { Scheduler.scheduleAll(this) } catch (e: Exception) { e.printStackTrace() }
                Toast.makeText(this, "✅ Alarmas activadas. Sonarán a las horas correspondientes.", Toast.LENGTH_LONG).show()
            } else {
                try { Scheduler.cancelAll(this) } catch (e: Exception) { e.printStackTrace() }
                Toast.makeText(this, "Alarmas desactivadas.", Toast.LENGTH_SHORT).show()
            }
            updateUI(newActive)
        }

        val initialActive = prefs.getBoolean("active", true)
        if (initialActive) {
            try { Scheduler.scheduleAll(this) } catch (e: Exception) { e.printStackTrace() }
        }
        updateUI(initialActive)

        root.addView(tv("📅 Horarios programados:", 16f, "#FFFFFF"))
        Scheduler.HORARIO.forEach { (h, m) ->
            root.addView(tv("⏰ ${Scheduler.label(h, m)} → un vaso de agua", 15f, "#E0AAFF"))
        }

        setContentView(scroll)

        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(android.net.Uri.parse("package:$packageName")))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
