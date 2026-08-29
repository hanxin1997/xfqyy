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
}
