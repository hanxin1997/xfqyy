package com.xfqiu.floatball.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * 折叠态悬浮球。纯 Canvas 绘制，无图片资源、无动画、无阴影。
 *
 * 按下反馈只加粗描边而不做黑白反色：墨水屏上反色需要重绘整个圆面，
 * 加粗描边只脏一圈，残影明显更轻。
 */
@SuppressLint("ViewConstructor")
class FloatBallView(context: Context) : View(context) {

    var onTap: (() -> Unit)? = null

    var onDragStart: (() -> Unit)? = null

    /** 参数为相对按下点的累计位移，避免逐帧增量取整造成漂移。 */
    var onDragMove: ((dx: Int, dy: Int) -> Unit)? = null

    var onDragEnd: (() -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val iconPath = Path()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var pressedVisual = false
    private var dragging = false
    private var downRawX = 0f
    private var downRawY = 0f

    override fun onDraw(canvas: Canvas) {
        val radius = minOf(width, height) / 2f
        if (radius <= 0f) return
        val centerX = width / 2f
        val centerY = height / 2f
        val ratio = if (pressedVisual) STROKE_RATIO_PRESSED else STROKE_RATIO_NORMAL
        strokePaint.strokeWidth = radius * ratio
        val inset = strokePaint.strokeWidth / 2f
        canvas.drawCircle(centerX, centerY, radius - inset, fillPaint)
        canvas.drawCircle(centerX, centerY, radius - inset, strokePaint)
        drawArrows(canvas, centerX, centerY, radius)
    }

    /** 上下两个实心三角，直观表达「翻页」。 */
    private fun drawArrows(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val halfWidth = radius * ARROW_HALF_WIDTH_RATIO
        val height = radius * ARROW_HEIGHT_RATIO
        val gap = radius * ARROW_GAP_RATIO
        iconPath.reset()
        iconPath.moveTo(centerX, centerY - gap - height)
        iconPath.lineTo(centerX - halfWidth, centerY - gap)
        iconPath.lineTo(centerX + halfWidth, centerY - gap)
        iconPath.close()
        iconPath.moveTo(centerX, centerY + gap + height)
        iconPath.lineTo(centerX - halfWidth, centerY + gap)
        iconPath.lineTo(centerX + halfWidth, centerY + gap)
        iconPath.close()
        canvas.drawPath(iconPath, iconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginTouch(event)
            MotionEvent.ACTION_MOVE -> continueTouch(event)
            MotionEvent.ACTION_UP -> endTouch(committed = true)
            MotionEvent.ACTION_CANCEL -> endTouch(committed = false)
            else -> return false
        }
        return true
    }

    private fun beginTouch(event: MotionEvent) {
        downRawX = event.rawX
        downRawY = event.rawY
        dragging = false
        setPressedVisual(true)
    }

    private fun continueTouch(event: MotionEvent) {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        if (!dragging) {
            if (hypot(dx, dy) < touchSlop) return
            dragging = true
            setPressedVisual(false)
            onDragStart?.invoke()
        }
        onDragMove?.invoke(dx.toInt(), dy.toInt())
    }

    private fun endTouch(committed: Boolean) {
        setPressedVisual(false)
        when {
            dragging -> onDragEnd?.invoke()
            committed -> onTap?.invoke()
        }
        dragging = false
    }

    private fun setPressedVisual(pressed: Boolean) {
        if (pressedVisual == pressed) return
        pressedVisual = pressed
        invalidate()
    }

    private companion object {
        const val STROKE_RATIO_NORMAL = 0.12f
        const val STROKE_RATIO_PRESSED = 0.26f
        const val ARROW_HALF_WIDTH_RATIO = 0.30f
        const val ARROW_HEIGHT_RATIO = 0.26f
        const val ARROW_GAP_RATIO = 0.10f
    }
}
