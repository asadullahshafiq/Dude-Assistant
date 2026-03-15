package com.dude.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetworkHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * DuckDuckGo Instant Answer API – free, no key needed
     */
    suspend fun askDuckDuckGo(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder().url(url)
                .header("User-Agent", "DudeAssistant/1.0")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext offlineAnswer(query)
            val json = JSONObject(body)

            val abstract_ = json.optString("AbstractText", "")
            if (abstract_.isNotEmpty()) return@withContext abstract_

            val answer = json.optString("Answer", "")
            if (answer.isNotEmpty()) return@withContext answer

            val definition = json.optString("Definition", "")
            if (definition.isNotEmpty()) return@withContext definition

            offlineAnswer(query)
        } catch (e: Exception) {
            offlineAnswer(query)
        }
    }

    fun offlineAnswer(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("time") || q.contains("waqt") -> {
                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                "Abhi ka waqt: ${sdf.format(java.util.Date())}"
            }
            q.contains("date") || q.contains("tarikh") || q.contains("aaj") -> {
                val sdf = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault())
                "Aaj ki tarikh: ${sdf.format(java.util.Date())}"
            }
            q.contains("battery") || q.contains("charge") -> "Battery status check kar raha hun..."
            q.contains("name") || q.contains("naam") -> "Mera naam Dude hai! Aapka personal AI assistant."
            q.contains("who made") || q.contains("kisne") || q.contains("kaun") -> "Mujhe aapke liye banaya gaya hai – Dude Assistant!"
            q.contains("kya kar sakta") || q.contains("help") -> {
                "Main yeh kar sakta hun: WhatsApp message, apps open/close, " +
                        "screenshot, volume control, web search, screen pe click, scroll, call, aur bahut kuch!"
            }
            else -> "Main is sawaal ka jawab offline nahi de sakta. Internet on karein to bataunga!"
        }
    }
}
