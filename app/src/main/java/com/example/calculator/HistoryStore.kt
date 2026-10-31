package com.example.calculator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Сохраняет историю в SharedPreferences, чтобы она переживала перезапуск приложения. */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HistoryEntry(o.getString("e"), o.getString("r"), o.optLong("t"), o.optString("n"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach {
            val o = JSONObject().put("e", it.expression).put("r", it.result).put("t", it.timestamp)
            if (it.note.isNotEmpty()) o.put("n", it.note)
            array.put(o)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
    }
}
