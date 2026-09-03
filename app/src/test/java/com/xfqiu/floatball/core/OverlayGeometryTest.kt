package com.xfqiu.floatball.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGeometryTest {

    private val screen = PixelSize(width = 1404, height = 1872)
    private val ball = PixelSize(width = 96, height = 96)

    @Test
    fun safeBounds_combineSystemInsetsAndEInkEdgeMargin() {
        val bounds = OverlayGeometry.safeBounds(
            screen = screen,
            insets = EdgeInsets(left = 0, top = 48, right = 32, bottom = 64),
            edgeMarginPx = 24
        )

        assertEquals(PixelBounds(left = 24, top = 72, right = 1348, bottom = 1784), bounds)
    }

    @Test
    fun oldPhysicalEdgePosition_isMigratedInsideTouchableArea() {
        val bounds = PixelBounds(left = 24, top = 72, right = 1348, bottom = 1784)

        val clamped = OverlayGeometry.clamp(
            position = PixelPoint(1404 - 96, 0),
            window = ball,
            bounds = bounds
        )

        assertEquals(PixelPoint(x = 1252, y = 72), clamped)
    }

    @Test
    fun docking_keepsBallAwayFromBothPhysicalEdges() {
        val bounds = PixelBounds(left = 24, top = 72, right = 1348, bottom = 1784)

        val left = OverlayGeometry.dock(PixelPoint(200, 500), ball, bounds)
        val right = OverlayGeometry.dock(PixelPoint(1000, 500), ball, bounds)

        assertEquals(24, left.x)
        assertEquals(1252, right.x)
    }

    @Test
    fun initialPosition_usesSafeRightEdgeAndVerticalCenter() {
        val bounds = PixelBounds(left = 24, top = 72, right = 1348, bottom = 1784)

        val initial = OverlayGeometry.initialPosition(ball, bounds)

        assertEquals(PixelPoint(x = 1252, y = 880), initial)
    }

    @Test
    fun centerPosition_centersWholeWindowInsideAsymmetricBounds() {
        val bounds = PixelBounds(left = 24, top = 72, right = 1348, bottom = 1784)

        val centered = OverlayGeometry.centerPosition(ball, bounds)

        assertEquals(PixelPoint(x = 638, y = 880), centered)
    }

    @Test
    fun centerPosition_windowLargerThanBounds_fallsBackToBoundsOrigin() {
        val bounds = PixelBounds(left = 10, top = 20, right = 110, bottom = 70)

        val centered = OverlayGeometry.centerPosition(
            window = PixelSize(width = 200, height = 100),
            bounds = bounds
        )

        assertEquals(PixelPoint(x = 10, y = 20), centered)
    }

    @Test
    fun expandedEInkMargin_keepsDockedBallOutsideWideGestureZone() {
        val bounds = OverlayGeometry.safeBounds(
            screen = screen,
            insets = EdgeInsets.ZERO,
            edgeMarginPx = 96
        )

        val right = OverlayGeometry.dock(PixelPoint(1200, 500), ball, bounds)

        assertEquals(PixelBounds(left = 96, top = 96, right = 1308, bottom = 1776), bounds)
        assertEquals(PixelPoint(x = 1212, y = 500), right)
    }
}
