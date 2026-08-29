package com.xfqiu.floatball.core

/** 持久化两阶段后台授权，系统设置页期间即使进程被回收也能继续隐藏组件阶段。 */
enum class BackgroundSetupPhase(val storageKey: String) {
    IDLE("idle"),
    WAITING_FOR_NATIVE_RETURN("waiting_native_return");

    companion object {
        fun fromKey(key: String?): BackgroundSetupPhase =
            entries.firstOrNull { it.storageKey == key } ?: IDLE
    }
}
