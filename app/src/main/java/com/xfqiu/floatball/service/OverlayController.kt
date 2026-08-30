package com.xfqiu.floatball.service

import android.annotation.TargetApi
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import com.xfqiu.floatball.R
import com.xfqiu.floatball.core.BallAction
import com.xfqiu.floatball.core.EdgeInsets
import com.xfqiu.floatball.core.DragRefresh
import com.xfqiu.floatball.core.EInkDragRefreshPolicy
import com.xfqiu.floatball.core.OverlayGeometry
import com.xfqiu.floatball.core.OverlayMode
import com.xfqiu.floatball.core.OverlayModePolicy
import com.xfqiu.floatball.core.OverlayWindowKind
import com.xfqiu.floatball.core.PixelBounds
import com.xfqiu.floatball.core.PixelPoint
import com.xfqiu.floatball.core.PixelSize
import com.xfqiu.floatball.core.Prefs
import com.xfqiu.floatball.core.dpToPx
import com.xfqiu.floatball.core.realScreenSize
import com.xfqiu.floatball.ui.FloatBallView
import com.xfqiu.floatball.ui.MenuPanelView
import kotlin.math.max

/**
 * 悬浮窗的全部窗口层操作。窗口仍由无障碍服务持有，但允许在厂商实现异常时
 * 强制使用普通悬浮窗。所有坐标都先经过墨水屏安全区，绝不再贴物理屏幕边缘。
 */
class OverlayController(
    private val context: Context,
    private val onAction: (BallAction) -> Unit
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val params = WindowManager.LayoutParams()

    private var prefs = Prefs.of(context)
    private lateinit var root: LinearLayout
    private lateinit var ball: FloatBallView
    private lateinit var menu: MenuPanelView

    private var attached = false
    private var attachedKind: OverlayWindowKind? = null
    private var expanded = false
    private var dragOriginX = 0
    private var dragOriginY = 0
    private var updateScheduled = false
    private var recoveryScheduled = false
    private var desiredTouchThrough = false
    private var appliedFlags = BASE_FLAGS
    private var systemInsets = EdgeInsets.ZERO
    private val dragRefreshPolicy = EInkDragRefreshPolicy()

    private val dragUpdateRunnable = Runnable {
        updateScheduled = false
        updateLayout()
    }

    private val recoveryRunnable = Runnable {
        recoveryScheduled = false
        recoverWindow()
    }

    private val ballSizePx: Int
        get() = context.dpToPx(prefs.ballSizeDp)

    private val dockedRight: Boolean
        get() {
            val bounds = safeBounds()
            return prefs.ballX + ballSizePx / 2 > (bounds.left + bounds.right) / 2
        }

    fun show() {
        if (attached) return
        prefs = Prefs.of(context)
        if (prefs.ballHidden) return
        desiredTouchThrough = false
        appliedFlags = BASE_FLAGS
        systemInsets = EdgeInsets.ZERO
        ensureInitialPosition()
        buildViews()
        configureParams()
        attachWithFallback()
    }

    fun hide() {
        if (::menu.isInitialized) menu.onHidden()
        handler.removeCallbacksAndMessages(null)
        updateScheduled = false
        recoveryScheduled = false
        desiredTouchThrough = false
        detachQuietly()
        attached = false
        attachedKind = null
        expanded = false
    }

    /** 设置项变化时完整重建，窗口类型也只有 remove/add 后才能切换。 */
    fun reload() {
        hide()
        show()
    }

    fun onScreenChanged() {
        if (!attached) return
        systemInsets = EdgeInsets.ZERO
        root.requestApplyInsets()
        applyGeometry()
        updateLayout()
    }

    /**
     * 派发翻页手势前切换触摸穿透。返回 false 表示系统窗口没有确认应用该 flag，
     * 调用方此时不能继续派发，否则可能把事件再次送给悬浮球本身。
     */
    fun setTouchThrough(enabled: Boolean): Boolean {
        if (!attached) return false
        desiredTouchThrough = enabled
        val targetFlags = flagsFor(enabled)
        if (appliedFlags == targetFlags) {
            params.flags = targetFlags
            return true
        }
        params.flags = targetFlags
        val updated = updateLayout()
        if (!updated && enabled) {
            // 开启穿透失败时回到安全目标；已排队的窗口恢复会按 BASE_FLAGS 重挂载。
            desiredTouchThrough = false
            params.flags = BASE_FLAGS
        }
        return updated
    }

    fun occupiedRect(): Rect? {
        if (!attached) return null
        return Rect(params.x, params.y, params.x + params.width, params.y + params.height)
    }

    private fun ensureInitialPosition() {
        val size = PixelSize(ballSizePx, ballSizePx)
        val bounds = safeBounds()
        val saved = if (
            prefs.ballX == Prefs.UNSET_POSITION || prefs.ballY == Prefs.UNSET_POSITION
        ) {
            OverlayGeometry.initialPosition(size, bounds)
        } else {
            OverlayGeometry.clamp(PixelPoint(prefs.ballX, prefs.ballY), size, bounds)
        }
        // 旧版本保存的物理贴边坐标会在这里自动迁进可触摸安全区。
        prefs.ballX = saved.x
        prefs.ballY = saved.y
    }

    private fun buildViews() {
        ball = FloatBallView(context).apply {
            onTap = this@OverlayController::toggle
            onDragStart = this@OverlayController::beginDrag
            onDragMove = this@OverlayController::onDragged
            onDragEnd = this@OverlayController::endDrag
            onDragCancel = this@OverlayController::cancelDrag
        }
        menu = MenuPanelView(context, prefs, ::dispatchAction, ::collapse)
        root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnApplyWindowInsetsListener { _, insets ->
                onWindowInsets(insets)
                insets
            }
        }
        layoutChildren()
    }

    private fun onWindowInsets(insets: WindowInsets) {
        val next = readSystemInsets(insets)
        if (next == systemInsets) return
        systemInsets = next
        handler.post {
            if (!attached) return@post
            applyGeometry()
            updateLayout()
        }
    }

    /** 菜单只向屏幕内侧展开，减少整屏墨水刷新面积。 */
    private fun layoutChildren() {
        val size = ballSizePx
        val ballParams = LinearLayout.LayoutParams(size, size)
        val menuParams = LinearLayout.LayoutParams(menu.desiredWidthPx, menu.desiredHeightPx)
        root.removeAllViews()
        if (dockedRight) {
            root.addView(menu, menuParams)
            root.addView(ball, ballParams)
        } else {
            root.addView(ball, ballParams)
            root.addView(menu, menuParams)
        }
        menu.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    private fun configureParams() {
        params.gravity = Gravity.TOP or Gravity.START
        params.format = PixelFormat.TRANSLUCENT
        params.flags = flagsFor(desiredTouchThrough)
        applyGeometry()
    }

    private fun applyGeometry() {
        val size = ballSizePx
        val bounds = safeBounds()
        if (expanded) {
            params.width = menu.desiredWidthPx + size
            params.height = max(menu.desiredHeightPx, size)
            val raw = PixelPoint(
                x = if (dockedRight) prefs.ballX - menu.desiredWidthPx else prefs.ballX,
                y = prefs.ballY - (params.height - size) / 2
            )
            val position = OverlayGeometry.clamp(
                raw,
                PixelSize(params.width, params.height),
                bounds
            )
            params.x = position.x
            params.y = position.y
            return
        }

        params.width = size
        params.height = size
        val position = OverlayGeometry.clamp(
            PixelPoint(prefs.ballX, prefs.ballY),
            PixelSize(size, size),
            bounds
        )
        params.x = position.x
        params.y = position.y
        prefs.ballX = position.x
        prefs.ballY = position.y
    }

    private fun safeBounds(): PixelBounds {
        val screen = context.realScreenSize()
        return OverlayGeometry.safeBounds(
            screen = PixelSize(screen.x, screen.y),
            insets = systemInsets,
            edgeMarginPx = context.dpToPx(prefs.edgeMarginDp)
        )
    }

    private fun attachWithFallback() {
        val manager = windowManager ?: return
        val plan = OverlayModePolicy.attachPlan(
            mode = prefs.overlayMode,
            canDrawOverlays = Settings.canDrawOverlays(context),
            strictApplication = prefs.strictApplicationOverlay
        )
        // 只有用户显式选了普通悬浮窗才提示：AUTO 退到无障碍覆盖层是正常兜底，
        // 而 notifySettingsChanged() 每次回到引导页都会重建窗口，无条件弹提示会变成骚扰。
        if (plan.degradedFromApplication && prefs.overlayMode == OverlayMode.APPLICATION) {
            Toast.makeText(context, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
        }
        params.flags = flagsFor(desiredTouchThrough)
        for (kind in plan.candidates) {
            params.type = windowTypeOf(kind)
            try {
                manager.addView(root, params)
                attached = true
                attachedKind = kind
                appliedFlags = params.flags
                root.requestApplyInsets()
                Log.i(TAG, "悬浮窗已连接，窗口类型=$kind")
                return
            } catch (error: RuntimeException) {
                Log.w(TAG, "addView 失败，窗口类型=$kind", error)
                detachQuietly()
                attached = false
                attachedKind = null
            }
        }
        // 用户主动禁了兜底，失败原因必然是 ROM 拒绝普通悬浮窗，
        // 此时再提示「请确认无障碍服务已开启」是误导。
        val message =
            if (plan.accessibilityFallbackDisabled) R.string.overlay_strict_rejected
            else R.string.overlay_failed
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /** 真正 addView 成功的窗口类型，未挂载时为 null。供引导页展示，便于定位「球点不动」。 */
    fun activeWindowKind(): OverlayWindowKind? = if (attached) attachedKind else null

    private fun windowTypeOf(kind: OverlayWindowKind): Int = when (kind) {
        OverlayWindowKind.ACCESSIBILITY -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        OverlayWindowKind.APPLICATION ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
    }

    private fun detachQuietly() {
        val manager = windowManager ?: return
        if (!::root.isInitialized) return
        runCatching { manager.removeViewImmediate(root) }
    }

    private fun toggle() {
        if (expanded) collapse() else expand()
    }

    private fun expand() {
        if (!attached || expanded) return
        expanded = true
        layoutChildren()
        applyGeometry()
        updateLayout()
        menu.onShown()
    }

    private fun collapse() {
        if (!attached || !expanded) return
        expanded = false
        menu.onHidden()
        menu.visibility = View.GONE
        applyGeometry()
        updateLayout()
    }

    private fun dispatchAction(action: BallAction) {
        if (action !in KEEP_OPEN_ACTIONS) collapse()
        onAction(action)
    }

    private fun beginDrag() {
        if (expanded) collapse()
        dragRefreshPolicy.reset()
        dragOriginX = params.x
        dragOriginY = params.y
    }

    private fun onDragged(dx: Int, dy: Int) {
        val position = OverlayGeometry.clamp(
            PixelPoint(dragOriginX + dx, dragOriginY + dy),
            PixelSize(params.width, params.height),
            safeBounds()
        )
        params.x = position.x
        params.y = position.y
        when (dragRefreshPolicy.next(prefs.lowRefreshDrag)) {
            DragRefresh.IMMEDIATE -> {
                cancelScheduledDragUpdate()
                updateLayout()
            }
            DragRefresh.NORMAL_FREQUENCY -> scheduleUpdate(DRAG_THROTTLE_MS)
            DragRefresh.LOW_FREQUENCY -> scheduleUpdate(LOW_REFRESH_DRAG_MS)
        }
    }

    private fun endDrag() {
        cancelScheduledDragUpdate()
        val position = OverlayGeometry.dock(
            PixelPoint(params.x, params.y),
            PixelSize(params.width, params.height),
            safeBounds()
        )
        params.x = position.x
        params.y = position.y
        prefs.ballX = position.x
        prefs.ballY = position.y
        updateLayout()
    }

    private fun cancelDrag() {
        cancelScheduledDragUpdate()
        // 位置没变就别刷：噪声尖峰被判回点击时会先复位再展开菜单，否则墨水屏要白刷一次。
        if (params.x == dragOriginX && params.y == dragOriginY) return
        params.x = dragOriginX
        params.y = dragOriginY
        // CANCEL 多由系统侧边区抢占造成，只恢复原位，不吸边、不写入 Prefs。
        updateLayout()
    }

    private fun scheduleUpdate(delayMs: Long) {
        if (updateScheduled) return
        updateScheduled = true
        handler.postDelayed(dragUpdateRunnable, delayMs)
    }

    private fun cancelScheduledDragUpdate() {
        handler.removeCallbacks(dragUpdateRunnable)
        updateScheduled = false
    }

    private fun updateLayout(): Boolean {
        if (!attached) return false
        val manager = windowManager ?: return false
        return try {
            manager.updateViewLayout(root, params)
            appliedFlags = params.flags
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "updateViewLayout 失败，将重挂载窗口", error)
            scheduleRecovery()
            false
        }
    }

    private fun scheduleRecovery() {
        if (!attached || recoveryScheduled) return
        recoveryScheduled = true
        handler.postDelayed(recoveryRunnable, RECOVERY_DELAY_MS)
    }

    private fun recoverWindow() {
        if (!attached) return
        Log.w(TAG, "重挂载悬浮窗，旧窗口类型=$attachedKind")
        detachQuietly()
        attached = false
        attachedKind = null
        attachWithFallback()
    }

    private fun flagsFor(touchThrough: Boolean): Int =
        if (touchThrough) BASE_FLAGS or FLAG_THROUGH else BASE_FLAGS

    private fun readSystemInsets(insets: WindowInsets): EdgeInsets = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> readInsetsApi30(insets)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> readInsetsApi29(insets)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> readInsetsApi28(insets)
        else -> legacyStableInsets(insets)
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun readInsetsApi30(insets: WindowInsets): EdgeInsets {
        val bars = insets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        ).toEdgeInsets()
        val gestures = insets.getInsets(
            WindowInsets.Type.systemGestures() or WindowInsets.Type.mandatorySystemGestures()
        ).toEdgeInsets()
        return maxInsets(bars, gestures)
    }

    @TargetApi(Build.VERSION_CODES.Q)
    @Suppress("DEPRECATION")
    private fun readInsetsApi29(insets: WindowInsets): EdgeInsets = maxInsets(
        legacyStableInsets(insets),
        insets.systemGestureInsets.toEdgeInsets(),
        insets.mandatorySystemGestureInsets.toEdgeInsets(),
        cutoutInsets(insets)
    )

    @TargetApi(Build.VERSION_CODES.P)
    private fun readInsetsApi28(insets: WindowInsets): EdgeInsets = maxInsets(
        legacyStableInsets(insets),
        cutoutInsets(insets)
    )

    @Suppress("DEPRECATION")
    private fun legacyStableInsets(insets: WindowInsets): EdgeInsets = EdgeInsets(
        insets.stableInsetLeft,
        insets.stableInsetTop,
        insets.stableInsetRight,
        insets.stableInsetBottom
    )

    @TargetApi(Build.VERSION_CODES.P)
    private fun cutoutInsets(insets: WindowInsets): EdgeInsets {
        val cutout = insets.displayCutout ?: return EdgeInsets.ZERO
        return EdgeInsets(
            cutout.safeInsetLeft,
            cutout.safeInsetTop,
            cutout.safeInsetRight,
            cutout.safeInsetBottom
        )
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun android.graphics.Insets.toEdgeInsets(): EdgeInsets =
        EdgeInsets(left, top, right, bottom)

    private fun maxInsets(vararg values: EdgeInsets): EdgeInsets = EdgeInsets(
        left = values.maxOfOrNull { it.left } ?: 0,
        top = values.maxOfOrNull { it.top } ?: 0,
        right = values.maxOfOrNull { it.right } ?: 0,
        bottom = values.maxOfOrNull { it.bottom } ?: 0
    )

    private companion object {
        const val TAG = "OverlayController"
        const val DRAG_THROTTLE_MS = 60L
        // 240ms 叠加残影会让用户以为球拖不动；120ms 仍远低于普通手机刷新频率。
        const val LOW_REFRESH_DRAG_MS = 120L
        const val RECOVERY_DELAY_MS = 50L
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        const val FLAG_THROUGH = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val KEEP_OPEN_ACTIONS = setOf(BallAction.Back, BallAction.PrevPage, BallAction.NextPage)
    }
}
