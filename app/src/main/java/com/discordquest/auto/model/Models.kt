package com.discordquest.auto.model

data class QuestResult(
    val name: String,
    val taskType: String,
    val status: QuestStatus,
    val message: String = ""
)

enum class QuestStatus { COMPLETED, SKIPPED, ERROR, WAITING, RUNNING }

data class QuestItem(
    val id: String,
    val name: String,
    val taskType: String,
    val secondsNeeded: Int,
    val secondsDone: Float,
    val enrolledAt: String?,
    val completedAt: String?
)

data class ProgressUpdate(
    val questName: String,
    val action: String,
    val done: List<QuestResult>,
    val allNames: List<String>,
    val currentIdx: Int
)

val TASK_LABELS = mapOf(
    "WATCH_VIDEO" to "🎬 Video",
    "WATCH_VIDEO_ON_MOBILE" to "📱 Video Mobile",
    "PLAY_ON_DESKTOP" to "🎮 Game",
    "STREAM_ON_DESKTOP" to "📡 Stream",
    "PLAY_ACTIVITY" to "🎯 Activity"
)

val TASK_NAMES = listOf(
    "WATCH_VIDEO",
    "PLAY_ON_DESKTOP",
    "STREAM_ON_DESKTOP",
    "PLAY_ACTIVITY",
    "WATCH_VIDEO_ON_MOBILE"
)
