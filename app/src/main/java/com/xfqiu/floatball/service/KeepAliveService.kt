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
import android.util.Log
import com.xfqiu.floatball.R
import com.xfqiu.floatball.core.Prefs

/**
 * 常驻通知，承担两件事：
 *
 * 1. 前台服务提升进程优先级，减少国产 ROM 回收整个进程的概率；
 * 2. 点击通知直接返回桌面。这条路径不经过无障碍服务——通知点击属于用户操作，
 *    不受 Android 10+ 的后台启动限制，即使无障碍服务被 ROM 杀掉也依然可用。
 *
 * 通知渠道使用 LOW：不发声、不横幅，同时保证厂商 ROM 不会把 MIN 级通知隐藏掉。
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.of(this).keepAlive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
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
        createNotificationChannel(this)
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

        // 渠道重要性创建后不可修改，因此换新 ID 迁移旧版的 IMPORTANCE_MIN 渠道。
        private const val CHANNEL_ID = "ink_float_ball_resident_v2"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_HOME = 100
        private const val TAG = "KeepAliveService"

        @Volatile
        private var running = false

        /** 幂等启停，并隔离厂商 ROM 的前台服务启动异常，不能连带打断无障碍服务。 */
        fun sync(context: Context, enabled: Boolean): Boolean {
            val intent = Intent(context, KeepAliveService::class.java)
            if (!enabled) {
                context.stopService(intent)
                return true
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (error: RuntimeException) {
                Log.e(TAG, "前台保活服务启动失败", error)
                false
            }
        }

        fun syncFromPrefs(context: Context): Boolean = sync(context, Prefs.of(context).keepAlive)

        fun isRunning(): Boolean = running

        fun isNotificationVisible(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        /** 在可见 Activity 中预建渠道，让系统通知授权流程有稳定触发时机。 */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
