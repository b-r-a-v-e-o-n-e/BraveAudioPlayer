package org.widelogic.braveaudio.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedEqPreset(val name: String, val levels: List<Float>)

class EqPresetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("brave_audio_eq", Context.MODE_PRIVATE)

    fun list(): List<SavedEqPreset> = runCatching {
        val array = JSONArray(prefs.getString("presets", "[]") ?: "[]")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val levels = item.getJSONArray("levels")
            SavedEqPreset(item.getString("name"), if (levels.length() >= 10) {
                List(10) { levels.optDouble(it, 0.0).toFloat().coerceIn(-1f,1f) }
            } else {
                // Legacy 6-band presets mapped onto logarithmic 10-band scale.
                val source = List(6) { levels.optDouble(it, 0.0).toFloat().coerceIn(-1f,1f) }
                List(10) { i ->
                    val pos = i * 5f / 9f
                    val a = pos.toInt().coerceIn(0,5)
                    val b = (a+1).coerceAtMost(5)
                    source[a]*(1f-(pos-a)) + source[b]*(pos-a)
                }
            })
        }
    }.getOrDefault(emptyList())

    fun save(name: String, levels: List<Float>) {
        val clean = name.trim().take(48)
        if (clean.isBlank() || levels.size != 10) return
        val updated = list().filterNot { it.name.equals(clean, ignoreCase = true) } + SavedEqPreset(clean, levels)
        write(updated)
    }

    fun delete(name: String) = write(list().filterNot { it.name == name })

    private fun write(data: List<SavedEqPreset>) {
        val array = JSONArray()
        data.forEach { preset ->
            val json = JSONObject()
            json.put("name", preset.name)
            json.put("levels", JSONArray().apply { preset.levels.forEach { put(it.toDouble()) } })
            array.put(json)
        }
        prefs.edit().putString("presets", array.toString()).apply()
    }
}
