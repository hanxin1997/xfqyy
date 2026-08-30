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

/**
 * 按优先级排列的窗口类型候选。
 *
 * [degradedFromApplication] 表示本次拿不到普通悬浮窗、只能退到无障碍覆盖层，
 * 调用方据此提示用户，而不是让球悄悄消失。
 *
 * [accessibilityFallbackDisabled] 表示兜底是被用户主动关掉的。全部候选失败时，
 * 调用方据此区分「ROM 拒绝普通悬浮窗」和「无障碍服务没开」，否则提示语会误导。
 */
data class OverlayAttachPlan(
    val candidates: List<OverlayWindowKind>,
    val degradedFromApplication: Boolean,
    val accessibilityFallbackDisabled: Boolean
)

/** 墨水屏优先普通悬浮窗，因为部分电纸书的无障碍覆盖层能显示但不分发触摸。 */
object OverlayModePolicy {

    /**
     * 即使用户显式选了 [OverlayMode.APPLICATION] 却没有权限，也保留无障碍覆盖层兜底：
     * 权限被回收时让球彻底不出现，用户在设置页里就没有任何自救入口了。
     *
     * [strictApplication] 是用户主动要求「强制模式不许回退」，只在显式 [OverlayMode.APPLICATION]
     * 且权限已给时才去掉兜底：
     * - [OverlayMode.AUTO] 的定义就是自动兜底，让开关改它等于两个控件抢一件事；
     * - 权限缺失属于用户能自己授权修好的失败，砍掉唯一候选只是让球白消失，换不到任何信息。
     *
     * 因此候选列表恒非空。
     */
    fun attachPlan(
        mode: OverlayMode,
        canDrawOverlays: Boolean,
        strictApplication: Boolean
    ): OverlayAttachPlan = when (mode) {
        OverlayMode.ACCESSIBILITY -> accessibilityOnlyPlan
        OverlayMode.AUTO -> applicationFirstPlan(canDrawOverlays, fallbackDisabled = false)
        OverlayMode.APPLICATION -> applicationFirstPlan(
            canDrawOverlays,
            fallbackDisabled = strictApplication && canDrawOverlays
        )
    }

    private val accessibilityOnlyPlan = OverlayAttachPlan(
        candidates = listOf(OverlayWindowKind.ACCESSIBILITY),
        degradedFromApplication = false,
        accessibilityFallbackDisabled = false
    )

    private fun applicationFirstPlan(
        canDrawOverlays: Boolean,
        fallbackDisabled: Boolean
    ): OverlayAttachPlan = OverlayAttachPlan(
        candidates = buildList {
            if (canDrawOverlays) add(OverlayWindowKind.APPLICATION)
            if (!fallbackDisabled) add(OverlayWindowKind.ACCESSIBILITY)
        },
        degradedFromApplication = !canDrawOverlays,
        accessibilityFallbackDisabled = fallbackDisabled
    )

    fun selection(requested: OverlayMode, canDrawOverlays: Boolean): OverlayModeSelection =
        if (requested == OverlayMode.APPLICATION && !canDrawOverlays) {
            OverlayModeSelection(modeToApply = null, requestPermission = true)
        } else {
            OverlayModeSelection(modeToApply = requested, requestPermission = false)
        }
}
