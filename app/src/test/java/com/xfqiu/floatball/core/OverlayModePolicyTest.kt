package com.xfqiu.floatball.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayModePolicyTest {

    @Test
    fun autoPrefersApplicationOverlayOnEInkWhenPermissionExists() {
        val plan = OverlayModePolicy.attachPlan(
            mode = OverlayMode.AUTO,
            canDrawOverlays = true,
            strictApplication = false
        )

        assertEquals(
            listOf(OverlayWindowKind.APPLICATION, OverlayWindowKind.ACCESSIBILITY),
            plan.candidates
        )
        assertFalse(plan.degradedFromApplication)
    }

    @Test
    fun autoStillHasAccessibilityFallbackWithoutPermission() {
        val plan = OverlayModePolicy.attachPlan(
            mode = OverlayMode.AUTO,
            canDrawOverlays = false,
            strictApplication = false
        )

        assertEquals(listOf(OverlayWindowKind.ACCESSIBILITY), plan.candidates)
        assertTrue(plan.degradedFromApplication)
    }

    /** 权限被回收时如果候选为空，球会彻底不出现，用户在设置页里就没有自救入口了。 */
    @Test
    fun forcedApplicationWithoutPermissionKeepsAccessibilityFallback() {
        val plan = OverlayModePolicy.attachPlan(
            mode = OverlayMode.APPLICATION,
            canDrawOverlays = false,
            strictApplication = false
        )

        assertEquals(listOf(OverlayWindowKind.ACCESSIBILITY), plan.candidates)
        assertTrue(plan.degradedFromApplication)
    }

    @Test
    fun forcedAccessibilityNeverTriesApplicationOverlay() {
        val plan = OverlayModePolicy.attachPlan(
            mode = OverlayMode.ACCESSIBILITY,
            canDrawOverlays = true,
            strictApplication = false
        )

        assertEquals(listOf(OverlayWindowKind.ACCESSIBILITY), plan.candidates)
        assertFalse(plan.degradedFromApplication)
    }

    /** 开关的全部意义：普通悬浮窗失败时不许悄悄退回不分发触摸的无障碍覆盖层。 */
    @Test
    fun strictForcedApplicationDropsAccessibilityFallback() {
        val plan = OverlayModePolicy.attachPlan(
            mode = OverlayMode.APPLICATION,
            canDrawOverlays = true,
            strictApplication = true
        )

        assertEquals(listOf(OverlayWindowKind.APPLICATION), plan.candidates)
        assertFalse(plan.degradedFromApplication)
        assertTrue(plan.accessibilityFallbackDisabled)
    }

    /** 权限缺失是用户能自己授权修好的失败，砍掉唯一候选只是让球白消失。 */
    @Test
    fun strictWithoutPermissionStillKeepsAccessibilityFallback() {
        val plan = OverlayModePolicy.attachPlan(
            mode = OverlayMode.APPLICATION,
            canDrawOverlays = false,
            strictApplication = true
        )

        assertEquals(listOf(OverlayWindowKind.ACCESSIBILITY), plan.candidates)
        assertFalse(plan.accessibilityFallbackDisabled)
    }

    /** AUTO 的定义就是自动兜底，开关不能改它，否则两个控件抢一件事。 */
    @Test
    fun strictDoesNotAffectAutoMode() {
        val plan = OverlayModePolicy.attachPlan(
            mode = OverlayMode.AUTO,
            canDrawOverlays = true,
            strictApplication = true
        )

        assertEquals(
            listOf(OverlayWindowKind.APPLICATION, OverlayWindowKind.ACCESSIBILITY),
            plan.candidates
        )
        assertFalse(plan.accessibilityFallbackDisabled)
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
