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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
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
            text = t; textSize = size; setTextColor(Color.parseColor(color)); setPadding(0, 16, 0, 16)
        }
        root.addView(tv("💧 Alarma del agua", 30f, "#FFFFFF"))
        Scheduler.HORARIO.forEach { (h, m) ->
            root.addView(tv("⏰ ${Scheduler.label(h, m)} → un vaso de agua", 18f, "#E0AAFF"))
        }
        val btn = Button(this).apply {
            text = "Probar alarma ahora"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#7B2CBF"))
            setOnClickListener {
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, AlarmService::class.java))
            }
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 32 }
        root.addView(btn, lp)
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

        try {
            Scheduler.scheduleAll(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
