package com.xfqiu.floatball.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoStartTargetsTest {

    @Test
    fun candidates_includeHiddenMiuiManagerWithoutMakingItTheOnlyPath() {
        val target = AutoStartTargets.candidates.first {
            it.className == "com.miui.permcenter.autostart.AutoStartManagementActivity"
        }

        assertEquals("com.miui.securitycenter", target.packageName)
        assertEquals(
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
            target.className
        )
    }

    @Test
    fun nativeAndroid11HighPowerActivity_isAlsoAvailableAsDirectFallback() {
        assertEquals("com.android.settings", NativeBackgroundTargets.highPowerApplications.packageName)
        assertEquals(
            "com.android.settings.Settings\$HighPowerApplicationsActivity",
            NativeBackgroundTargets.highPowerApplications.className
        )
    }

    @Test
    fun miuiOfficialPermissionEditorAction_isRetainedForClassNameVariants() {
        assertEquals("miui.intent.action.APP_PERM_EDITOR", AutoStartTargets.MIUI_PERMISSION_EDITOR_ACTION)
    }
}
