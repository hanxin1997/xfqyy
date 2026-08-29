package com.xfqiu.floatball.core

/** 低刷新模式的第一次移动必须立刻可见，后续才降频，避免“完全拉不动”的错觉。 */
class EInkDragRefreshPolicy {

    private var firstMove = true

    fun next(lowRefresh: Boolean): DragRefresh {
        if (!lowRefresh) return DragRefresh.NORMAL_FREQUENCY
        if (firstMove) {
            firstMove = false
            return DragRefresh.IMMEDIATE
        }
        return DragRefresh.LOW_FREQUENCY
    }

    fun reset() {
        firstMove = true
    }
}

enum class DragRefresh {
    IMMEDIATE,
    NORMAL_FREQUENCY,
    LOW_FREQUENCY
}
