package fr.montraitement

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/** Widget d'écran d'accueil : les médicaments du jour, à cocher d'un simple appui. */
class MedWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
        Alarms.scheduleAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val id = intent.getStringExtra(EXTRA_ID) ?: return
            if (Store.taken(context)[id] == null) {
                Store.setTaken(context, id, Store.nowHM())
                Notify.cancelIfDone(context)
            }
            updateAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "fr.montraitement.TOGGLE"
        const val EXTRA_ID = "id"

        fun updateAll(c: Context) {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, MedWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(c)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun openApp(c: Context, code: Int): PendingIntent =
            PendingIntent.getActivity(
                c, code, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun build(c: Context): RemoteViews {
            val meds = Store.meds(c)
            val taken = Store.taken(c)
            val rv = RemoteViews(c.packageName, R.layout.widget)
            rv.setTextViewText(R.id.w_title, Store.longDate())
            rv.setTextViewText(R.id.w_count, "${meds.count { taken[it.id] != null }}/${meds.size} pris")
            rv.setOnClickPendingIntent(R.id.w_header, openApp(c, 0))
            rv.removeAllViews(R.id.w_rows)

            meds.forEachIndexed { i, m ->
                val row = RemoteViews(c.packageName, R.layout.widget_row)
                val st = Store.status(m, taken)
                row.setTextViewText(R.id.row_name, "${m.name}  ${m.dose}")
                row.setTextViewText(R.id.row_info, "${Store.frTime(m.time)} · ${Store.statusLabel(m, taken)}")
                row.setTextViewText(R.id.row_box, if (st == 3) "✓" else "")
                row.setInt(R.id.row_box, "setBackgroundResource", if (st == 3) R.drawable.box_done else R.drawable.box_empty)
                row.setInt(
                    R.id.row_root, "setBackgroundResource",
                    when (st) { 3 -> R.drawable.row_done; 1 -> R.drawable.row_now; 2 -> R.drawable.row_late; else -> R.drawable.row_todo }
                )
                val pi = if (st == 3) {
                    openApp(c, 100 + i)
                } else {
                    val intent = Intent(c, MedWidget::class.java)
                        .setAction(ACTION_TOGGLE)
                        .setData(Uri.parse("montraitement://prise/${m.id}"))
                        .putExtra(EXTRA_ID, m.id)
                    PendingIntent.getBroadcast(c, 1000 + i, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
                row.setOnClickPendingIntent(R.id.row_root, pi)
                rv.addView(R.id.w_rows, row)
            }
            return rv
        }
    }
}
