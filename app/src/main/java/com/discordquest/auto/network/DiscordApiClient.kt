package com.discordquest.auto.network

import android.util.Base64
import com.discordquest.auto.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object DiscordApiClient {

    private const val BASE = "https://discord.com/api/v10"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val PROPERTIES = mapOf(
        "os" to "Windows",
        "browser" to "Discord Client",
        "release_channel" to "stable",
        "client_version" to "1.0.9215",
        "os_version" to "10.0.19045",
        "os_arch" to "x64",
        "app_arch" to "x64",
        "system_locale" to "en-US",
        "has_client_mods" to false,
        "browser_user_agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) discord/1.0.9215 Chrome/138.0.7204.251 Electron/37.6.0 Safari/537.36",
        "browser_version" to "37.6.0",
        "os_sdk_version" to "19045",
        "client_build_number" to 471091,
        "native_build_number" to 72186,
        "client_event_source" to null,
        "client_app_state" to "focused"
    )

    private val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) discord/1.0.9215 Chrome/138.0.7204.251 Electron/37.6.0 Safari/537.36"

    private fun makeHeaders(token: String): Map<String, String> {
        val propsJson = gson.toJson(PROPERTIES)
        val propsB64 = Base64.encodeToString(propsJson.toByteArray(), Base64.NO_WRAP)
        return mapOf(
            "Authorization" to token,
            "Content-Type" to "application/json",
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Origin" to "https://discord.com",
            "Referer" to "https://discord.com/channels/@me",
            "x-super-properties" to propsB64,
            "x-discord-locale" to "en-US",
            "x-discord-timezone" to "Asia/Saigon",
            "x-debug-options" to "bugReporterEnabled"
        )
    }

    private fun apiCall(token: String, method: String, path: String, body: Any? = null): JsonObject? {
        val url = "$BASE$path"
        val reqBody = if (body != null) gson.toJson(body).toRequestBody(JSON_MEDIA) else null

        val reqBuilder = Request.Builder().url(url)
        makeHeaders(token).forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        when (method.uppercase()) {
            "GET" -> reqBuilder.get()
            "POST" -> reqBuilder.post(reqBody ?: "{}".toRequestBody(JSON_MEDIA))
            "DELETE" -> reqBuilder.delete(reqBody)
            else -> reqBuilder.get()
        }

        val response = client.newCall(reqBuilder.build()).execute()
        if (response.code == 204) return null
        val text = response.body?.string() ?: ""
        if (!response.isSuccessful) throw Exception("API $method $path => ${response.code}: $text")
        return gson.fromJson(text, JsonObject::class.java)
    }

    fun verifyToken(token: String): String {
        val me = apiCall(token, "GET", "/users/@me")
            ?: throw Exception("Empty response from /users/@me")
        return me.get("username")?.asString ?: throw Exception("No username in response")
    }

    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseEpoch(s: String): Long {
        return try {
            sdf.parse(s.take(19))?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    fun runQuests(
        token: String,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Flow<List<QuestResult>> = flow {
        val results = mutableListOf<QuestResult>()
        val now = System.currentTimeMillis()

        val response = apiCall(token, "GET", "/quests/@me")
        val questsArray = response?.getAsJsonArray("quests") ?: run {
            emit(results)
            return@flow
        }

        val valid = questsArray.map { it.asJsonObject }.filter { q ->
            val id = q.get("id")?.asString ?: ""
            val userStatus = q.getAsJsonObject("user_status")
            val completedAt = userStatus?.get("completed_at")?.asString
            val config = q.getAsJsonObject("config")
            val expiresAt = config?.get("expires_at")?.asString
            val expires = if (expiresAt != null) parseEpoch(expiresAt) else 0L

            id != "1412491570820812933" && completedAt.isNullOrEmpty() && expires > now
        }

        if (valid.isEmpty()) {
            emit(results)
            return@flow
        }

        val allNames = valid.map { q ->
            val cfg = q.getAsJsonObject("config")
            val messages = cfg?.getAsJsonObject("messages")
            messages?.get("quest_name")?.asString
                ?: messages?.get("questName")?.asString
                ?: q.get("id")?.asString ?: "Unknown"
        }

        for ((idx, q) in valid.withIndex()) {
            val questId = q.get("id").asString
            val questName = allNames[idx]
            val cfg = q.getAsJsonObject("config")
            val taskConfig = cfg?.getAsJsonObject("task_config_v2")
                ?: cfg?.getAsJsonObject("task_config")

            suspend fun prog(action: String) {
                onProgress(ProgressUpdate(questName, action, results.toList(), allNames, idx))
            }

            prog("Đang chuẩn bị...")

            if (taskConfig == null || !taskConfig.has("tasks")) {
                results.add(QuestResult(questName, "unknown", QuestStatus.SKIPPED, "No task config"))
                prog("Bỏ qua — Không có task config")
                continue
            }

            val tasksObj = taskConfig.getAsJsonObject("tasks")
            val taskName = TASK_NAMES.firstOrNull { tasksObj.has(it) }

            if (taskName == null) {
                results.add(QuestResult(questName, "unknown", QuestStatus.SKIPPED, "Unknown task"))
                prog("Bỏ qua — Task không hỗ trợ")
                continue
            }

            try {
                var userStatus = q.getAsJsonObject("user_status")

                // Enroll if needed
                if (userStatus?.get("enrolled_at")?.asString.isNullOrEmpty()) {
                    prog("Đang đăng ký quest...")
                    val enrollBody = mapOf(
                        "location" to 11,
                        "is_targeted" to false,
                        "metadata_raw" to null
                    )
                    userStatus = apiCall(token, "POST", "/quests/$questId/enroll", enrollBody)
                }

                // Re-fetch if still no enrolled_at
                if (userStatus?.get("enrolled_at")?.asString.isNullOrEmpty()) {
                    val fresh = apiCall(token, "GET", "/quests/@me")
                    val freshArray = fresh?.getAsJsonArray("quests")
                    val found = freshArray?.map { it.asJsonObject }?.firstOrNull {
                        it.get("id")?.asString == questId
                    }
                    if (found != null) userStatus = found.getAsJsonObject("user_status")
                }

                val taskDef = tasksObj.getAsJsonObject(taskName)
                val secondsNeeded = taskDef.get("target").asInt
                val progressObj = userStatus?.getAsJsonObject("progress")
                    ?.getAsJsonObject(taskName)
                var secondsDone = progressObj?.get("value")?.asFloat ?: 0f

                when (taskName) {
                    "WATCH_VIDEO", "WATCH_VIDEO_ON_MOBILE" -> {
                        val maxFuture = 10
                        val speed = 7
                        val enrolledAtStr = userStatus?.get("enrolled_at")?.asString ?: ""
                        val enrolledAt = if (enrolledAtStr.isNotEmpty())
                            parseEpoch(enrolledAtStr) / 1000L
                        else
                            System.currentTimeMillis() / 1000L - maxFuture

                        var current = secondsDone.toDouble()
                        var completed = false

                        while (true) {
                            val nowSec = System.currentTimeMillis() / 1000L
                            val maxAllowed = (nowSec - enrolledAt) + maxFuture
                            val diff = maxAllowed - current
                            val nextTs = current + speed

                            if (diff >= speed) {
                                val ts = min(secondsNeeded.toDouble(), nextTs + Random.nextDouble())
                                val res = apiCall(token, "POST", "/quests/$questId/video-progress",
                                    mapOf("timestamp" to ts))
                                completed = res?.get("completed_at")?.asString?.isNotEmpty() == true
                                current = min(secondsNeeded.toDouble(), nextTs)
                                val pct = (current / secondsNeeded * 100).toInt()
                                prog("🎬 Video: ${current.toInt()}/${secondsNeeded}s ($pct%)")
                            }

                            if (nextTs >= secondsNeeded) break
                            delay(1000)
                        }

                        if (!completed) {
                            apiCall(token, "POST", "/quests/$questId/video-progress",
                                mapOf("timestamp" to secondsNeeded))
                        }

                        results.add(QuestResult(questName, taskName, QuestStatus.COMPLETED))
                        prog("✅ Hoàn thành!")
                    }

                    "PLAY_ON_DESKTOP" -> {
                        val applications = taskDef.getAsJsonArray("applications")
                        val applicationId = applications?.get(0)?.asJsonObject?.get("id")?.asString
                            ?: cfg?.getAsJsonObject("application")?.get("id")?.asString

                        val minsLeft = max(1, ((secondsNeeded - secondsDone) / 60).toInt())
                        prog("🎮 Game: ~$minsLeft phút...")

                        var current = secondsDone.toDouble()
                        while (true) {
                            val res = apiCall(token, "POST", "/quests/$questId/heartbeat",
                                mapOf("application_id" to applicationId, "terminal" to false))
                            val newVal = res?.getAsJsonObject("progress")
                                ?.getAsJsonObject(taskName)?.get("value")?.asDouble
                            if (newVal != null) current = newVal
                            val ml = max(0, ((secondsNeeded - current) / 60).toInt())
                            prog("🎮 Game: ${current.toInt()}/${secondsNeeded}s (~$ml phút)")

                            if (current >= secondsNeeded || res?.get("completed_at")?.asString?.isNotEmpty() == true) break
                            delay(60_000)
                        }

                        apiCall(token, "POST", "/quests/$questId/heartbeat",
                            mapOf("application_id" to applicationId, "terminal" to true))

                        results.add(QuestResult(questName, taskName, QuestStatus.COMPLETED))
                        prog("✅ Hoàn thành!")
                    }

                    "STREAM_ON_DESKTOP", "PLAY_ACTIVITY" -> {
                        results.add(QuestResult(questName, taskName, QuestStatus.WAITING, "Dùng Discord desktop app"))
                        prog("Bỏ qua — Cần Discord desktop app")
                    }

                    else -> {
                        results.add(QuestResult(questName, taskName, QuestStatus.SKIPPED, "Không hỗ trợ"))
                        prog("Bỏ qua — Không hỗ trợ")
                    }
                }
            } catch (e: Exception) {
                results.add(QuestResult(questName, taskName, QuestStatus.ERROR, e.message ?: "Unknown error"))
                onProgress(ProgressUpdate(questName, "❌ Lỗi: ${e.message}", results.toList(), allNames, idx))
            }
        }

        emit(results)
    }.flowOn(Dispatchers.IO)
}
