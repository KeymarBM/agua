package com.aguaalarma

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmService : Service() {
    private var player: MediaPlayer? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopAlarm(); return START_NOT_STICKY }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("agua", "Alarma del agua", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) })
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, ScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, "agua")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("💧 ¡Hora de tomar agua!")
            .setContentText("Escanea un vaso con agua para apagar la alarma")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, 1, n, type)
        startSound()
        return START_STICKY
    }

    private fun startSound() {
        if (player != null) return
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare(); start()
        }
        @Suppress("DEPRECATION")
        (getSystemService(VIBRATOR_SERVICE) as Vibrator)
            .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun stopAlarm() {
        player?.run { stop(); release() }; player = null
        @Suppress("DEPRECATION")
        (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
