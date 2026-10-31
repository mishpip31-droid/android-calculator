package com.example.calculator

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue

/** Цвет из текущей темы (праздничной или обычной). */
fun Context.themeColor(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
}

/** Выбор праздничной темы: автоматически по календарю, выключено или конкретная. */
object ThemeManager {
    const val AUTO = "auto"
    const val OFF = "off"
    private const val PREFS = "theme"
    private const val KEY = "mode"

    fun mode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AUTO) ?: AUTO

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply()
    }

    fun current(context: Context, today: Day = Day.today()): Holiday? = when (val m = mode(context)) {
        OFF -> null
        AUTO -> Holidays.forDate(today)
        else -> Holidays.byId(m)
    }

    fun style(holiday: Holiday?): Int = when (holiday?.id) {
        "new_year" -> R.style.Holiday_NewYear
        "christmas" -> R.style.Holiday_Christmas
        "valentine" -> R.style.Holiday_Valentine
        "defender" -> R.style.Holiday_Defender
        "maslenitsa" -> R.style.Holiday_Maslenitsa
        "womens_day" -> R.style.Holiday_WomensDay
        "easter" -> R.style.Holiday_Easter
        "labour_day" -> R.style.Holiday_LabourDay
        "victory_day" -> R.style.Holiday_VictoryDay
        "independence" -> R.style.Holiday_Independence
        "kupalle" -> R.style.Holiday_Kupalle
        "knowledge" -> R.style.Holiday_Knowledge
        "mothers_day" -> R.style.Holiday_MothersDay
        "halloween" -> R.style.Holiday_Halloween
        else -> R.style.AppTheme
    }

    /** Диалог выбора темы; после выбора экран перерисовывается. */
    fun showPicker(activity: Activity) {
        val modes = ArrayList<String>()
        val labels = ArrayList<String>()
        val today = Holidays.forDate(Day.today())
        modes += AUTO
        labels += activity.getString(R.string.theme_auto) +
            (today?.let { " — ${it.emoji.first()} ${it.title}" } ?: "")
        modes += OFF
        labels += activity.getString(R.string.theme_off)
        for (h in Holidays.all) {
            modes += h.id
            labels += "${h.emoji.first()}  ${h.title}  ·  ${h.dates}"
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(labels.toTypedArray(), modes.indexOf(mode(activity)).coerceAtLeast(0)) { d, which ->
                setMode(activity, modes[which])
                d.dismiss()
                activity.recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

/** Базовый экран: применяет праздничную тему и обновляется, если она сменилась. */
open class ThemedActivity : Activity() {
    protected var holiday: Holiday? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        holiday = ThemeManager.current(this)
        setTheme(ThemeManager.style(holiday))
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Сменили тему на другом экране или наступил праздник — перерисовываемся.
        if (ThemeManager.current(this)?.id != holiday?.id) recreate()
    }
}
