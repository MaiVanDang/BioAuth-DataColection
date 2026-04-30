package com.datn.datacollectv2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat

class SensorForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): SensorForegroundService = this@SensorForegroundService
    }
    private val binder = LocalBinder()

    var session: RecordingSession? = null
        private set

    var isRecording = false
        private set

    private var anchorMs = 0L

    var onTick: ((Long) -> Unit)? = null

    companion object {
        const val CHANNEL_ID      = "sensor_recording_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_ACTIVITY_LABEL = "ACTIVITY_LABEL"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsed = currentElapsedMs()
            updateNotification(elapsed)
            onTick?.invoke(elapsed)
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sensor)
            .setContentTitle("Sẵn sàng thu dữ liệu")
            .setContentText("Chọn hoạt động để bắt đầu")
            .setSilent(true)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    // ── Public API ─────────────────────────────────────────────────────

    fun startRecording(newSession: RecordingSession) {
        session      = newSession
        isRecording  = true
        anchorMs     = SystemClock.elapsedRealtime()

        val notification = buildNotification(newSession, currentElapsedMs(), isRecordingNow = true)
        startForeground(NOTIFICATION_ID, notification)

        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
    }

    fun stopRecording(): RecordingSession? {
        val current = session ?: return null
        val frozenMs = currentElapsedMs()

        isRecording = false
        handler.removeCallbacks(timerRunnable)

        val updated = current.copy(accumulatedMs = frozenMs)
        session = updated

        notifyManager().notify(
            NOTIFICATION_ID,
            buildNotification(updated, frozenMs, isRecordingNow = false)
        )
        return updated
    }

    fun currentElapsedMs(): Long {
        val s = session ?: return 0L
        return if (isRecording) s.totalElapsed(anchorMs)
        else s.accumulatedMs
    }

    fun dismissNotification() {
        isRecording = false
        handler.removeCallbacksAndMessages(null)
    }

    // ── Notification ───────────────────────────────────────────────────

    private fun updateNotification(elapsedMs: Long) {
        val s = session ?: return
        notifyManager().notify(NOTIFICATION_ID, buildNotification(s, elapsedMs, isRecordingNow = true))
    }

    private fun buildNotification(
        s: RecordingSession,
        elapsedMs: Long,
        isRecordingNow: Boolean
    ): Notification {
        val openIntent = Intent(this, SensorCollectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACTIVITY_LABEL, s.activityLabel)
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mm        = elapsedMs / 1000 / 60
        val ss        = elapsedMs / 1000 % 60
        val timerText = "%02d:%02d".format(mm, ss)
        val actName   = labelToVietnamese(s.activityLabel)
        val actIcon   = labelToEmoji(s.activityLabel)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sensor)
            .setOngoing(isRecordingNow)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isRecordingNow) {
            builder
                .setContentTitle("$actIcon Đang thu: $actName")
                .setContentText("⏱ $timerText — Nhấn để xem chi tiết")
        } else {
            builder
                .setContentTitle("$actIcon Đã dừng: $actName")
                .setContentText("⏱ $timerText — Dữ liệu đã được tạm lưu")
        }

        return builder.build()
    }

    private fun notifyManager() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Thu thập cảm biến",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Trạng thái thu dữ liệu cảm biến"
                setShowBadge(false)
            }
            notifyManager().createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }
    private fun labelToVietnamese(label: String) = when (label) {
        "walking" -> "Đi bộ"
        "standing" -> "Đứng"
        "sitting" -> "Ngồi"
        else -> label
    }

    private fun labelToEmoji(label: String) = when (label) {
        "walking" -> "🚶"
        "standing" -> "🧍"
        "sitting" -> "🪑"
        else -> "⏱"
    }
}