package com.xfqiu.floatball.core

import android.content.Context
import android.content.Intent

/**
 * 前台应用历史，「前进」动作的数据来源。Android 没有 Forward 这种全局动作，
 * 只能自己记住刚才待过的应用再重新拉起。
 *
 * 只保留两格可启动应用，不做长历史：手上这个动作要的是「回到刚才那个」，
 * 长历史除了增加状态没有别的收益。
 */
class ForegroundTracker(context: Context) {

    private val packageManager = context.packageManager
    private val selfPackage = context.packageName

    /** 最近一次窗口变化的包名，可能是桌面、系统 UI、输入法等无法启动的包。 */
    private var foreground: String? = null

    /** 最近两个可启动应用，让连续「前进」能在两者之间来回切。 */
    private var currentApp: String? = null
    private var previousApp: String? = null

    fun onForeground(packageName: CharSequence?) {
        val pkg = packageName?.toString() ?: return
        // 同一个包的连续事件（输入法、弹窗）直接丢掉，省下 PackageManager 查询
        if (pkg.isEmpty() || pkg == selfPackage || pkg == foreground) return
        foreground = pkg
        // 不可启动的包（桌面、系统 UI、输入法）只更新 foreground，不进历史
        if (pkg == currentApp || launchIntentOf(pkg) == null) return
        previousApp = currentApp
        currentApp = pkg
    }

    /**
     * 「前进」目标：站在桌面或系统界面上时回到刚才那个应用；
     * 已经在记录的应用里则切到它之前的那个。没有历史时返回 null。
     */
    fun forwardIntent(): Intent? {
        val target = if (foreground == currentApp) previousApp else currentApp
        return target?.let { launchIntentOf(it) }
    }

    /**
     * 刻意不加 FLAG_ACTIVITY_RESET_TASK_IF_NEEDED：那会把目标应用重置到首页，
     * 阅读进度就丢了。只要 NEW_TASK，已存在的任务会带着原状态回到前台。
     */
    private fun launchIntentOf(pkg: String): Intent? =
        packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
