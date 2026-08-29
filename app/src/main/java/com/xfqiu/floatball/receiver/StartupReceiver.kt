package com.xfqiu.floatball.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xfqiu.floatball.core.Prefs
import com.xfqiu.floatball.service.KeepAliveService

/** 开机和应用升级后恢复常驻通知；无障碍服务仍只能由系统按用户授权重新绑定。 */
class StartupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        if (!Prefs.of(context).keepAlive) return
        if (!KeepAliveService.sync(context, true)) {
            Log.w(TAG, "启动广播到达，但保活服务恢复失败: $action")
        }
    }

    private companion object {
        const val TAG = "StartupReceiver"
        val SUPPORTED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
