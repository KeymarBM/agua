package com.aguaalarma

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    // hora, minuto
    val HORARIO = listOf(
        13 to 0,   // 1:00 p. m.
        15 to 0,   // 3:00 p. m.
        17 to 0,   // 5:00 p. m.
        19 to 0,   // 7:00 p. m.
        21 to 0,   // 9:00 p. m.
        22 to 30,  // 10:30 p. m.
        23 to 30,  // 11:30 p. m.
        0 to 30,   // 12:30 a. m.
        1 to 15,   // 1:15 a. m.
        2 to 0     // 2:00 a. m.
    )

    fun label(h: Int, m: Int) =
        String.format("%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "a. m." else "p. m.")

    fun scheduleAll(ctx: Context) = HORARIO.indices.forEach { schedule(ctx, it) }

    fun schedule(ctx: Context, idx: Int) {
        val (h, m) = HORARIO[idx]
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fire = PendingIntent.getBroadcast(
            ctx, idx, Intent(ctx, AlarmReceiver::class.java).putExtra("idx", idx), flags)
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
