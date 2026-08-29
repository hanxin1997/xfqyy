package com.xfqiu.floatball.core

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.Locale

data class SystemComponentTarget(val packageName: String, val className: String)

/** 只接受明确的“应用自启动管理”命名，避免误开首次开机向导或 Bootloader 页面。 */
object AutoStartTargetMatcher {
    private val TOKENS = listOf(
        "autostart",
        "startupapp",
        "appstartup",
        "startupmanage",
        "bootmanage",
        "自启动",
        "自动启动",
        "开机启动",
        "后台启动"
    )

    fun matches(className: String, label: String): Boolean {
        val compact = "$className $label"
            .lowercase(Locale.ROOT)
            .replace(".", "")
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
        return TOKENS.any { token -> compact.contains(token) }
    }
}

/**
 * 电纸书可能只保留厂商组件而隐藏设置主页入口。候选不按品牌过滤，逐个直接启动；
 * 接近 AOSP 的设备同时走标准 Settings action，不把私有组件当成唯一方案。
 */
object AutoStartTargets {
    const val MIUI_PERMISSION_EDITOR_ACTION = "miui.intent.action.APP_PERM_EDITOR"

    val xiaomi = SystemComponentTarget(
        packageName = "com.miui.securitycenter",
        className = "com.miui.permcenter.autostart.AutoStartManagementActivity"
    )

    val candidates = listOf(
        xiaomi,
        SystemComponentTarget(
            packageName = "com.miui.securitycenter",
            className = "com.miui.permcenter.permissions.PermissionsEditorActivity"
        ),
        SystemComponentTarget(
            packageName = "com.miui.securitycenter",
            className = "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
        )
    )
}

/** Android 11 AOSP Settings 中承载高耗电白名单的 Activity；标准 action 失效时直达。 */
object NativeBackgroundTargets {
    val highPowerApplications = SystemComponentTarget(
        packageName = "com.android.settings",
        className = "com.android.settings.Settings\$HighPowerApplicationsActivity"
    )
}

object BackgroundRunSettings {

    private val xiaomiBatteryTarget = SystemComponentTarget(
        packageName = "com.miui.powerkeeper",
        className = "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
    )

    /** 厂商组件链：不先 resolve，避免被系统的隐藏/可见性策略错误过滤。 */
    fun openHiddenAutoStart(activity: Activity): Boolean {
        val primary = AutoStartTargets.candidates.first()
        if (start(activity, autoStartIntent(activity, primary))) return true

        // 精简电纸书 ROM 常保留组件却删掉设置入口：运行时扫描已导出的系统 Activity。
        for (target in discoverSystemAutoStartTargets(activity)) {
            if (target == primary) continue
            if (start(activity, autoStartIntent(activity, target))) return true
        }

        // 小米官方兼容建议提供的是这个 action；精简系统可能保留 action 映射但改了类名。
        val permissionEditor = Intent(AutoStartTargets.MIUI_PERMISSION_EDITOR_ACTION).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra("extra_pkgname", activity.packageName)
            putExtra("package_name", activity.packageName)
        }
        if (start(activity, permissionEditor)) return true

        for (target in AutoStartTargets.candidates.drop(1)) {
            if (start(activity, autoStartIntent(activity, target))) return true
        }
        // AOSP 没有“自启动权限”，最接近的是高耗电白名单；action 被裁剪时直达 Activity。
        if (start(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return true
        if (start(activity, explicitIntent(NativeBackgroundTargets.highPowerApplications))) return true
        return openAppDetails(activity)
    }

    private fun autoStartIntent(activity: Activity, target: SystemComponentTarget): Intent =
        explicitIntent(target).apply {
            putExtra("extra_pkgname", activity.packageName)
            putExtra("package_name", activity.packageName)
        }

    @Suppress("DEPRECATION")
    private fun discoverSystemAutoStartTargets(context: Context): List<SystemComponentTarget> {
        val manager = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_SYSTEM_ONLY
        return runCatching { manager.getInstalledPackages(flags) }
            .getOrElse {
                Log.w(TAG, "无法读取系统自启动组件", it)
                emptyList()
            }
            .asSequence()
            .flatMap { packageInfo -> packageInfo.activities.orEmpty().asSequence() }
            .filter { it.exported && it.packageName != context.packageName }
            .mapNotNull { activityInfo ->
                val label = runCatching { activityInfo.loadLabel(manager).toString() }.getOrDefault("")
                if (!AutoStartTargetMatcher.matches(activityInfo.name, label)) return@mapNotNull null
                SystemComponentTarget(activityInfo.packageName, activityInfo.name)
            }
            .distinct()
            .toList()
    }

    /** 厂商省电页面只是快捷入口；失败后始终回到 Android 原生豁免请求。 */
    fun openVendorBatteryPolicy(activity: Activity): Boolean {
        val intent = explicitIntent(xiaomiBatteryTarget).apply {
            putExtra("package_name", activity.packageName)
            putExtra("package_label", activity.applicationInfo.loadLabel(activity.packageManager))
        }
        if (start(activity, intent)) return true
        return requestBatteryOptimizationExemption(activity)
    }

    fun requestBatteryOptimizationExemption(activity: Activity): Boolean {
        if (isIgnoringBatteryOptimizations(activity)) return true
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${activity.packageName}")
        )
        if (start(activity, direct)) return true
        if (start(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return true
        if (start(activity, explicitIntent(NativeBackgroundTargets.highPowerApplications))) return true
        return openAppDetails(activity)
    }

    fun openNotificationSettings(activity: Activity): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            }
        } else {
            appDetailsIntent(activity)
        }
        return start(activity, intent) || openAppDetails(activity)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAppDetails(activity: Activity): Boolean = start(activity, appDetailsIntent(activity))

    private fun explicitIntent(target: SystemComponentTarget): Intent = Intent().apply {
        component = ComponentName(target.packageName, target.className)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )

    private fun start(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (error: RuntimeException) {
        Log.w(TAG, "系统设置页启动失败: ${intent.component ?: intent.action}", error)
        false
    }

    private const val TAG = "BackgroundRunSettings"
}
