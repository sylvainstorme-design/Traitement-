package fr.montraitement

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import java.util.Calendar

/** Programme une vraie alarme à chaque heure de prise, et le passage au jour suivant à minuit. */
object Alarms {
    const val ACTION_ALARM = "fr.montraitement.ALARM"
    const val ACTION_MIDNIGHT = "fr.montraitement.MIDNIGHT"
    const val ACTION_TAKEN = "fr.montraitement.TAKEN"
    const val EXTRA_TIME = "time"

    private fun alarmIntent(c: Context, time: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .setData(Uri.parse("montraitement://alarme/$time"))
            .putExtra(EXTRA_TIME, time)
        return PendingIntent.getBroadcast(c, Store.minutes(time), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun nextAt(minutes: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun nextAlarmText(c: Context): String? {
        val next = Store.meds(c).map { it.time }.distinct()
            .map { nextAt(Store.minutes(it)) to it }
            .minByOrNull { it.first } ?: return null
        val at = Calendar.getInstance().apply { timeInMillis = next.first }
        val today = Calendar.getInstance()
        val word = if (at.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) "aujourd'hui" else "demain"
        return "$word à ${Store.frTime(next.second)}"
    }

    fun scheduleAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val prefs = c.getSharedPreferences("alarmes", Context.MODE_PRIVATE)
        prefs.getStringSet("times", emptySet())?.forEach { am.cancel(alarmIntent(c, it)) }

        val times = Store.meds(c).map { it.time }.distinct()
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val show = PendingIntent.getActivity(c, 7, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        times.forEach { t ->
            val at = nextAt(Store.minutes(t))
            val pi = alarmIntent(c, t)
            try {
                if (canExact) am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        prefs.edit().putStringSet("times", times.toSet()).apply()

        // Remise à zéro des cases à minuit (mise à jour du widget).
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 30); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val mi = PendingIntent.getBroadcast(c, 9999,
            Intent(c, AlarmReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight, mi)
    }
}

/** Notification d'alarme : sonne en boucle jusqu'à ce que vous touchiez « C'est pris » ou la fermiez. */
object Notify {
    private const val CHANNEL = "alarme_prise_v1"

    private fun channel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Alarme de prise", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Sonne à l'heure de chaque médicament"
        ch.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)
    }

    fun show(c: Context, time: String, due: List<Med>) {
        channel(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(c, 20, Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val takenIntent = PendingIntent.getBroadcast(c, 30 + Store.minutes(time),
            Intent(c, AlarmReceiver::class.java).setAction(Alarms.ACTION_TAKEN)
                .setData(Uri.parse("montraitement://pris/$time"))
                .putExtra(Alarms.EXTRA_TIME, time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = due.joinToString(", ") { "${it.name} ${it.dose}" }
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("C'est l'heure : ${Store.frTime(time)}")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText("$text\nTouchez « C'est pris » une fois le traitement pris."))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "C'est pris", takenIntent).build())
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        try { nm.notify(Store.minutes(time), n) } catch (e: SecurityException) { }
    }

    /** Arrête la sonnerie quand tous les médicaments de cette heure sont cochés. */
    fun cancelIfDone(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val taken = Store.taken(c)
        Store.meds(c).groupBy { it.time }.forEach { (t, list) ->
            if (list.all { taken[it.id] != null }) nm.cancel(Store.minutes(t))
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            Alarms.ACTION_ALARM -> {
                val time = intent.getStringExtra(Alarms.EXTRA_TIME) ?: return
                val taken = Store.taken(c)
                val due = Store.meds(c).filter { it.time == time && taken[it.id] == null }
                if (due.isNotEmpty()) Notify.show(c, time, due)
                MedWidget.updateAll(c)
                Alarms.scheduleAll(c)
            }
            Alarms.ACTION_TAKEN -> {
                val time = intent.getStringExtra(Alarms.EXTRA_TIME) ?: return
                val taken = Store.taken(c)
                Store.meds(c).filter { it.time == time && taken[it.id] == null }
                    .forEach { Store.setTaken(c, it.id, Store.nowHM()) }
                c.getSystemService(android.app.NotificationManager::class.java).cancel(Store.minutes(time))
                MedWidget.updateAll(c)
            }
            Alarms.ACTION_MIDNIGHT -> {
                MedWidget.updateAll(c)
                Alarms.scheduleAll(c)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Alarms.scheduleAll(c)
        MedWidget.updateAll(c)
    }
}
