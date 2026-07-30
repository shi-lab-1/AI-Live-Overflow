package com.aeli.overflow.sync

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseSync(
    private val url: String,
    private val key: String
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun post(table: String, body: JSONObject) {
        scope.launch {
            try {
                val conn = (URL("$url/rest/v1/$table").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("apikey", key)
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Prefer", "return=minimal")
                    doOutput = true
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    suspend fun getLatest(table: String, orderColumn: String = "updated_at"): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val conn = (URL("$url/rest/v1/$table?order=$orderColumn.desc&limit=1")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", key)
                    setRequestProperty("Authorization", "Bearer $key")
                }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = org.json.JSONArray(body)
                if (arr.length() > 0) arr.getJSONObject(0) else null
            } catch (_: Exception) { null }
        }
    }
}