package fr.montraitement

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Med(val id: String, val name: String, val dose: String, val time: String) {
    val minutes: Int get() = Store.minutes(time)
}

/** Données gardées sur l'appareil : la liste des médicaments et les prises cochées par jour. */
object Store {
    private const val PREFS = "traitement"

    private val DEFAULT = listOf(
        Med("ritaline", "RITALINE", "60 mg", "08:00"),
        Med("quetiapine", "QUETIAPINE", "600 mg", "21:30"),
        Med("teralithe", "TERALITHE", "1600 mg", "21:30"),
    )

    private val JOURS = arrayOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")
    private val MOIS = arrayOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet",
        "août", "septembre", "octobre", "novembre", "décembre")

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun minutes(t: String): Int {
        val p = t.split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun frTime(t: String): String {
        val p = t.split(":")
        return "${p.getOrNull(0)?.toIntOrNull() ?: 0}h${p.getOrNull(1) ?: "00"}"
    }

    fun nowHM(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    fun dayKey(offset: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun longDate(offset: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        return "${JOURS[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)} ${MOIS[cal.get(Calendar.MONTH)]}"
    }

    fun meds(c: Context): List<Med> {
        val raw = prefs(c).getString("meds", null) ?: return DEFAULT
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Med(o.getString("id"), o.getString("name"), o.getString("dose"), o.getString("time"))
            }.sortedWith(compareBy({ it.minutes }, { it.name }))
        } catch (e: Exception) {
            DEFAULT
        }
    }

    fun saveMeds(c: Context, list: List<Med>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("dose", it.dose).put("time", it.time))
        }
        prefs(c).edit().putString("meds", arr.toString()).apply()
    }

    /** id du médicament -> heure de prise ("HH:mm") pour le jour donné. */
    fun taken(c: Context, day: String = dayKey()): Map<String, String> {
        val raw = prefs(c).getString("j_$day", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setTaken(c: Context, medId: String, time: String?, day: String = dayKey()) {
        val m = taken(c, day).toMutableMap()
        if (time == null) m.remove(medId) else m[medId] = time
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        prefs(c).edit().putString("j_$day", o.toString()).commit()
    }

    /** Statut affiché : 0 = à venir, 1 = à prendre maintenant, 2 = en retard, 3 = pris. */
    fun status(m: Med, taken: Map<String, String>, isToday: Boolean = true): Int {
        if (taken[m.id] != null) return 3
        if (!isToday) return 2
        val diff = minutes(nowHM()) - m.minutes
        return when {
            diff < -30 -> 0
            diff < 120 -> 1
            else -> 2
        }
    }

    fun statusLabel(m: Med, taken: Map<String, String>, isToday: Boolean = true): String =
        when (status(m, taken, isToday)) {
            3 -> "Pris à " + frTime(taken[m.id] ?: "")
            1 -> "À prendre maintenant"
            2 -> if (isToday) "En retard (prévu à ${frTime(m.time)})" else "Non coché"
            else -> "À prendre à " + frTime(m.time)
        }

    fun newId(): String = "m" + System.currentTimeMillis().toString(36)
}
