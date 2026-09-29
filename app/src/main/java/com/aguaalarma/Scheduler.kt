package com.aguaalarma

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    const val PREFS_NAME = "agua_prefs"
    const val ALARMS_COUNT = 9

    // Horarios predeterminados solicitados: cada 1 hora y media desde 1:00 p. m. hasta 1:00 a. m.
    val DEFAULT_HORARIO = listOf(
        13 to 0,   // 1:00 p. m.  → 1 vaso
        14 to 30,  // 2:30 p. m.  → 1 vaso
        16 to 0,   // 4:00 p. m.  → 1 vaso
        17 to 30,  // 5:30 p. m.  → 1 vaso
        19 to 0,   // 7:00 p. m.  → 1 vaso
        20 to 30,  // 8:30 p. m.  → 1 vaso
        22 to 0,   // 10:00 p. m. → 1 vaso
        23 to 30,  // 11:30 p. m. → 1 vaso
        1 to 0     // 1:00 a. m.  → 1 vaso
    )

    fun label(h: Int, m: Int): String {
        val displayH = if (h % 12 == 0) 12 else h % 12
        val amPm = if (h < 12) "a. m." else "p. m."
        return String.format("%d:%02d %s", displayH, m, amPm)
    }

    fun getAlarms(ctx: Context): List<Pair<Int, Int>> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (0 until ALARMS_COUNT).map { idx ->
            val defaultTime = DEFAULT_HORARIO[idx]
            val h = prefs.getInt("alarm_${idx}_h", defaultTime.first)
            val m = prefs.getInt("alarm_${idx}_m", defaultTime.second)
            h to m
        }
    }

    fun saveAlarm(ctx: Context, idx: Int, hour: Int, minute: Int) {
        if (idx !in 0 until ALARMS_COUNT) return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("alarm_${idx}_h", hour)
            .putInt("alarm_${idx}_m", minute)
            .apply()
    }

    fun resetToDefaults(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (idx in 0 until ALARMS_COUNT) {
            editor.remove("alarm_${idx}_h")
            editor.remove("alarm_${idx}_m")
        }
        editor.apply()
    }

    fun getNextAlarmText(ctx: Context): String {
        val now = System.currentTimeMillis()
        val alarms = getAlarms(ctx)
        val nextTime = alarms.map { (h, m) ->
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                while (timeInMillis <= now + 1000) add(Calendar.DAY_OF_YEAR, 1)
            }
        }.minByOrNull { it.timeInMillis } ?: return ""

        val todayCal = Calendar.getInstance()
        val isToday = nextTime.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) &&
                      nextTime.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
        val dayStr = if (isToday) "hoy" else "mañana"
        val h = nextTime.get(Calendar.HOUR_OF_DAY)
        val m = nextTime.get(Calendar.MINUTE)
        return "$dayStr a las ${label(h, m)}"
    }

    fun scheduleAll(ctx: Context) {
        for (i in 0 until ALARMS_COUNT) {
            schedule(ctx, i)
        }
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        for (idx in 0 until ALARMS_COUNT) {
            val fire = PendingIntent.getBroadcast(
                ctx, idx, Intent(ctx, AlarmReceiver::class.java).putExtra("idx", idx), flags
            )
            try {
                am.cancel(fire)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun schedule(ctx: Context, idx: Int) {
        val alarms = getAlarms(ctx)
        if (idx !in alarms.indices) return
        val (h, m) = alarms[idx]
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fire = PendingIntent.getBroadcast(
            ctx, idx, Intent(ctx, AlarmReceiver::class.java).putExtra("idx", idx), flags
        )
        val show = PendingIntent.getActivity(ctx, 100, Intent(ctx, MainActivity::class.java), flags)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), fire)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, fire)
                }
            } else {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), fire)
            }
        } catch (e: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, fire)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
