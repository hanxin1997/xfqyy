package com.xfqiu.floatball.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.xfqiu.floatball.R
import com.xfqiu.floatball.SettingsActivity
import com.xfqiu.floatball.core.BallAction
import com.xfqiu.floatball.core.GestureFactory
import com.xfqiu.floatball.core.Prefs
import com.xfqiu.floatball.core.realScreenSize

/**
 * 悬浮球的运行载体。选无障碍服务而不是普通前台服务，有两个决定性理由：
 *
 * 1. 只有无障碍服务能向任意应用注入翻页手势；
 * 2. 它可以使用 TYPE_ACCESSIBILITY_OVERLAY，免去悬浮窗权限，且被系统绑定不易回收。
 *
 * 本服务不读取任何屏幕内容（canRetrieveWindowContent=false），只注入手势。
 */
class FloatBallService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: OverlayController? = null

    /** 手势派发期间会打开触摸穿透，重入会让穿透标记的开关次序错乱。 */
    private var gesturePending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val controller = OverlayController(this, ::execute)
        overlay = controller
        controller.show()
        KeepAliveService.sync(this, Prefs.of(this).keepAlive)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onScreenChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        handler.removeCallbacksAndMessages(null)
        gesturePending = false
        overlay?.hide()
        overlay = null
        KeepAliveService.sync(this, false)
        if (instance === this) instance = null
    }

    private fun execute(action: BallAction) {
        when (action) {
            BallAction.PrevPage -> turnPage(forward = false)
            BallAction.NextPage -> turnPage(forward = true)
            BallAction.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
            BallAction.OpenSettings -> openSettings()
            is BallAction.LaunchApp -> launchShortcut(action.slot)
        }
    }

    private fun turnPage(forward: Boolean) {
        if (gesturePending) return
        val controller = overlay ?: return
        val gesture = GestureFactory.build(
            Prefs.of(this),
            forward,
            realScreenSize(),
            controller.occupiedRect()
        ) ?: return
        gesturePending = true
        controller.setTouchThrough(true)
        // 触摸穿透标记要经过一次窗口更新才生效，稍等一下再派发
        handler.postDelayed({ dispatch(gesture, controller) }, PASSTHROUGH_SETTLE_MS)
    }

    /**
     * 派发手势并保证穿透标记一定会被恢复：正常靠回调，
     * 回调不来（部分 ROM 的已知行为）则靠超时兜底，否则悬浮球会永久失去触摸。
     */
    private fun dispatch(gesture: GestureDescription, controller: OverlayController) {
        val restore = Runnable {
            gesturePending = false
            controller.setTouchThrough(false)
        }
        handler.postDelayed(restore, PASSTHROUGH_TIMEOUT_MS)
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = reschedule()

            override fun onCancelled(gestureDescription: GestureDescription?) = reschedule()

            private fun reschedule() {
                handler.removeCallbacks(restore)
                handler.postDelayed(restore, RESTORE_DELAY_MS)
            }
        }
        if (dispatchGesture(gesture, callback, null)) return
        Log.w(TAG, "dispatchGesture 被系统拒绝")
        handler.removeCallbacks(restore)
        restore.run()
    }

    private fun launchShortcut(slot: Int) {
        val shortcut = Prefs.of(this).shortcuts.getOrNull(slot) ?: return
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(shortcut.packageName, shortcut.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivitySafely(intent, shortcut.label)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivitySafely(intent, getString(R.string.menu_settings))
    }

    /**
     * Android 10 起限制后台启动 Activity。持有 SYSTEM_ALERT_WINDOW 是明确的豁免条件，
     * 所以引导页会建议授予该权限即便悬浮窗本身用不到它。
     */
    private fun startActivitySafely(intent: Intent, label: String) {
        try {
            startActivity(intent)
        } catch (error: RuntimeException) {
            Log.w(TAG, "启动失败: $label", error)
            Toast.makeText(this, getString(R.string.launch_failed, label), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {

        private const val TAG = "FloatBallService"
        private const val PASSTHROUGH_SETTLE_MS = 30L
        private const val PASSTHROUGH_TIMEOUT_MS = 800L
        private const val RESTORE_DELAY_MS = 60L

        @Volatile
        private var instance: FloatBallService? = null

        /** 设置项变更后让悬浮球按新参数重建；服务未运行时静默忽略。 */
        fun notifySettingsChanged() {
            val service = instance ?: return
            service.handler.post {
                service.overlay?.reload()
                KeepAliveService.sync(service, Prefs.of(service).keepAlive)
            }
        }
    }
}
