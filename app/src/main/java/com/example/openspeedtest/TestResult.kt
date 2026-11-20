package com.example.openspeedtest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class TestResult(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val serverName: String,
    val serverUrl: String,
    val download: Double, // Mbps
    val upload: Double,   // Mbps
    val ping: Double,     // ms
    val jitter: Double?,  // ms
)

/** 29.0 -> "29", 200.53 -> "200.5" */
fun formatNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.getDefault(), "%.1f", v)

/** Saves test history on the phone (newest first). */
class HistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<TestResult> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TestResult(
                    id = o.getString("id"),
                    timestamp = o.getLong("timestamp"),
                    serverName = o.getString("serverName"),
                    serverUrl = o.getString("serverUrl"),
                    download = o.getDouble("download"),
                    upload = o.getDouble("upload"),
                    ping = o.getDouble("ping"),
                    jitter = if (o.isNull("jitter")) null else o.getDouble("jitter"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(results: List<TestResult>) {
        val arr = JSONArray()
        results.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("timestamp", it.timestamp)
                    .put("serverName", it.serverName)
                    .put("serverUrl", it.serverUrl)
                    .put("download", it.download)
                    .put("upload", it.upload)
                    .put("ping", it.ping)
                    .put("jitter", it.jitter ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "results"
    }
}
