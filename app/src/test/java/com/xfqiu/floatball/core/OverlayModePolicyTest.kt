package com.xfqiu.floatball.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayModePolicyTest {

    @Test
    fun autoPrefersApplicationOverlayOnEInkWhenPermissionExists() {
        assertEquals(
            listOf(OverlayWindowKind.APPLICATION, OverlayWindowKind.ACCESSIBILITY),
            OverlayModePolicy.candidates(OverlayMode.AUTO, canDrawOverlays = true)
        )
    }

    @Test
    fun autoStillHasAccessibilityFallbackWithoutPermission() {
        assertEquals(
            listOf(OverlayWindowKind.ACCESSIBILITY),
            OverlayModePolicy.candidates(OverlayMode.AUTO, canDrawOverlays = false)
        )
    }

    @Test
    fun selectingApplicationWithoutPermissionDoesNotApplyItYet() {
        val decision = OverlayModePolicy.selection(
            requested = OverlayMode.APPLICATION,
            canDrawOverlays = false
        )

        assertNull(decision.modeToApply)
        assertTrue(decision.requestPermission)
    }

    @Test
    fun selectingApplicationWithPermissionAppliesImmediately() {
        val decision = OverlayModePolicy.selection(
            requested = OverlayMode.APPLICATION,
            canDrawOverlays = true
        )

        assertEquals(OverlayMode.APPLICATION, decision.modeToApply)
        assertFalse(decision.requestPermission)
    }
}
