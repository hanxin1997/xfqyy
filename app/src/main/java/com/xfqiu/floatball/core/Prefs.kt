package com.xfqiu.floatball.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 全部可调参数的唯一出口。故意不做内存缓存：配置读写频率极低，
 * 直读 SharedPreferences 可以彻底避免 Service 与 Activity 两侧的状态不一致。
 *
 * 所有 setter 都做区间收敛，脏数据不会传导到手势坐标计算。
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    /** 折叠态悬浮球左上角坐标，[UNSET_POSITION] 表示尚未初始化。 */
    var ballX: Int
        get() = sp.getInt(KEY_BALL_X, UNSET_POSITION)
        set(value) = sp.edit().putInt(KEY_BALL_X, value).apply()

    var ballY: Int
        get() = sp.getInt(KEY_BALL_Y, UNSET_POSITION)
        set(value) = sp.edit().putInt(KEY_BALL_Y, value).apply()

    var ballSizeDp: Int
        get() = sp.getInt(KEY_BALL_SIZE, BALL_SIZE_DEFAULT_DP)
        set(value) = putClamped(KEY_BALL_SIZE, value, BALL_SIZE_MIN_DP, BALL_SIZE_MAX_DP)

    var menuItemSizeDp: Int
        get() = sp.getInt(KEY_MENU_ITEM_SIZE, MENU_ITEM_DEFAULT_DP)
        set(value) = putClamped(KEY_MENU_ITEM_SIZE, value, MENU_ITEM_MIN_DP, MENU_ITEM_MAX_DP)

    /** 电纸书侧边常有系统翻页/返回触控区，悬浮球不能贴物理边缘。 */
    var edgeMarginDp: Int
        get() = sp.getInt(KEY_EDGE_MARGIN, EDGE_MARGIN_DEFAULT_DP)
        set(value) = putClamped(KEY_EDGE_MARGIN, value, EDGE_MARGIN_MIN_DP, EDGE_MARGIN_MAX_DP)

    var overlayMode: OverlayMode
        get() = OverlayMode.fromKey(sp.getString(KEY_OVERLAY_MODE, null))
        set(value) = sp.edit().putString(KEY_OVERLAY_MODE, value.storageKey).apply()

    /** 普通悬浮窗授权页返回后才提交，避免拒绝授权时把现有球拆掉。 */
    var pendingOverlayMode: OverlayMode?
        get() = sp.getString(KEY_PENDING_OVERLAY_MODE, null)?.let { OverlayMode.fromKey(it) }
        set(value) {
            val editor = sp.edit()
            if (value == null) editor.remove(KEY_PENDING_OVERLAY_MODE)
            else editor.putString(KEY_PENDING_OVERLAY_MODE, value.storageKey)
            editor.apply()
        }

    var pageTurnMode: PageTurnMode
        get() = PageTurnMode.fromKey(sp.getString(KEY_PAGE_TURN_MODE, null))
        set(value) = sp.edit().putString(KEY_PAGE_TURN_MODE, value.storageKey).apply()

    /** 菜单中是否显示上页/下页。默认关闭：多数阅读软件自带翻页，不需要注入手势。 */
    var pageTurnEnabled: Boolean
        get() = sp.getBoolean(KEY_PAGE_TURN_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_PAGE_TURN_ENABLED, value).apply()

    /** 全局隐藏悬浮球，只留常驻通知点击回桌面。设置页仍可从桌面图标进入。 */
    var ballHidden: Boolean
        get() = sp.getBoolean(KEY_BALL_HIDDEN, false)
        set(value) = sp.edit().putBoolean(KEY_BALL_HIDDEN, value).apply()

    /** 上一页点击点的横向位置，屏幕宽度百分比。 */
    var prevTapXPercent: Int
        get() = sp.getInt(KEY_PREV_TAP_X, PREV_TAP_X_DEFAULT)
        set(value) = putClamped(KEY_PREV_TAP_X, value, PERCENT_MIN, PERCENT_MAX)

    var nextTapXPercent: Int
        get() = sp.getInt(KEY_NEXT_TAP_X, NEXT_TAP_X_DEFAULT)
        set(value) = putClamped(KEY_NEXT_TAP_X, value, PERCENT_MIN, PERCENT_MAX)

    /** 点击点与横向滑动路径的纵向位置，屏幕高度百分比。 */
    var tapYPercent: Int
        get() = sp.getInt(KEY_TAP_Y, TAP_Y_DEFAULT)
        set(value) = putClamped(KEY_TAP_Y, value, PERCENT_MIN, PERCENT_MAX)

    var swipeDistancePercent: Int
        get() = sp.getInt(KEY_SWIPE_DISTANCE, SWIPE_DISTANCE_DEFAULT)
        set(value) = putClamped(KEY_SWIPE_DISTANCE, value, SWIPE_DISTANCE_MIN, SWIPE_DISTANCE_MAX)

    /** 滑动耗时。过短会被部分应用判定为点击而非滑动。 */
    var swipeDurationMs: Int
        get() = sp.getInt(KEY_SWIPE_DURATION, SWIPE_DURATION_DEFAULT_MS)
        set(value) = putClamped(KEY_SWIPE_DURATION, value, SWIPE_DURATION_MIN_MS, SWIPE_DURATION_MAX_MS)

    /** 菜单无操作自动收起的秒数，0 表示不自动收起。 */
    var autoCollapseSeconds: Int
        get() = sp.getInt(KEY_AUTO_COLLAPSE, AUTO_COLLAPSE_DEFAULT_SEC)
        set(value) = putClamped(KEY_AUTO_COLLAPSE, value, AUTO_COLLAPSE_MIN_SEC, AUTO_COLLAPSE_MAX_SEC)

    /** 开启后使用低频位置反馈，兼顾可拖动感知与墨水屏残影控制。 */
    var lowRefreshDrag: Boolean
        get() = sp.getBoolean(KEY_LOW_REFRESH_DRAG, true)
        set(value) = sp.edit().putBoolean(KEY_LOW_REFRESH_DRAG, value).apply()

    /** 常驻通知：兼任进程保活与点击回桌面。 */
    var keepAlive: Boolean
        get() = sp.getBoolean(KEY_KEEP_ALIVE, true)
        set(value) = sp.edit().putBoolean(KEY_KEEP_ALIVE, value).apply()

    /** 用户是否已完整走过“原生后台设置 → 隐藏组件”两阶段引导。 */
    var backgroundGuideShown: Boolean
        get() = sp.getBoolean(KEY_BACKGROUND_GUIDE_SHOWN, false)
        set(value) = sp.edit().putBoolean(KEY_BACKGROUND_GUIDE_SHOWN, value).apply()

    var backgroundSetupPhase: BackgroundSetupPhase
        get() = BackgroundSetupPhase.fromKey(sp.getString(KEY_BACKGROUND_SETUP_PHASE, null))
        set(value) = sp.edit().putString(KEY_BACKGROUND_SETUP_PHASE, value.storageKey).apply()

    var shortcuts: List<AppShortcut>
        get() = readShortcuts()
        set(value) = writeShortcuts(value)

    private fun readShortcuts(): List<AppShortcut> {
        val raw = sp.getString(KEY_SHORTCUTS, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(RECORD_SEPARATOR).mapNotNull { AppShortcut.fromStorage(it) }
    }

    private fun writeShortcuts(shortcuts: List<AppShortcut>) {
        val encoded = shortcuts.take(MAX_SHORTCUTS)
            .joinToString(RECORD_SEPARATOR) { it.toStorage() }
        sp.edit().putString(KEY_SHORTCUTS, encoded).apply()
    }

    private fun putClamped(key: String, value: Int, min: Int, max: Int) {
        sp.edit().putInt(key, value.coerceIn(min, max)).apply()
    }

    companion object {

        const val UNSET_POSITION = -1

        const val BALL_SIZE_MIN_DP = 36
        const val BALL_SIZE_MAX_DP = 72
        const val BALL_SIZE_DEFAULT_DP = 48

        const val MENU_ITEM_MIN_DP = 44
        const val MENU_ITEM_MAX_DP = 80
        const val MENU_ITEM_DEFAULT_DP = 56

        const val EDGE_MARGIN_MIN_DP = 8
        const val EDGE_MARGIN_MAX_DP = 40
        const val EDGE_MARGIN_DEFAULT_DP = 16

        const val PERCENT_MIN = 5
        const val PERCENT_MAX = 95
        const val PREV_TAP_X_DEFAULT = 20
        const val NEXT_TAP_X_DEFAULT = 80
        const val TAP_Y_DEFAULT = 50

        const val SWIPE_DISTANCE_MIN = 20
        const val SWIPE_DISTANCE_MAX = 90
        const val SWIPE_DISTANCE_DEFAULT = 60

        const val SWIPE_DURATION_MIN_MS = 80
        const val SWIPE_DURATION_MAX_MS = 600
        const val SWIPE_DURATION_DEFAULT_MS = 200

        const val AUTO_COLLAPSE_MIN_SEC = 0
        const val AUTO_COLLAPSE_MAX_SEC = 15
        const val AUTO_COLLAPSE_DEFAULT_SEC = 5

        const val MAX_SHORTCUTS = 6

        private const val FILE_NAME = "ink_float_ball"
        private const val RECORD_SEPARATOR = "\n"

        private const val KEY_BALL_X = "ball_x"
        private const val KEY_BALL_Y = "ball_y"
        private const val KEY_BALL_SIZE = "ball_size_dp"
        private const val KEY_MENU_ITEM_SIZE = "menu_item_size_dp"
        private const val KEY_EDGE_MARGIN = "edge_margin_dp"
        private const val KEY_OVERLAY_MODE = "overlay_mode"
        private const val KEY_PENDING_OVERLAY_MODE = "pending_overlay_mode"
        private const val KEY_PAGE_TURN_MODE = "page_turn_mode"
        private const val KEY_PAGE_TURN_ENABLED = "page_turn_enabled"
        private const val KEY_BALL_HIDDEN = "ball_hidden"
        private const val KEY_PREV_TAP_X = "prev_tap_x"
        private const val KEY_NEXT_TAP_X = "next_tap_x"
        private const val KEY_TAP_Y = "tap_y"
        private const val KEY_SWIPE_DISTANCE = "swipe_distance"
        private const val KEY_SWIPE_DURATION = "swipe_duration"
        private const val KEY_AUTO_COLLAPSE = "auto_collapse_sec"
        private const val KEY_LOW_REFRESH_DRAG = "low_refresh_drag"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val KEY_BACKGROUND_GUIDE_SHOWN = "background_guide_shown"
        private const val KEY_BACKGROUND_SETUP_PHASE = "background_setup_phase"
        private const val KEY_SHORTCUTS = "shortcuts"

        fun of(context: Context): Prefs =
            Prefs(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    }
}
