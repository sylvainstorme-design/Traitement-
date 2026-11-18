package fr.montraitement

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Écran de l'application : le jour, les médicaments à cocher, l'historique et la liste à modifier. */
class MainActivity : Activity() {

    private val ink = Color.parseColor("#14212B")
    private val muted = Color.parseColor("#5B6B78")
    private val accent = Color.parseColor("#1F6F8B")
    private var offset = 0 // 0 = aujourd'hui, -1 = hier...

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Alarms.scheduleAll(this)
    }

    override fun onResume() {
        super.onResume()
        Notify.cancelIfDone(this)
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(fill: String, stroke: String, radius: Int = 18) = GradientDrawable().apply {
        setColor(Color.parseColor(fill)); setStroke(dp(2), Color.parseColor(stroke)); cornerRadius = dp(radius).toFloat()
    }

    private fun text(s: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 17f; isAllCaps = false
        setTextColor(if (primary) Color.WHITE else accent)
        background = if (primary) box("#1F6F8B", "#1F6F8B", 14) else box("#FFFFFF", "#D9E1E7", 14)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        setOnClickListener { onClick() }
    }

    private fun gap(v: View, top: Int = 12): View {
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }
        return v
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(40))
        }
        val day = Store.dayKey(offset)
        val isToday = offset == 0
        val meds = Store.meds(this)
        val taken = Store.taken(this, day)

        // En-tête : jour et flèches
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nav.addView(button("‹") { offset--; render() })
        nav.addView(text(Store.longDate(offset), 28f, ink, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        })
        nav.addView(button("›") { if (offset < 0) { offset++; render() } }.apply { isEnabled = offset < 0; alpha = if (offset < 0) 1f else 0.3f })
        root.addView(nav)

        val count = meds.count { taken[it.id] != null }
        val sub = (if (isToday) "Aujourd'hui" else if (offset == -1) "Hier" else "Jour passé") + " · $count sur ${meds.size} pris"
        root.addView(gap(text(sub, 17f, muted).apply { gravity = Gravity.CENTER }, 6))
        if (isToday) Alarms.nextAlarmText(this)?.let {
            root.addView(gap(text("Prochaine alarme : $it", 16f, accent).apply { gravity = Gravity.CENTER }, 2))
        }

        if (meds.isEmpty()) {
            root.addView(gap(text("Aucun médicament. Touchez « Modifier la liste » pour en ajouter.", 18f, muted), 24))
        }

        var lastSlot = ""
        meds.forEach { m ->
            val slot = if (m.minutes < 15 * 60) "Matin" else "Soir"
            if (slot != lastSlot) {
                root.addView(gap(text(slot, 22f, ink, true), 24)); lastSlot = slot
            }
            val st = Store.status(m, taken, isToday)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = when (st) {
                    3 -> box("#E1F3E9", "#1D7A4D")
                    1 -> box("#FFF1CF", "#8A5A00")
                    2 -> box("#FBE9E1", "#B5481D")
                    else -> box("#FFFFFF", "#D9E1E7")
                }
            }
            val check = text(if (st == 3) "✓" else "", 30f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = if (st == 3) box("#1D7A4D", "#1D7A4D", 14) else box("#FFFFFF", "#5B6B78", 14)
                layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
            }
            card.addView(check)
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(text(m.name, 22f, ink, true))
            info.addView(text("${m.dose} · ${Store.frTime(m.time)}", 18f))
            val stColor = when (st) { 3 -> "#1D7A4D"; 1 -> "#8A5A00"; 2 -> "#B5481D"; else -> "#5B6B78" }
            info.addView(text(Store.statusLabel(m, taken, isToday), 16f, Color.parseColor(stColor), st != 0))
            card.addView(info)
            card.setOnClickListener {
                if (taken[m.id] == null) {
                    Store.setTaken(this, m.id, if (isToday) Store.nowHM() else m.time, day)
                    Notify.cancelIfDone(this)
                    MedWidget.updateAll(this)
                    Toast.makeText(this, "Coché : ${m.name}", Toast.LENGTH_SHORT).show()
                    render()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Décocher ${m.name} ?")
                        .setMessage("À faire seulement si vous l'avez coché par erreur.")
                        .setPositiveButton("Oui, décocher") { _, _ ->
                            Store.setTaken(this, m.id, null, day); MedWidget.updateAll(this); render()
                        }
                        .setNegativeButton("Garder", null)
                        .show()
                }
            }
            root.addView(gap(card, 10))
        }

        if (!isToday) root.addView(gap(button("Revenir à aujourd'hui", true) { offset = 0; render() }, 20))

        // Actions
        root.addView(gap(text("Réglages", 22f, ink, true), 32))
        root.addView(gap(button("Ajouter le widget sur l'écran d'accueil", true) { pinWidget() }, 10))
        root.addView(gap(button("Tester l'alarme") { testAlarm() }, 10))
        root.addView(gap(button("Modifier la liste des médicaments") { editList() }, 10))
        root.addView(gap(button("Historique des 14 derniers jours") { history() }, 10))
        root.addView(gap(text("Changez un dosage seulement sur prescription de votre médecin.", 14f, muted), 16))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pinWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, MedWidget::class.java), null, null)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Ajouter le widget")
                .setMessage("Appuyez longuement sur un espace vide de l'écran d'accueil, touchez « Widgets », puis choisissez « Mon traitement ».")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun testAlarm() {
        val meds = Store.meds(this)
        if (meds.isEmpty()) return
        Notify.show(this, meds.first().time, meds.filter { it.time == meds.first().time })
        Toast.makeText(this, "Alarme de test : touchez « C'est pris » ou fermez la notification pour l'arrêter.", Toast.LENGTH_LONG).show()
    }

    private fun history() {
        val meds = Store.meds(this)
        val lines = (0 downTo -13).joinToString("\n\n") { off ->
            val t = Store.taken(this, Store.dayKey(off))
            val head = when (off) { 0 -> "Aujourd'hui"; -1 -> "Hier"; else -> Store.longDate(off) }
            head + " : " + meds.count { t[it.id] != null } + "/" + meds.size + "\n" +
                meds.joinToString("   ") { m -> (if (t[m.id] != null) "✓ " else "✗ ") + m.name + (t[m.id]?.let { " " + Store.frTime(it) } ?: "") }
        }
        AlertDialog.Builder(this).setTitle("Historique").setMessage(lines).setPositiveButton("Fermer", null).show()
    }

    private fun editList() {
        val meds = Store.meds(this)
        val items = meds.map { "${it.name} ${it.dose} · ${Store.frTime(it.time)}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Mes médicaments")
            .setItems(items) { _, which -> editMed(meds[which]) }
            .setPositiveButton("Ajouter") { _, _ -> editMed(null) }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun editMed(med: Med?) {
        var time = med?.time ?: "08:00"
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val name = EditText(this).apply {
            hint = "Nom (ex. QUETIAPINE)"; setText(med?.name ?: ""); textSize = 18f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val dose = EditText(this).apply { hint = "Dosage (ex. 600 mg)"; setText(med?.dose ?: ""); textSize = 18f }
        val timeBtn = button("Heure : ${Store.frTime(time)}") {}
        timeBtn.setOnClickListener {
            val m = Store.minutes(time)
            TimePickerDialog(this, { _, h, min ->
                time = String.format("%02d:%02d", h, min); timeBtn.text = "Heure : ${Store.frTime(time)}"
            }, m / 60, m % 60, true).show()
        }
        form.addView(name); form.addView(dose); form.addView(gap(timeBtn, 12))

        val b = AlertDialog.Builder(this)
            .setTitle(if (med == null) "Ajouter un médicament" else "Modifier ${med.name}")
            .setView(form)
            .setPositiveButton("Enregistrer") { _, _ ->
                val n = name.text.toString().trim(); val d = dose.text.toString().trim()
                if (n.isEmpty() || d.isEmpty()) {
                    Toast.makeText(this, "Indiquez le nom et le dosage.", Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                val list = Store.meds(this).toMutableList()
                if (med == null) list.add(Med(Store.newId(), n, d, time))
                else list.replaceAll { if (it.id == med.id) Med(med.id, n, d, time) else it }
                saveList(list)
            }
            .setNegativeButton("Annuler", null)
        if (med != null) b.setNeutralButton("Retirer") { _, _ ->
            AlertDialog.Builder(this)
                .setTitle("Retirer ${med.name} ?")
                .setPositiveButton("Oui, retirer") { _, _ -> saveList(Store.meds(this).filter { it.id != med.id }) }
                .setNegativeButton("Annuler", null).show()
        }
        b.show()
    }

    private fun saveList(list: List<Med>) {
        Store.saveMeds(this, list)
        Alarms.scheduleAll(this)
        MedWidget.updateAll(this)
        render()
    }
}
