package com.xfqiu.floatball.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartTargetMatcherTest {

    @Test
    fun matchesCommonHiddenAutoStartManagers() {
        assertTrue(AutoStartTargetMatcher.matches("AutoStartManagementActivity", "权限管理"))
        assertTrue(AutoStartTargetMatcher.matches("StartupAppListActivity", "应用管理"))
        assertTrue(AutoStartTargetMatcher.matches("PowerActivity", "自启动设置"))
    }

    @Test
    fun rejectsSetupWizardAndGenericBootPages() {
        assertFalse(AutoStartTargetMatcher.matches("StartupWizardActivity", "设备设置"))
        assertFalse(AutoStartTargetMatcher.matches("BootloaderUnlockActivity", "OEM 解锁"))
    }
}
