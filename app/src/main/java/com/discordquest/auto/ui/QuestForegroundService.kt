package com.discordquest.auto.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.discordquest.auto.R

class QuestForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "quest_runner"
        const val NOTIF_ID = 1001
        const val EXTRA_TOKEN = "token"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = buildNotification("Đang chạy quests...")
        startForeground(NOTIF_ID, notif)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Discord Quest Auto")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_quest)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Quest Runner",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Thông báo tiến trình chạy quests"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
