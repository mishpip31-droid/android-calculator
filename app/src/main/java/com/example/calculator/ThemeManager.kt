package com.example.calculator

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue

/** Цвет из текущей темы оформления. */
fun Context.themeColor(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
}

/** Оформление: как в системе (по умолчанию), всегда светлое или всегда тёмное. */
object ThemeManager {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    private const val PREFS = "appearance"
    private const val KEY = "mode"

    fun mode(context: Context): String {
        val m = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM)
        return if (m == LIGHT || m == DARK) m else SYSTEM
    }

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply()
    }

    fun style(mode: String): Int = when (mode) {
        LIGHT -> R.style.AppTheme_ForcedLight
        DARK -> R.style.AppTheme_ForcedDark
        else -> R.style.AppTheme
    }

    /** Диалог выбора оформления; после выбора экран перерисовывается. */
    fun showPicker(activity: Activity) {
        val modes = arrayOf(SYSTEM, LIGHT, DARK)
        val labels = arrayOf(
            activity.getString(R.string.appearance_system),
            activity.getString(R.string.appearance_light),
            activity.getString(R.string.appearance_dark),
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.appearance_title)
            .setSingleChoiceItems(labels, modes.indexOf(mode(activity)).coerceAtLeast(0)) { d, which ->
                setMode(activity, modes[which])
                d.dismiss()
                activity.recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

/** Базовый экран: применяет выбранное оформление и обновляется, если его сменили. */
open class ThemedActivity : Activity() {
    private var appliedMode = ThemeManager.SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedMode = ThemeManager.mode(this)
        setTheme(ThemeManager.style(appliedMode))
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Оформление сменили на другом экране — перерисовываемся.
        if (ThemeManager.mode(this) != appliedMode) recreate()
    }
}
