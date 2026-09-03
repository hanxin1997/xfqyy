package com.xfqiu.floatball.core

import kotlin.math.max

/** 纯整数几何，便于在没有 Android 模拟器的 CI 宿主 JVM 上验证。 */
data class PixelPoint(val x: Int, val y: Int)

data class PixelSize(val width: Int, val height: Int)

/** right/bottom 使用开区间，与 Android Rect 语义一致。 */
data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class EdgeInsets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        val ZERO = EdgeInsets(0, 0, 0, 0)
    }
}

/** 墨水屏悬浮窗统一使用的安全区计算，禁止重新退回物理屏幕绝对边缘。 */
object OverlayGeometry {

    fun safeBounds(screen: PixelSize, insets: EdgeInsets, edgeMarginPx: Int): PixelBounds {
        val width = screen.width.coerceAtLeast(0)
        val height = screen.height.coerceAtLeast(0)
        val margin = edgeMarginPx.coerceAtLeast(0)
        val left = (insets.left.coerceAtLeast(0) + margin).coerceIn(0, width)
        val top = (insets.top.coerceAtLeast(0) + margin).coerceIn(0, height)
        val right = (width - insets.right.coerceAtLeast(0) - margin).coerceIn(left, width)
        val bottom = (height - insets.bottom.coerceAtLeast(0) - margin).coerceIn(top, height)
        return PixelBounds(left, top, right, bottom)
    }

    fun initialPosition(window: PixelSize, bounds: PixelBounds): PixelPoint {
        val x = max(bounds.left, bounds.right - window.width.coerceAtLeast(0))
        val availableHeight = (bounds.bottom - bounds.top - window.height).coerceAtLeast(0)
        return PixelPoint(x, bounds.top + availableHeight / 2)
    }

    /**
     * 把窗口移到安全区中央，供设置页在悬浮球掉进厂商手势死区时脱困。
     * 窗口大于安全区时仍返回可夹取的左上角，不产生负坐标。
     */
    fun centerPosition(window: PixelSize, bounds: PixelBounds): PixelPoint {
        val availableWidth = (bounds.right - bounds.left - window.width.coerceAtLeast(0))
            .coerceAtLeast(0)
        val availableHeight = (bounds.bottom - bounds.top - window.height.coerceAtLeast(0))
            .coerceAtLeast(0)
        return clamp(
            PixelPoint(
                x = bounds.left + availableWidth / 2,
                y = bounds.top + availableHeight / 2
            ),
            window,
            bounds
        )
    }

    fun clamp(position: PixelPoint, window: PixelSize, bounds: PixelBounds): PixelPoint {
        val maxX = max(bounds.left, bounds.right - window.width.coerceAtLeast(0))
        val maxY = max(bounds.top, bounds.bottom - window.height.coerceAtLeast(0))
        return PixelPoint(
            position.x.coerceIn(bounds.left, maxX),
            position.y.coerceIn(bounds.top, maxY)
        )
    }

    fun dock(position: PixelPoint, window: PixelSize, bounds: PixelBounds): PixelPoint {
        val clamped = clamp(position, window, bounds)
        val dockRight = clamped.x + window.width / 2 > (bounds.left + bounds.right) / 2
        val x = if (dockRight) {
            max(bounds.left, bounds.right - window.width.coerceAtLeast(0))
        } else {
            bounds.left
        }
        return PixelPoint(x, clamped.y)
    }
}
