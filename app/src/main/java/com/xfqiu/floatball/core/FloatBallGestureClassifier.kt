package com.xfqiu.floatball.core

/**
 * 不依赖 Android View 的悬浮球手势分类器。
 *
 * 墨水屏触控层常有数像素抖动，因此由调用方传入经过电纸书下限修正后的阈值。
 * 分类器只负责一次手势的提交语义，窗口移动和刷新节流留给 Controller。
 */
class FloatBallGestureClassifier(touchSlopPx: Float) {

    private val touchSlopSquared = touchSlopPx.coerceAtLeast(0f).let { it * it }

    private var state = State.IDLE
    private var downX = 0f
    private var downY = 0f

    fun onDown(rawX: Float, rawY: Float): GestureDecision {
        downX = rawX
        downY = rawY
        state = State.PRESSED
        return GestureDecision.NONE
    }

    fun onMove(rawX: Float, rawY: Float): GestureDecision {
        if (state == State.IDLE) return GestureDecision.NONE
        val dx = rawX - downX
        val dy = rawY - downY
        val startsDrag = state == State.PRESSED && dx * dx + dy * dy >= touchSlopSquared
        if (state == State.PRESSED && !startsDrag) return GestureDecision.NONE
        state = State.DRAGGING
        return GestureDecision(
            dragStarted = startsDrag,
            dragMoved = true,
            dx = dx.toInt(),
            dy = dy.toInt()
        )
    }

    /**
     * ACTION_UP 自带的最终坐标也必须参与分类。低刷新驱动可能合并掉 MOVE；
     * 若只看先前 MOVE，会把一次明显拖动误判成点击。
     */
    fun onUp(rawX: Float, rawY: Float): GestureDecision {
        if (state == State.IDLE) return GestureDecision.NONE
        val finalMove = onMove(rawX, rawY)
        val decision = when (state) {
            State.PRESSED -> finalMove.copy(tapped = true)
            State.DRAGGING -> finalMove.copy(dragFinished = true)
            State.IDLE -> GestureDecision.NONE
        }
        reset()
        return decision
    }

    fun onCancel(): GestureDecision {
        val decision = if (state == State.DRAGGING) {
            GestureDecision(dragCancelled = true)
        } else {
            GestureDecision.NONE
        }
        reset()
        return decision
    }

    private fun reset() {
        state = State.IDLE
    }

    private enum class State {
        IDLE,
        PRESSED,
        DRAGGING
    }
}

data class GestureDecision(
    val tapped: Boolean = false,
    val dragStarted: Boolean = false,
    val dragMoved: Boolean = false,
    val dragFinished: Boolean = false,
    val dragCancelled: Boolean = false,
    val dx: Int = 0,
    val dy: Int = 0
) {
    companion object {
        val NONE = GestureDecision()
    }
}
