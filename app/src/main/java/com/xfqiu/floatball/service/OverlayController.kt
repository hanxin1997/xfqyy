package com.xfqiu.floatball.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import com.xfqiu.floatball.R
import com.xfqiu.floatball.core.BallAction
import com.xfqiu.floatball.core.Prefs
import com.xfqiu.floatball.core.dpToPx
import com.xfqiu.floatball.core.realScreenSize
import com.xfqiu.floatball.ui.FloatBallView
import com.xfqiu.floatball.ui.MenuPanelView

/**
 * 悬浮窗的全部窗口层操作。由 [FloatBallService] 持有，因此可以使用
 * TYPE_ACCESSIBILITY_OVERLAY —— 这个类型不需要 SYSTEM_ALERT_WINDOW 权限。
 *
 * 球与菜单共用一个窗口：统一一套坐标、一次 updateViewLayout 就能切换形态，
 * 也让触摸穿透标记只需管理一处。
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
    private var expanded = false
    private var dragOriginX = 0
    private var dragOriginY = 0
    private var updateScheduled = false

    private val ballSizePx: Int
        get() = context.dpToPx(prefs.ballSizeDp)

    private val dockedRight: Boolean
        get() {
            val screenWidth = context.realScreenSize().x
            if (screenWidth <= 0) return true
            return prefs.ballX + ballSizePx / 2 > screenWidth / 2
        }

    fun show() {
        if (attached) return
        prefs = Prefs.of(context)
        if (prefs.ballHidden) return
        ensureInitialPosition()
        buildViews()
        configureParams()
        attachWithFallback()
    }

    fun hide() {
        if (!attached) return
        menu.onHidden()
        handler.removeCallbacksAndMessages(null)
        updateScheduled = false
        detachQuietly()
        attached = false
        expanded = false
    }

    /**
     * 设置项变更后整体重建，避免逐项同步时漏掉某个尺寸或快捷项。
     * 不能因为当前未显示就早退——隐藏开关关掉时正是要从未显示恢复成显示。
     */
    fun reload() {
        hide()
        show()
    }

    /** 屏幕旋转后重新收敛位置，否则球可能落在新屏幕之外。 */
    fun onScreenChanged() {
        if (!attached) return
        applyGeometry()
        updateLayout()
    }

    /** 派发手势期间打开触摸穿透，让注入的事件落到下层应用而不是被自己吃掉。 */
    fun setTouchThrough(enabled: Boolean) {
        if (!attached) return
        val target = if (enabled) BASE_FLAGS or FLAG_THROUGH else BASE_FLAGS
        if (params.flags == target) return
        params.flags = target
        updateLayout()
    }

    /** 当前窗口占据的屏幕矩形，供手势坐标避让使用。 */
    fun occupiedRect(): Rect? {
        if (!attached) return null
        return Rect(params.x, params.y, params.x + params.width, params.y + params.height)
    }

    private fun ensureInitialPosition() {
        if (prefs.ballX != Prefs.UNSET_POSITION && prefs.ballY != Prefs.UNSET_POSITION) return
        val screen = context.realScreenSize()
        val size = ballSizePx
        prefs.ballX = maxOf(0, screen.x - size)
        prefs.ballY = maxOf(0, screen.y / 2 - size / 2)
    }

    private fun buildViews() {
        ball = FloatBallView(context).apply {
            onTap = this@OverlayController::toggle
            onDragStart = this@OverlayController::beginDrag
            onDragMove = this@OverlayController::onDragged
            onDragEnd = this@OverlayController::endDrag
        }
        menu = MenuPanelView(context, prefs, ::dispatchAction, ::collapse)
        root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        layoutChildren()
    }

    /** 菜单朝屏幕内侧展开：球贴右边缘时菜单在左，反之在右。 */
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
        params.flags = BASE_FLAGS
        applyGeometry()
    }

    private fun applyGeometry() {
        val size = ballSizePx
        if (expanded) {
            params.width = menu.desiredWidthPx + size
            params.height = maxOf(menu.desiredHeightPx, size)
            params.x = if (dockedRight) prefs.ballX - menu.desiredWidthPx else prefs.ballX
            params.y = prefs.ballY - (params.height - size) / 2
        } else {
            params.width = size
            params.height = size
            params.x = prefs.ballX
            params.y = prefs.ballY
        }
        clampToScreen(context.realScreenSize())
    }

    /** 折叠态顺带把收敛结果写回配置，旋转屏幕后记录的位置才不会失效。 */
    private fun clampToScreen(screen: Point) {
        params.x = params.x.coerceIn(0, maxOf(0, screen.x - params.width))
        params.y = params.y.coerceIn(0, maxOf(0, screen.y - params.height))
        if (expanded) return
        prefs.ballX = params.x
        prefs.ballY = params.y
    }

    private fun attachWithFallback() {
        val manager = windowManager ?: return
        for (type in candidateWindowTypes()) {
            params.type = type
            try {
                manager.addView(root, params)
                attached = true
                return
            } catch (error: RuntimeException) {
                Log.w(TAG, "addView 失败，窗口类型=$type", error)
                detachQuietly()
            }
        }
        Toast.makeText(context, R.string.overlay_failed, Toast.LENGTH_LONG).show()
    }

    /**
     * 无障碍覆盖层优先：不需要额外权限。失败再退到普通悬浮窗类型，
     * 那一档需要用户已授予 SYSTEM_ALERT_WINDOW。
     */
    private fun candidateWindowTypes(): List<Int> {
        val fallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return listOf(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, fallback)
    }

    private fun detachQuietly() {
        val manager = windowManager ?: return
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

    /** 翻页和返回都可能连按，保持菜单展开可以少刷几次屏；其余动作会切走前台，先收起。 */
    private fun dispatchAction(action: BallAction) {
        if (action !in KEEP_OPEN_ACTIONS) collapse()
        onAction(action)
    }

    private fun beginDrag() {
        if (expanded) collapse()
        dragOriginX = params.x
        dragOriginY = params.y
    }

    private fun onDragged(dx: Int, dy: Int) {
        val screen = context.realScreenSize()
        params.x = (dragOriginX + dx).coerceIn(0, maxOf(0, screen.x - params.width))
        params.y = (dragOriginY + dy).coerceIn(0, maxOf(0, screen.y - params.height))
        if (prefs.lowRefreshDrag) return
        scheduleUpdate()
    }

    private fun endDrag() {
        val screen = context.realScreenSize()
        val snapRight = params.x + params.width / 2 > screen.x / 2
        params.x = if (snapRight) maxOf(0, screen.x - params.width) else 0
        prefs.ballX = params.x
        prefs.ballY = params.y
        updateLayout()
    }

    /** 合并高频拖动事件，墨水屏经不起逐帧刷新。 */
    private fun scheduleUpdate() {
        if (updateScheduled) return
        updateScheduled = true
        handler.postDelayed({
            updateScheduled = false
            updateLayout()
        }, DRAG_THROTTLE_MS)
    }

    private fun updateLayout() {
        if (!attached) return
        val manager = windowManager ?: return
        runCatching { manager.updateViewLayout(root, params) }
            .onFailure { Log.w(TAG, "updateViewLayout 失败", it) }
    }

    private companion object {
        const val TAG = "OverlayController"
        const val DRAG_THROTTLE_MS = 60L
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        const val FLAG_THROUGH = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val KEEP_OPEN_ACTIONS = setOf(BallAction.Back, BallAction.PrevPage, BallAction.NextPage)
    }
}
