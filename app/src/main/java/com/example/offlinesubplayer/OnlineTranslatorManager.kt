package com.example.offlinesubplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class TranslationMode(val label: String) {
    OFFLINE("Offline (ML Kit)"),
    ONLINE("Online (Cloud)"),
    HYBRID("Hybrid (Online → Offline)")
}

class OnlineTranslatorManager {
    private val cache = ConcurrentHashMap<String, String>()

    fun clearCache() {
        cache.clear()
    }

    suspend fun translateText(
        text: String,
        sourceLang: String,
        targetLang: String,
        geminiApiKey: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext Result.success("")
        val cacheKey = "$sourceLang|$targetLang|${geminiApiKey.isNotBlank()}|$clean"
        cache[cacheKey]?.let { return@withContext Result.success(it) }

        runCatching {
            val translated = if (geminiApiKey.isNotBlank()) {
                translateWithGemini(clean, sourceLang, targetLang, geminiApiKey)
            } else {
                translateWithPublicEndpoint(clean, sourceLang, targetLang)
            }
            cache[cacheKey] = translated
            translated
        }
    }

    private fun translateWithPublicEndpoint(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val sl = URLEncoder.encode(sourceLang, "UTF-8")
        val tl = URLEncoder.encode(targetLang, "UTF-8")
        val url = URL(
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encodedText"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("HTTP $code dari layanan terjemahan online")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rootArray = JSONArray(body)
            val segments = rootArray.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i)
                val part = seg?.optString(0).orEmpty()
                sb.append(part)
            }
            val out = sb.toString().trim()
            if (out.isEmpty()) error("Respons terjemahan kosong")
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun translateWithGemini(
        text: String,
        sourceLang: String,
        targetLang: String,
        apiKey: String
    ): String {
        val endpoint = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${
                URLEncoder.encode(apiKey.trim(), "UTF-8")
            }"
        )
        val prompt = "Translate this subtitle line naturally from $sourceLang to $targetLang. " +
            "Return ONLY the translated subtitle text without quotes or explanations:\n$text"
        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
        }
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 12000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Gemini API HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val result = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
            if (result.isEmpty()) error("Respons Gemini kosong")
            return result
        } finally {
            conn.disconnect()
        }
    }
}
