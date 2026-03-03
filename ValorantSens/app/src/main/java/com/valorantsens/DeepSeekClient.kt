package com.valorantsens

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekClient(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("deepseek_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val API_URL     = "https://api.deepseek.com/chat/completions"
        private const val MODEL       = "deepseek-chat"
        private const val KEY_API_KEY = "api_key"
    }

    fun saveApiKey(key: String) = prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    fun getApiKey(): String     = prefs.getString(KEY_API_KEY, "") ?: ""
    fun hasApiKey(): Boolean    = getApiKey().isNotBlank()

    // ==================== 主分析接口 ====================

    suspend fun analyze(stats: RealtimeStats, config: SensitivityConfig): DeepSeekResult =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isBlank())           return@withContext DeepSeekResult.Error("未设置 API Key")
            if (stats.totalSamples < 5)     return@withContext DeepSeekResult.Error("数据不足，请先打几局再分析（当前 ${stats.totalSamples}/5）")

            try {
                val json = callApi(apiKey, buildPrompt(stats, config))
                parseResponse(json)
            } catch (e: Exception) {
                DeepSeekResult.Error("请求失败：${e.message}")
            }
        }

    // ==================== Prompt（只要数值） ====================

    private fun buildPrompt(stats: RealtimeStats, config: SensitivityConfig): String {
        val h = stats.hipfire
        val a = stats.aimSight
        val g = stats.gyro

        val gyroDesc = if (g.sampleCount >= 3) {
            "陀螺仪数据(${g.sampleCount}次)：旋转速率=${"%.2f".format(g.avgRotationRate)}rad/s，稳定性=${"%.2f".format(g.avgEndStability)}，抖动=${"%.3f".format(g.avgVariance)}，手腕稳定=${if (g.isStableEnough) "是" else "否"}"
        } else {
            "陀螺仪：未采集"
        }

        return """
你是无畏契约手游灵敏度专家。根据以下操作数据给出最优配置。

当前配置：滑屏一键=${config.slideMainPct}%，滑屏垂直=${config.slideVertPct}%，陀螺仪=${if (config.gyroEnabled) "开(一键${config.gyroMainPct}%,垂直${config.gyroVertPct}%)" else "关"}
采集：${stats.totalSamples}次瞄准，过冲=${h.overshootRate.toInt()}px，偏差=${h.avgDeviationDeg.toInt()}°，速度方差=${"%.2f".format(h.speedVariance)}
$gyroDesc

只返回JSON，不要其他文字：
{"slideMainPct":<50~300整数>,"slideVertPct":<10~200整数>,"enableGyro":<true/false>,"gyroMainPct":<50~300整数>,"gyroVertPct":<10~200整数>}
        """.trimIndent()
    }

    // ==================== HTTP 调用 ====================

    private fun callApi(apiKey: String, prompt: String): String {
        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 15_000
            readTimeout    = 30_000
            doOutput       = true
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 80)       // 只输出 JSON，token 很少
            put("temperature", 0.1)     // 极低温度，数值输出稳定
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }.toString()

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code   = conn.responseCode
        val stream = if (code == 200) conn.inputStream else conn.errorStream
        val text   = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()

        if (code != 200) throw Exception("HTTP $code")
        return text
    }

    // ==================== 解析 ====================

    private fun parseResponse(responseText: String): DeepSeekResult {
        return try {
            val content = JSONObject(responseText)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            val start = content.indexOf('{')
            val end   = content.lastIndexOf('}')
            if (start == -1 || end == -1) throw Exception("响应中无 JSON")

            val json = JSONObject(content.substring(start, end + 1))
            DeepSeekResult.Success(
                slideMainPct = json.optInt("slideMainPct", 114).coerceIn(50, 300),
                slideVertPct = json.optInt("slideVertPct", 28).coerceIn(10, 200),
                enableGyro   = json.optBoolean("enableGyro", false),
                gyroMainPct  = json.optInt("gyroMainPct", 172).coerceIn(50, 300),
                gyroVertPct  = json.optInt("gyroVertPct", 49).coerceIn(10, 200)
            )
        } catch (e: Exception) {
            DeepSeekResult.Error("解析失败：${e.message}")
        }
    }
}

// ==================== 结果 ====================

sealed class DeepSeekResult {
    data class Success(
        // 滑屏
        val slideMainPct:  Int,      // 滑屏一键调节 (50~300%)
        val slideVertPct:  Int,      // 滑屏垂直灵敏度 (10~200%)
        // 陀螺仪
        val enableGyro:    Boolean,
        val gyroMainPct:   Int,      // 陀螺仪一键调节 (50~300%)
        val gyroVertPct:   Int       // 陀螺仪垂直灵敏度 (10~200%)
    ) : DeepSeekResult()

    data class Error(val message: String) : DeepSeekResult()
}
