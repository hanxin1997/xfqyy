package com.xfqiu.floatball.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBallGestureClassifierTest {

    @Test
    fun noisyEInkTap_staysTapInsideMinimumSlop() {
        val classifier = FloatBallGestureClassifier(touchSlopPx = 12f)

        classifier.onDown(100f, 100f)
        val jitter = classifier.onMove(108f, 106f)
        val up = classifier.onUp(108f, 106f)

        assertFalse(jitter.dragStarted)
        assertFalse(jitter.dragMoved)
        assertTrue(up.tapped)
    }

    @Test
    fun deliberateMove_startsAndFinishesDragWithoutTap() {
        val classifier = FloatBallGestureClassifier(touchSlopPx = 12f)

        classifier.onDown(100f, 100f)
        val move = classifier.onMove(116f, 100f)
        val up = classifier.onUp(116f, 100f)

        assertTrue(move.dragStarted)
        assertTrue(move.dragMoved)
        assertTrue(up.dragFinished)
        assertFalse(up.tapped)
    }

    @Test
    fun cancelledDrag_neverCommitsDockOrTap() {
        val classifier = FloatBallGestureClassifier(touchSlopPx = 12f)

        classifier.onDown(100f, 100f)
        classifier.onMove(116f, 100f)
        val cancelled = classifier.onCancel()

        assertTrue(cancelled.dragCancelled)
        assertFalse(cancelled.dragFinished)
        assertFalse(cancelled.tapped)
    }

    @Test
    fun movementAtThreshold_isDrag() {
        val classifier = FloatBallGestureClassifier(touchSlopPx = 12f)

        classifier.onDown(0f, 0f)
        val move = classifier.onMove(12f, 0f)

        assertTrue(move.dragStarted)
    }

    /** 墨水屏噪声采样越过阈值后手指回到原点：必须还回点击，并撤回已经发生的位移。 */
    @Test
    fun noiseSpikeThenReturnToOrigin_isStillTap() {
        val classifier = FloatBallGestureClassifier(touchSlopPx = 12f)

        classifier.onDown(100f, 100f)
        val spike = classifier.onMove(120f, 100f)
        val up = classifier.onUp(101f, 100f)

        assertTrue(spike.dragStarted)
        assertTrue(up.tapped)
        assertTrue(up.dragCancelled)
        assertFalse(up.dragFinished)
    }

    @Test
    fun coalescedMoveDeliveredOnlyOnUp_stillCommitsDrag() {
        val classifier = FloatBallGestureClassifier(touchSlopPx = 12f)

        classifier.onDown(100f, 100f)
        val up = classifier.onUp(140f, 100f)

        assertTrue(up.dragStarted)
        assertTrue(up.dragMoved)
        assertTrue(up.dragFinished)
        assertFalse(up.tapped)
    }
}
