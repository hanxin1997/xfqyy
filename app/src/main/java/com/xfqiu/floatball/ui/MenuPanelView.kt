package com.xfqiu.floatball.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.xfqiu.floatball.R
import com.xfqiu.floatball.core.AppShortcut
import com.xfqiu.floatball.core.BallAction
import com.xfqiu.floatball.core.Prefs
import com.xfqiu.floatball.core.dpToPx
import com.xfqiu.floatball.core.loadShortcutIcon

/**
 * 展开态菜单：沿屏幕边缘铺开的一列等宽方块按钮。
 *
 * 竖直条形态是为墨水屏选的——每次展开/收起只脏一条窄带，
 * 扇形或环形菜单会让刷新区域扩散到屏幕中央。
 */
@SuppressLint("ViewConstructor")
class MenuPanelView(
    context: Context,
    private val prefs: Prefs,
    private val onAction: (BallAction) -> Unit,
    private val onIdleCollapse: () -> Unit
) : LinearLayout(context) {

    private val itemSizePx = context.dpToPx(prefs.menuItemSizeDp)
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { onIdleCollapse() }
    private var idleTimeoutMs = 0L

    val desiredWidthPx: Int
        get() = itemSizePx

    val desiredHeightPx: Int
        get() = itemSizePx * childCount

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        buildItems()
    }

    private fun buildItems() {
        removeAllViews()
        addTextItem(R.string.menu_back, BallAction.Back)
        addTextItem(R.string.menu_forward, BallAction.Forward)
        if (prefs.pageTurnEnabled) {
            addTextItem(R.string.menu_prev, BallAction.PrevPage)
            addTextItem(R.string.menu_next, BallAction.NextPage)
        }
        addTextItem(R.string.menu_home, BallAction.Home)
        prefs.shortcuts.forEachIndexed { slot, shortcut -> addAppItem(shortcut, slot) }
        addTextItem(R.string.menu_settings, BallAction.OpenSettings)
    }

    private fun addTextItem(labelRes: Int, action: BallAction) {
        attachItem(textItemView(context.getString(labelRes)), action)
    }

    private fun addAppItem(shortcut: AppShortcut, slot: Int) {
        // 应用被卸载时退回文字标签，而不是留一个空白按钮
        val icon = context.loadShortcutIcon(shortcut)
        val view = if (icon == null) {
            textItemView(shortcut.label.take(FALLBACK_LABEL_CHARS))
        } else {
            iconItemView(icon)
        }
        attachItem(view, BallAction.LaunchApp(slot))
    }

    private fun textItemView(label: String): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        textSize = TEXT_SIZE_SP
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundResource(R.drawable.bg_ink_cell)
    }

    private fun iconItemView(icon: Drawable): ImageView = ImageView(context).apply {
        setImageDrawable(icon)
        scaleType = ImageView.ScaleType.FIT_CENTER
        val padding = (itemSizePx * ICON_PADDING_RATIO).toInt()
        setPadding(padding, padding, padding, padding)
        setBackgroundResource(R.drawable.bg_ink_cell)
    }

    private fun attachItem(view: View, action: BallAction) {
        view.setOnClickListener {
            restartIdleTimer()
            onAction(action)
        }
        addView(view, LayoutParams(itemSizePx, itemSizePx))
    }

    fun onShown() {
        idleTimeoutMs = prefs.autoCollapseSeconds * MS_PER_SECOND
        restartIdleTimer()
    }

    fun onHidden() {
        idleHandler.removeCallbacks(idleRunnable)
    }

    /** 任何触摸都算「还在用」，重新计时。 */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        restartIdleTimer()
        return false
    }

    private fun restartIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
        if (idleTimeoutMs <= 0L) return
        idleHandler.postDelayed(idleRunnable, idleTimeoutMs)
    }

    private companion object {
        const val TEXT_SIZE_SP = 14f
        const val ICON_PADDING_RATIO = 0.18f
        const val FALLBACK_LABEL_CHARS = 2
        const val MS_PER_SECOND = 1000L
    }
}
