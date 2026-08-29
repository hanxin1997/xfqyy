package com.xfqiu.floatball.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.xfqiu.floatball.R
import com.xfqiu.floatball.core.FloatBallGestureClassifier
import com.xfqiu.floatball.core.GestureDecision
import com.xfqiu.floatball.core.dpToPx

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

    /** ROM 抢占侧边触摸或出现多指时恢复原位，不得当作一次成功拖动。 */
    var onDragCancel: (() -> Unit)? = null

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
    private val touchSlop = maxOf(
        ViewConfiguration.get(context).scaledTouchSlop,
        context.dpToPx(E_INK_MIN_TOUCH_SLOP_DP)
    )
    private val classifier = FloatBallGestureClassifier(touchSlop.toFloat())

    private var pressedVisual = false
    private var activePointerId = INVALID_POINTER_ID

    init {
        isClickable = true
        contentDescription = context.getString(R.string.float_ball_description)
    }

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
            MotionEvent.ACTION_UP -> finishTouch(event)
            MotionEvent.ACTION_CANCEL -> cancelTouch()
            // 多指会让旧版 Android 的 rawX/rawY 基准跳变；电纸书上宁可取消本次拖动，
            // 也不能把球瞬移到屏幕外或误触菜单。
            MotionEvent.ACTION_POINTER_DOWN -> cancelTouch()
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) cancelTouch()
            }
        }
        return true
    }

    private fun beginTouch(event: MotionEvent) {
        val index = event.actionIndex
        activePointerId = event.getPointerId(index)
        val point = rawPoint(event, index)
        classifier.onDown(point.x, point.y)
        setPressedVisual(true)
    }

    private fun continueTouch(event: MotionEvent) {
        if (activePointerId == INVALID_POINTER_ID) return
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) {
            cancelTouch()
            return
        }
        val point = rawPoint(event, index)
        applyDecision(classifier.onMove(point.x, point.y))
    }

    private fun finishTouch(event: MotionEvent) {
        setPressedVisual(false)
        if (activePointerId == INVALID_POINTER_ID) return
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) {
            cancelTouch()
            return
        }
        val point = rawPoint(event, index)
        applyDecision(classifier.onUp(point.x, point.y))
        activePointerId = INVALID_POINTER_ID
    }

    private fun cancelTouch() {
        setPressedVisual(false)
        applyDecision(classifier.onCancel())
        activePointerId = INVALID_POINTER_ID
    }

    private fun applyDecision(decision: GestureDecision) {
        if (decision.dragStarted) {
            setPressedVisual(false)
            onDragStart?.invoke()
        }
        if (decision.dragMoved) onDragMove?.invoke(decision.dx, decision.dy)
        if (decision.dragFinished) onDragEnd?.invoke()
        if (decision.dragCancelled) onDragCancel?.invoke()
        if (decision.tapped) performClick()
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap?.invoke()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setGestureExclusion(w, h)
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun setGestureExclusion(width: Int, height: Int) {
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }

    private fun rawPoint(event: MotionEvent, index: Int): RawPoint {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return rawPointApi29(event, index)
        // getRawX(index) 从 API 29 才有；旧系统用指针局部坐标加窗口偏移还原。
        return RawPoint(
            x = event.getX(index) + (event.rawX - event.x),
            y = event.getY(index) + (event.rawY - event.y)
        )
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun rawPointApi29(event: MotionEvent, index: Int): RawPoint = RawPoint(
        x = event.getRawX(index),
        y = event.getRawY(index)
    )

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
        const val E_INK_MIN_TOUCH_SLOP_DP = 12
        const val INVALID_POINTER_ID = -1
    }
}

private data class RawPoint(val x: Float, val y: Float)
