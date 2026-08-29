package com.xfqiu.floatball

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.xfqiu.floatball.core.BackgroundRunSettings
import com.xfqiu.floatball.core.BackgroundSetupPhase
import com.xfqiu.floatball.core.Prefs
import com.xfqiu.floatball.service.FloatBallService
import com.xfqiu.floatball.service.KeepAliveService

/**
 * 墨水屏电纸书专用引导页。后台授权同时覆盖 Android 原生设置和可能被隐藏的
 * 厂商自启动组件；厂商权限没有标准查询 API，因此不伪造“已授权”状态。
 */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var accessibilityStatus: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var keepAliveStatus: TextView
    private lateinit var notificationStatus: TextView
    private val statusHandler = Handler(Looper.getMainLooper())
    private val delayedStatusRefresh = Runnable { refreshStatus() }
    private var backgroundGuidePromptedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs.of(this)
        accessibilityStatus = findViewById(R.id.accessibility_status)
        serviceStatus = findViewById(R.id.service_status)
        overlayStatus = findViewById(R.id.overlay_status)
        batteryStatus = findViewById(R.id.battery_status)
        keepAliveStatus = findViewById(R.id.keep_alive_status)
        notificationStatus = findViewById(R.id.notification_status)
        KeepAliveService.createNotificationChannel(this)
        bindClicks()
    }

    override fun onResume() {
        super.onResume()
        KeepAliveService.syncFromPrefs(this)
        // 从任一厂商权限页返回后重试窗口，普通悬浮窗授权无需重启无障碍服务。
        FloatBallService.notifySettingsChanged()
        refreshStatus()
        // 前台服务异步创建后只补刷这一小块文字，不做动画或整页持续刷新。
        statusHandler.removeCallbacks(delayedStatusRefresh)
        statusHandler.postDelayed(delayedStatusRefresh, STATUS_REFRESH_DELAY_MS)
        if (prefs.backgroundSetupPhase == BackgroundSetupPhase.WAITING_FOR_NATIVE_RETURN) {
            prefs.backgroundSetupPhase = BackgroundSetupPhase.IDLE
            completeBackgroundSetup()
            return
        }
        maybeShowBackgroundGuide()
    }

    override fun onPause() {
        statusHandler.removeCallbacks(delayedStatusRefresh)
        super.onPause()
    }

    private fun bindClicks() {
        findViewById<View>(R.id.open_accessibility).setOnClickListener {
            openSystemPage(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.open_overlay).setOnClickListener {
            openSystemPage(overlayPermissionIntent())
        }
        findViewById<View>(R.id.open_autostart).setOnClickListener {
            openHiddenAutoStartGuide()
        }
        findViewById<View>(R.id.open_native_background).setOnClickListener {
            startBackgroundSetup()
        }
        findViewById<View>(R.id.open_battery_policy).setOnClickListener {
            if (!BackgroundRunSettings.openVendorBatteryPolicy(this)) showMissingPage()
        }
        findViewById<View>(R.id.open_app_details).setOnClickListener {
            if (!BackgroundRunSettings.openAppDetails(this)) showMissingPage()
        }
        findViewById<View>(R.id.open_notifications).setOnClickListener {
            if (!BackgroundRunSettings.openNotificationSettings(this)) showMissingPage()
        }
        findViewById<View>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun refreshStatus() {
        accessibilityStatus.text =
            statusText(R.string.status_accessibility, isAccessibilityServiceEnabled())
        serviceStatus.text = statusText(R.string.status_service_connected, FloatBallService.isConnected())
        overlayStatus.text = statusText(R.string.status_overlay, Settings.canDrawOverlays(this))
        batteryStatus.text = statusText(
            R.string.status_battery_unrestricted,
            BackgroundRunSettings.isIgnoringBatteryOptimizations(this)
        )
        keepAliveStatus.text = statusText(
            R.string.status_keep_alive_running,
            KeepAliveService.isRunning()
        )
        notificationStatus.text = statusText(
            R.string.status_notification,
            KeepAliveService.isNotificationVisible(this)
        )
    }

    private fun statusText(labelRes: Int, granted: Boolean): String {
        val state = getString(if (granted) R.string.state_on else R.string.state_off)
        return getString(labelRes, state)
    }

    private fun maybeShowBackgroundGuide() {
        if (prefs.backgroundGuideShown || backgroundGuidePromptedThisSession) return
        // 先完成悬浮球核心授权，避免首次启动多个系统页面互相抢焦点。
        if (!isAccessibilityServiceEnabled() || !Settings.canDrawOverlays(this)) return
        backgroundGuidePromptedThisSession = true
        AlertDialog.Builder(this)
            .setTitle(R.string.background_guide_title)
            .setMessage(R.string.background_guide_message)
            .setPositiveButton(R.string.start_background_setup) { _, _ -> startBackgroundSetup() }
            .setNegativeButton(R.string.guide_later, null)
            .show()
    }

    /** 用户一次点击后先走 Android 原生豁免，返回时再尝试设备隐藏的自启动组件。 */
    private fun startBackgroundSetup() {
        if (BackgroundRunSettings.isIgnoringBatteryOptimizations(this)) {
            completeBackgroundSetup()
            return
        }
        prefs.backgroundSetupPhase = BackgroundSetupPhase.WAITING_FOR_NATIVE_RETURN
        if (!BackgroundRunSettings.requestBatteryOptimizationExemption(this)) {
            prefs.backgroundSetupPhase = BackgroundSetupPhase.IDLE
            completeBackgroundSetup()
        }
    }

    private fun openHiddenAutoStartGuide() {
        if (!BackgroundRunSettings.openHiddenAutoStart(this)) showMissingPage()
    }

    private fun completeBackgroundSetup() {
        val opened = BackgroundRunSettings.openHiddenAutoStart(this)
        prefs.backgroundGuideShown = opened
        if (!opened) showMissingPage()
    }

    /** 不同 ROM 可能写完整类名或短类名，两种都要识别。 */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val component = ComponentName(this, FloatBallService::class.java)
        val full = component.flattenToString()
        val short = component.flattenToShortString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(SERVICE_SEPARATOR).any {
            it.equals(full, ignoreCase = true) || it.equals(short, ignoreCase = true)
        }
    }

    private fun overlayPermissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
    )

    private fun openSystemPage(intent: Intent) {
        try {
            startActivity(intent)
        } catch (error: RuntimeException) {
            showMissingPage()
        }
    }

    private fun showMissingPage() {
        Toast.makeText(this, R.string.system_page_missing, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val SERVICE_SEPARATOR = ":"
        const val STATUS_REFRESH_DELAY_MS = 300L
    }
}
