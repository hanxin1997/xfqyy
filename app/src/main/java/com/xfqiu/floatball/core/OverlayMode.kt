package com.xfqiu.floatball.core

/** 厂商无障碍覆盖层能显示但不收触摸时，允许用户强制切换普通悬浮窗。 */
enum class OverlayMode(val storageKey: String) {
    AUTO("auto"),
    ACCESSIBILITY("accessibility"),
    APPLICATION("application");

    companion object {
        fun fromKey(key: String?): OverlayMode = entries.firstOrNull { it.storageKey == key } ?: AUTO
    }
}

enum class OverlayWindowKind {
    APPLICATION,
    ACCESSIBILITY
}

data class OverlayModeSelection(
    val modeToApply: OverlayMode?,
    val requestPermission: Boolean
)

/** 墨水屏优先普通悬浮窗，因为部分电纸书的无障碍覆盖层能显示但不分发触摸。 */
object OverlayModePolicy {

    fun candidates(mode: OverlayMode, canDrawOverlays: Boolean): List<OverlayWindowKind> =
        when (mode) {
            OverlayMode.AUTO -> buildList {
                if (canDrawOverlays) add(OverlayWindowKind.APPLICATION)
                add(OverlayWindowKind.ACCESSIBILITY)
            }
            OverlayMode.ACCESSIBILITY -> listOf(OverlayWindowKind.ACCESSIBILITY)
            OverlayMode.APPLICATION -> if (canDrawOverlays) {
                listOf(OverlayWindowKind.APPLICATION)
            } else {
                emptyList()
            }
        }

    fun selection(requested: OverlayMode, canDrawOverlays: Boolean): OverlayModeSelection =
        if (requested == OverlayMode.APPLICATION && !canDrawOverlays) {
            OverlayModeSelection(modeToApply = null, requestPermission = true)
        } else {
            OverlayModeSelection(modeToApply = requested, requestPermission = false)
        }
}
