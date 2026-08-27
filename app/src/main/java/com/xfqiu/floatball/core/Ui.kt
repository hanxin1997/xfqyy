package com.xfqiu.floatball.core

import android.content.ComponentName
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.view.WindowManager

private const val CONTRAST_SCALE = 1.5f
private const val COLOR_MAX = 255f

/**
 * 去色 + 提高对比度。彩色应用图标直接放到墨水屏上会变成一坨难以辨认的脏灰，
 * 拉高对比度后轮廓才清晰。
 */
private val INK_COLOR_FILTER: ColorMatrixColorFilter = run {
    val shift = (1f - CONTRAST_SCALE) / 2f * COLOR_MAX
    val contrast = ColorMatrix(
        floatArrayOf(
            CONTRAST_SCALE, 0f, 0f, 0f, shift,
            0f, CONTRAST_SCALE, 0f, 0f, shift,
            0f, 0f, CONTRAST_SCALE, 0f, shift,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val matrix = ColorMatrix().apply { setSaturation(0f) }
    matrix.postConcat(contrast)
    ColorMatrixColorFilter(matrix)
}

fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density + 0.5f).toInt()

/**
 * 包含状态栏与导航栏的物理屏幕尺寸。dispatchGesture 使用的是整屏坐标系，
 * 因此不能用去掉系统栏的可用区域尺寸。
 */
fun Context.realScreenSize(): Point {
    val windowManager = getSystemService(WindowManager::class.java) ?: return Point(0, 0)
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay.getRealMetrics(metrics)
    return Point(metrics.widthPixels, metrics.heightPixels)
}

/** 返回副本，避免污染 PackageManager 缓存的共享 Drawable 实例。 */
fun Drawable.toInkGray(): Drawable {
    val copy = constantState?.newDrawable()?.mutate() ?: mutate()
    copy.colorFilter = INK_COLOR_FILTER
    return copy
}

/** 应用被卸载后取图标会失败，返回 null 交由调用方决定兜底显示。 */
fun Context.loadShortcutIcon(shortcut: AppShortcut): Drawable? = runCatching {
    val component = ComponentName(shortcut.packageName, shortcut.activityName)
    packageManager.getActivityIcon(component).toInkGray()
}.getOrNull()
