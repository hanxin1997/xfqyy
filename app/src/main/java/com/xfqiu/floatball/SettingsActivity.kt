package com.xfqiu.floatball

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.xfqiu.floatball.core.AppShortcut
import com.xfqiu.floatball.core.OverlayMode
import com.xfqiu.floatball.core.OverlayModePolicy
import com.xfqiu.floatball.core.PageTurnMode
import com.xfqiu.floatball.core.Prefs
import com.xfqiu.floatball.core.loadShortcutIcon
import com.xfqiu.floatball.service.FloatBallService
import com.xfqiu.floatball.service.KeepAliveService

/**
 * 参数设置。滑块与开关全部由代码按 spec 列表生成，
 * 避免十几段结构相同的 XML 与手工绑定代码。
 *
 * 所有修改即时落盘，松手时通知服务重建悬浮球。
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var shortcutContainer: LinearLayout
    private lateinit var addShortcutButton: View
    private lateinit var overlayModeGroup: RadioGroup
    private var suppressOverlayModeCallback = false
    private var lastOverlayPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs.of(this)
        shortcutContainer = findViewById(R.id.shortcut_container)
        addShortcutButton = findViewById(R.id.add_shortcut)
        addShortcutButton.setOnClickListener { pickApp() }
        bindPageTurnMode()
        bindOverlayMode()
        fillSliders(R.id.gesture_slider_container, gestureSliders())
        fillSliders(R.id.appearance_slider_container, appearanceSliders())
        fillSwitches(R.id.page_turn_switch_container, pageTurnSwitches())
        fillSwitches(R.id.switch_container, behaviorSwitches())
        renderShortcuts()
        lastOverlayPermission = Settings.canDrawOverlays(this)
    }

    override fun onResume() {
        super.onResume()
        if (!::prefs.isInitialized) return
        val currentPermission = Settings.canDrawOverlays(this)
        val pendingResolved = reconcilePendingOverlayMode(currentPermission)
        if (pendingResolved || currentPermission != lastOverlayPermission) notifyService()
        lastOverlayPermission = currentPermission
    }

    private fun bindPageTurnMode() {
        val group = findViewById<RadioGroup>(R.id.page_turn_group)
        group.check(radioIdOf(prefs.pageTurnMode))
        group.setOnCheckedChangeListener { _, checkedId ->
            prefs.pageTurnMode = modeOf(checkedId)
            notifyService()
        }
    }

    private fun radioIdOf(mode: PageTurnMode): Int = when (mode) {
        PageTurnMode.TAP -> R.id.mode_tap
        PageTurnMode.SWIPE_HORIZONTAL -> R.id.mode_swipe_horizontal
        PageTurnMode.SWIPE_VERTICAL -> R.id.mode_swipe_vertical
    }

    private fun modeOf(radioId: Int): PageTurnMode = when (radioId) {
        R.id.mode_swipe_horizontal -> PageTurnMode.SWIPE_HORIZONTAL
        R.id.mode_swipe_vertical -> PageTurnMode.SWIPE_VERTICAL
        else -> PageTurnMode.TAP
    }

    private fun bindOverlayMode() {
        overlayModeGroup = findViewById(R.id.overlay_mode_group)
        setOverlayModeChecked(prefs.overlayMode)
        overlayModeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressOverlayModeCallback) return@setOnCheckedChangeListener
            val requested = overlayModeOf(checkedId)
            val decision = OverlayModePolicy.selection(
                requested,
                Settings.canDrawOverlays(this)
            )
            if (decision.requestPermission) {
                // 先保留当前可用窗口；授权成功返回后才提交 APPLICATION。
                prefs.pendingOverlayMode = requested
                setOverlayModeChecked(prefs.overlayMode)
                if (!openOverlayPermission()) prefs.pendingOverlayMode = null
                return@setOnCheckedChangeListener
            }
            prefs.pendingOverlayMode = null
            prefs.overlayMode = decision.modeToApply ?: return@setOnCheckedChangeListener
            notifyService()
        }
    }

    private fun reconcilePendingOverlayMode(canDrawOverlays: Boolean): Boolean {
        val pending = prefs.pendingOverlayMode ?: return false
        prefs.pendingOverlayMode = null
        if (pending == OverlayMode.APPLICATION && !canDrawOverlays) {
            setOverlayModeChecked(prefs.overlayMode)
            Toast.makeText(this, R.string.overlay_mode_permission_denied, Toast.LENGTH_LONG).show()
            return false
        }
        prefs.overlayMode = pending
        setOverlayModeChecked(pending)
        return true
    }

    private fun setOverlayModeChecked(mode: OverlayMode) {
        suppressOverlayModeCallback = true
        overlayModeGroup.check(radioIdOf(mode))
        suppressOverlayModeCallback = false
    }

    private fun radioIdOf(mode: OverlayMode): Int = when (mode) {
        OverlayMode.AUTO -> R.id.overlay_mode_auto
        OverlayMode.ACCESSIBILITY -> R.id.overlay_mode_accessibility
        OverlayMode.APPLICATION -> R.id.overlay_mode_application
    }

    private fun overlayModeOf(radioId: Int): OverlayMode = when (radioId) {
        R.id.overlay_mode_accessibility -> OverlayMode.ACCESSIBILITY
        R.id.overlay_mode_application -> OverlayMode.APPLICATION
        else -> OverlayMode.AUTO
    }

    private fun openOverlayPermission(): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        return try {
            startActivity(intent)
            true
        } catch (error: RuntimeException) {
            Toast.makeText(this, R.string.system_page_missing, Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun gestureSliders(): List<SliderSpec> = listOf(
        SliderSpec(
            R.string.slider_prev_x, R.string.unit_percent,
            Prefs.PERCENT_MIN, Prefs.PERCENT_MAX, prefs.prevTapXPercent
        ) { prefs.prevTapXPercent = it },
        SliderSpec(
            R.string.slider_next_x, R.string.unit_percent,
            Prefs.PERCENT_MIN, Prefs.PERCENT_MAX, prefs.nextTapXPercent
        ) { prefs.nextTapXPercent = it },
        SliderSpec(
            R.string.slider_tap_y, R.string.unit_percent,
            Prefs.PERCENT_MIN, Prefs.PERCENT_MAX, prefs.tapYPercent
        ) { prefs.tapYPercent = it },
        SliderSpec(
            R.string.slider_swipe_distance, R.string.unit_percent,
            Prefs.SWIPE_DISTANCE_MIN, Prefs.SWIPE_DISTANCE_MAX, prefs.swipeDistancePercent
        ) { prefs.swipeDistancePercent = it },
        SliderSpec(
            R.string.slider_swipe_duration, R.string.unit_ms,
            Prefs.SWIPE_DURATION_MIN_MS, Prefs.SWIPE_DURATION_MAX_MS, prefs.swipeDurationMs
        ) { prefs.swipeDurationMs = it }
    )

    private fun appearanceSliders(): List<SliderSpec> = listOf(
        SliderSpec(
            R.string.slider_ball_size, R.string.unit_dp,
            Prefs.BALL_SIZE_MIN_DP, Prefs.BALL_SIZE_MAX_DP, prefs.ballSizeDp
        ) { prefs.ballSizeDp = it },
        SliderSpec(
            R.string.slider_menu_size, R.string.unit_dp,
            Prefs.MENU_ITEM_MIN_DP, Prefs.MENU_ITEM_MAX_DP, prefs.menuItemSizeDp
        ) { prefs.menuItemSizeDp = it },
        SliderSpec(
            R.string.slider_edge_margin, R.string.unit_dp,
            Prefs.EDGE_MARGIN_MIN_DP, Prefs.EDGE_MARGIN_MAX_DP, prefs.edgeMarginDp
        ) { prefs.edgeMarginDp = it },
        SliderSpec(
            R.string.slider_auto_collapse, R.string.unit_second,
            Prefs.AUTO_COLLAPSE_MIN_SEC, Prefs.AUTO_COLLAPSE_MAX_SEC, prefs.autoCollapseSeconds
        ) { prefs.autoCollapseSeconds = it }
    )

    private fun fillSliders(containerId: Int, specs: List<SliderSpec>) {
        val container = findViewById<LinearLayout>(containerId)
        specs.forEach { container.addView(sliderRow(container, it)) }
    }

    private fun sliderRow(parent: ViewGroup, spec: SliderSpec): View {
        val row = layoutInflater.inflate(R.layout.view_slider, parent, false)
        val valueLabel = row.findViewById<TextView>(R.id.slider_value)
        row.findViewById<TextView>(R.id.slider_label).setText(spec.labelRes)
        valueLabel.text = getString(spec.unitRes, spec.value)
        val seekBar = row.findViewById<SeekBar>(R.id.slider_seek)
        seekBar.max = spec.max - spec.min
        seekBar.progress = spec.value - spec.min
        seekBar.setOnSeekBarChangeListener(sliderListener(spec, valueLabel))
        return row
    }

    /** 拖动过程只更新读数，松手才通知服务，避免连续重建悬浮球。 */
    private fun sliderListener(spec: SliderSpec, valueLabel: TextView) =
        object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress + spec.min
                valueLabel.text = getString(spec.unitRes, value)
                spec.onChange(value)
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit

            override fun onStopTrackingTouch(bar: SeekBar) = notifyService()
        }

    private fun fillSwitches(containerId: Int, specs: List<SwitchSpec>) {
        val container = findViewById<LinearLayout>(containerId)
        specs.forEach { container.addView(switchRow(container, it)) }
    }

    private fun pageTurnSwitches(): List<SwitchSpec> = listOf(
        SwitchSpec(R.string.switch_page_turn, prefs.pageTurnEnabled) {
            prefs.pageTurnEnabled = it
        }
    )

    private fun behaviorSwitches(): List<SwitchSpec> = listOf(
        SwitchSpec(R.string.switch_hide_ball, prefs.ballHidden) {
            prefs.ballHidden = it
        },
        SwitchSpec(R.string.switch_low_refresh, prefs.lowRefreshDrag) {
            prefs.lowRefreshDrag = it
        },
        SwitchSpec(R.string.switch_keep_alive, prefs.keepAlive) {
            prefs.keepAlive = it
            if (!KeepAliveService.sync(applicationContext, it)) {
                Toast.makeText(this, R.string.keep_alive_start_failed, Toast.LENGTH_LONG).show()
            }
        }
    )

    private fun switchRow(parent: ViewGroup, spec: SwitchSpec): View {
        val view = layoutInflater.inflate(R.layout.view_switch, parent, false) as Switch
        view.setText(spec.labelRes)
        view.isChecked = spec.checked
        view.setOnCheckedChangeListener { _, checked ->
            spec.onChange(checked)
            notifyService()
        }
        return view
    }

    private fun renderShortcuts() {
        val shortcuts = prefs.shortcuts
        shortcutContainer.removeAllViews()
        addShortcutButton.isEnabled = shortcuts.size < Prefs.MAX_SHORTCUTS
        shortcuts.forEachIndexed { index, shortcut ->
            shortcutContainer.addView(shortcutRow(shortcut, index))
        }
    }

    private fun shortcutRow(shortcut: AppShortcut, index: Int): View {
        val row = layoutInflater.inflate(R.layout.item_shortcut, shortcutContainer, false)
        row.findViewById<TextView>(R.id.shortcut_label).text = shortcut.label
        row.findViewById<ImageView>(R.id.shortcut_icon).setImageDrawable(loadShortcutIcon(shortcut))
        row.findViewById<View>(R.id.shortcut_remove).setOnClickListener { removeShortcut(index) }
        return row
    }

    private fun removeShortcut(index: Int) {
        val current = prefs.shortcuts.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        prefs.shortcuts = current
        renderShortcuts()
        notifyService()
    }

    private fun pickApp() {
        startActivityForResult(Intent(this, AppPickerActivity::class.java), REQUEST_PICK_APP)
    }

    /** 零依赖实现下没有 ActivityResult API，沿用平台原生回调。 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_APP || resultCode != RESULT_OK || data == null) return
        val shortcut = AppPickerActivity.readResult(data) ?: return
        appendShortcut(shortcut)
    }

    private fun appendShortcut(shortcut: AppShortcut) {
        val current = prefs.shortcuts
        if (current.size >= Prefs.MAX_SHORTCUTS) return
        val duplicated = current.any {
            it.packageName == shortcut.packageName && it.activityName == shortcut.activityName
        }
        if (duplicated) {
            Toast.makeText(this, R.string.shortcut_duplicated, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.shortcuts = current + shortcut
        renderShortcuts()
        notifyService()
    }

    private fun notifyService() = FloatBallService.notifySettingsChanged()

    private companion object {
        const val REQUEST_PICK_APP = 1
    }
}

private class SliderSpec(
    val labelRes: Int,
    val unitRes: Int,
    val min: Int,
    val max: Int,
    val value: Int,
    val onChange: (Int) -> Unit
)

private class SwitchSpec(
    val labelRes: Int,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit
)
