package com.xfqiu.floatball.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EInkDragRefreshPolicyTest {

    @Test
    fun lowRefresh_showsFirstMoveImmediatelyThenThrottles() {
        val policy = EInkDragRefreshPolicy()

        assertEquals(DragRefresh.IMMEDIATE, policy.next(lowRefresh = true))
        assertEquals(DragRefresh.LOW_FREQUENCY, policy.next(lowRefresh = true))
        assertEquals(DragRefresh.LOW_FREQUENCY, policy.next(lowRefresh = true))
    }

    @Test
    fun nextGesture_getsImmediateFeedbackAgain() {
        val policy = EInkDragRefreshPolicy()

        policy.next(lowRefresh = true)
        policy.reset()

        assertEquals(DragRefresh.IMMEDIATE, policy.next(lowRefresh = true))
    }

    @Test
    fun normalMode_usesResponsiveThrottle() {
        val policy = EInkDragRefreshPolicy()

        assertEquals(DragRefresh.NORMAL_FREQUENCY, policy.next(lowRefresh = false))
    }
}
