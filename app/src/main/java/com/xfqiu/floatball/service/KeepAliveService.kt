package com.xfqiu.floatball.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.xfqiu.floatball.R

/**
 * 常驻通知，承担两件事：
 *
 * 1. 前台服务提升进程优先级，减少国产 ROM 回收整个进程的概率；
 * 2. 点击通知直接返回桌面。这条路径不经过无障碍服务——通知点击属于用户操作，
 *    不受 Android 10+ 的后台启动限制，即使无障碍服务被 ROM 杀掉也依然可用。
 *
 * 通知渠道重要性设为 IMPORTANCE_MIN：不发声、不横幅、不占状态栏图标。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(homePendingIntent())
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_MIN
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun homePendingIntent(): PendingIntent {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, REQUEST_HOME, home, flags)
    }

    companion object {

        private const val CHANNEL_ID = "ink_float_ball_resident"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_HOME = 100

        /** 幂等：重复调用同一状态不会产生副作用。 */
        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (!enabled) {
                context.stopService(intent)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
