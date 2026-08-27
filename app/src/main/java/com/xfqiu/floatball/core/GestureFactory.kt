package com.xfqiu.floatball.core

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect

/**
 * 把「翻页方式 + 用户参数」翻译成可派发的手势。
 *
 * avoid 是悬浮窗当前占据的矩形。派发的手势会命中最上层窗口，若目标点正好压在
 * 悬浮球或菜单上，事件会被自己吃掉，表现为「点了没反应」。这里做坐标避让，
 * 与 OverlayController 的触摸穿透标记构成双保险。
 */
object GestureFactory {

    private const val TAP_DURATION_MS = 50L

    /** 单点 Path 会被 StrokeDescription 判为空路径，需要一个极小位移。 */
    private const val TAP_JITTER_PX = 1f

    private const val EDGE_MARGIN_PX = 4f
    private const val AVOID_MARGIN_RATIO = 0.08f
    private const val PERCENT_BASE = 100f

    fun build(prefs: Prefs, forward: Boolean, screen: Point, avoid: Rect?): GestureDescription? {
        if (screen.x <= 0 || screen.y <= 0) return null
        return when (prefs.pageTurnMode) {
            PageTurnMode.TAP -> buildTap(prefs, forward, screen, avoid)
            PageTurnMode.SWIPE_HORIZONTAL -> buildHorizontalSwipe(prefs, forward, screen, avoid)
            PageTurnMode.SWIPE_VERTICAL -> buildVerticalSwipe(prefs, forward, screen, avoid)
        }
    }

    private fun buildTap(
        prefs: Prefs,
        forward: Boolean,
        screen: Point,
        avoid: Rect?
    ): GestureDescription {
        val xPercent = if (forward) prefs.nextTapXPercent else prefs.prevTapXPercent
        val x = clamp(screen.x * xPercent / PERCENT_BASE, screen.x)
        var y = screen.y * prefs.tapYPercent / PERCENT_BASE
        if (avoid != null && avoid.contains(x.toInt(), y.toInt())) {
            y = escapeVertically(y, avoid, screen.y)
        }
        val fixedY = clamp(y, screen.y)
        return singleStroke(x, fixedY, x, fixedY + TAP_JITTER_PX, TAP_DURATION_MS)
    }

    private fun buildHorizontalSwipe(
        prefs: Prefs,
        forward: Boolean,
        screen: Point,
        avoid: Rect?
    ): GestureDescription {
        val halfDistance = screen.x * prefs.swipeDistancePercent / PERCENT_BASE / 2f
        val centerX = screen.x / 2f
        val leftX = clamp(centerX - halfDistance, screen.x)
        val rightX = clamp(centerX + halfDistance, screen.x)
        var y = screen.y * prefs.tapYPercent / PERCENT_BASE
        if (avoid != null && hitsHorizontalPath(avoid, y, leftX, rightX)) {
            y = escapeVertically(y, avoid, screen.y)
        }
        val fixedY = clamp(y, screen.y)
        // 下一页 = 内容向左移动 = 手指自右向左划
        val startX = if (forward) rightX else leftX
        val endX = if (forward) leftX else rightX
        return singleStroke(startX, fixedY, endX, fixedY, prefs.swipeDurationMs.toLong())
    }

    private fun buildVerticalSwipe(
        prefs: Prefs,
        forward: Boolean,
        screen: Point,
        avoid: Rect?
    ): GestureDescription {
        val halfDistance = screen.y * prefs.swipeDistancePercent / PERCENT_BASE / 2f
        val centerY = screen.y / 2f
        val topY = clamp(centerY - halfDistance, screen.y)
        val bottomY = clamp(centerY + halfDistance, screen.y)
        var x = screen.x / 2f
        if (avoid != null && hitsVerticalPath(avoid, x, topY, bottomY)) {
            x = escapeHorizontally(x, avoid, screen.x)
        }
        val fixedX = clamp(x, screen.x)
        // 下一页 = 内容向上移动 = 手指自下向上划
        val startY = if (forward) bottomY else topY
        val endY = if (forward) topY else bottomY
        return singleStroke(fixedX, startY, fixedX, endY, prefs.swipeDurationMs.toLong())
    }

    /** 把纵坐标挪出 avoid：优先往上，上方空间不足再往下，两侧都不够则原样返回。 */
    private fun escapeVertically(current: Float, avoid: Rect, screenHeight: Int): Float {
        val margin = screenHeight * AVOID_MARGIN_RATIO
        val above = avoid.top - margin
        if (above > EDGE_MARGIN_PX) return above
        val below = avoid.bottom + margin
        if (below < screenHeight - EDGE_MARGIN_PX) return below
        return current
    }

    /** 把横坐标挪出 avoid：优先往左，左侧空间不足再往右。 */
    private fun escapeHorizontally(current: Float, avoid: Rect, screenWidth: Int): Float {
        val margin = screenWidth * AVOID_MARGIN_RATIO
        val left = avoid.left - margin
        if (left > EDGE_MARGIN_PX) return left
        val right = avoid.right + margin
        if (right < screenWidth - EDGE_MARGIN_PX) return right
        return current
    }

    /** 横向路径纵坐标固定，只需判断水平线段是否与 avoid 相交。要求 fromX <= toX。 */
    private fun hitsHorizontalPath(avoid: Rect, y: Float, fromX: Float, toX: Float): Boolean =
        y >= avoid.top && y <= avoid.bottom && toX >= avoid.left && fromX <= avoid.right

    /** 纵向路径横坐标固定。要求 fromY <= toY。 */
    private fun hitsVerticalPath(avoid: Rect, x: Float, fromY: Float, toY: Float): Boolean =
        x >= avoid.left && x <= avoid.right && toY >= avoid.top && fromY <= avoid.bottom

    private fun clamp(value: Float, max: Int): Float {
        val upper = (max - EDGE_MARGIN_PX).coerceAtLeast(EDGE_MARGIN_PX)
        return value.coerceIn(EDGE_MARGIN_PX, upper)
    }

    private fun singleStroke(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): GestureDescription {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        return GestureDescription.Builder().addStroke(stroke).build()
    }
}
