package com.xfqiu.floatball

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.xfqiu.floatball.service.FloatBallService

/**
 * 引导页。只做两件事：显示两项权限的真实状态、把用户送到对应的系统设置页。
 *
 * 无障碍服务无法由应用自行开启，这是系统的硬性设计，只能引导。
 */
class MainActivity : Activity() {

    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        accessibilityStatus = findViewById(R.id.accessibility_status)
        overlayStatus = findViewById(R.id.overlay_status)
        bindClicks()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun bindClicks() {
        findViewById<View>(R.id.open_accessibility).setOnClickListener {
            openSystemPage(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.open_overlay).setOnClickListener {
            openSystemPage(overlayPermissionIntent())
        }
        findViewById<View>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun refreshStatus() {
        accessibilityStatus.text =
            statusText(R.string.status_accessibility, isAccessibilityServiceEnabled())
        overlayStatus.text =
            statusText(R.string.status_overlay, Settings.canDrawOverlays(this))
    }

    private fun statusText(labelRes: Int, granted: Boolean): String {
        val state = getString(if (granted) R.string.state_on else R.string.state_off)
        return getString(labelRes, state)
    }

    /**
     * 不同 ROM 在这项设置里既可能写完整类名，也可能写 ".service.FloatBallService"
     * 这样的短名，两种都要比对。
     */
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

    /** 部分定制 ROM 裁掉了这些系统页面，跳转失败要给出可见反馈。 */
    private fun openSystemPage(intent: Intent) {
        try {
            startActivity(intent)
        } catch (error: RuntimeException) {
            Toast.makeText(this, R.string.system_page_missing, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val SERVICE_SEPARATOR = ":"
    }
}
