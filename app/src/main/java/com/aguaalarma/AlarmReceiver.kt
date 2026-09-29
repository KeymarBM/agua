package com.aguaalarma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.schedule(c, i.getIntExtra("idx", 0)) // reprograma para mañana
        ContextCompat.startForegroundService(c, Intent(c, AlarmService::class.java))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Scheduler.scheduleAll(c)
}
