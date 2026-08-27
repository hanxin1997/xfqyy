package com.xfqiu.floatball.core

/** 悬浮球菜单可执行的全部动作。 */
sealed class BallAction {

    object Back : BallAction()

    /** 回到刚才待过的应用，目标由 [ForegroundTracker] 决定。 */
    object Forward : BallAction()

    object PrevPage : BallAction()

    object NextPage : BallAction()

    object Home : BallAction()

    object OpenSettings : BallAction()

    /** slot 为快捷应用在 [Prefs.shortcuts] 中的下标。 */
    data class LaunchApp(val slot: Int) : BallAction()
}
