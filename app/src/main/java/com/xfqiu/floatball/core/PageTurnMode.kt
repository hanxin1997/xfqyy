package com.xfqiu.floatball.core

/**
 * 翻页手势的实现方式。不同阅读器对翻页操作的识别差异很大，
 * 因此三种都实现并交由用户切换，而不是猜测哪种通用。
 */
enum class PageTurnMode(val storageKey: String) {

    /** 点击屏幕左右区域，绝大多数阅读器通用。 */
    TAP("tap"),

    /** 横向滑动，适合漫画、图片类以及不响应点击翻页的应用。 */
    SWIPE_HORIZONTAL("swipe_h"),

    /** 纵向滑动，适合网页、连续滚动模式的 PDF。 */
    SWIPE_VERTICAL("swipe_v");

    companion object {

        fun fromKey(key: String?): PageTurnMode =
            values().firstOrNull { it.storageKey == key } ?: TAP
    }
}
