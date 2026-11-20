package com.example.openspeedtest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saves the server list and the selected server on the phone, so they survive restarts. */
class ServerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("servers", Context.MODE_PRIVATE)

    fun loadServers(): List<Server> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Server(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    allowSelfSigned = o.optBoolean("allowSelfSigned", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveServers(servers: List<Server>) {
        val arr = JSONArray()
        servers.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("allowSelfSigned", it.allowSelfSigned)
            )
        }
        prefs.edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    fun loadSelectedId(): String? = prefs.getString(KEY_SELECTED, null)

    fun saveSelectedId(id: String?) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun loadOledMode(): OledMode =
        runCatching { OledMode.valueOf(prefs.getString(KEY_OLED, null) ?: "Auto") }.getOrDefault(OledMode.Auto)

    fun saveOledMode(mode: OledMode) {
        prefs.edit().putString(KEY_OLED, mode.name).apply()
    }

    private companion object {
        const val KEY_SERVERS = "server_list"
        const val KEY_SELECTED = "selected_server_id"
        const val KEY_OLED = "oled_mode"
    }
}
