package com.xfqiu.floatball

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.xfqiu.floatball.core.AppShortcut
import com.xfqiu.floatball.core.toInkGray
import java.text.Collator
import java.util.Locale

/**
 * 应用选择器。取图标与读标签都要访问 APK 资源，几十个应用足以卡住主线程，
 * 因此整个查询放到后台线程，加载完成后一次性交给 ListView。
 */
class AppPickerActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var stateLabel: TextView
    private var adapter: AppAdapter? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        listView = findViewById(R.id.app_list)
        stateLabel = findViewById(R.id.app_list_state)
        listView.setOnItemClickListener { _, _, position, _ -> pick(position) }
        loadAsync()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadAsync() {
        stateLabel.setText(R.string.app_list_loading)
        Thread({
            val apps = queryLaunchableApps()
            handler.post { onLoaded(apps) }
        }, LOADER_THREAD_NAME).start()
    }

    private fun onLoaded(apps: List<AppEntry>) {
        if (isFinishing) return
        if (apps.isEmpty()) {
            stateLabel.setText(R.string.app_list_empty)
            return
        }
        stateLabel.visibility = View.GONE
        adapter = AppAdapter(this, apps).also { listView.adapter = it }
    }

    /**
     * 依赖 manifest 里的 `<queries>` 与 QUERY_ALL_PACKAGES：Android 11 起
     * 缺少声明时这里只会返回自己，列表会空。
     */
    private fun queryLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.getDefault())
        val byLabel = Comparator<AppEntry> { left, right ->
            collator.compare(left.shortcut.label, right.shortcut.label)
        }
        return packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != packageName }
            .map { toEntry(it) }
            .sortedWith(byLabel)
            .toList()
    }

    private fun toEntry(info: ResolveInfo): AppEntry {
        val activityInfo = info.activityInfo
        val label = info.loadLabel(packageManager).toString()
        val shortcut = AppShortcut(activityInfo.packageName, activityInfo.name, label)
        return AppEntry(shortcut, info.loadIcon(packageManager).toInkGray())
    }

    private fun pick(position: Int) {
        val entry = adapter?.getItem(position) ?: return
        val data = Intent()
            .putExtra(EXTRA_PACKAGE, entry.shortcut.packageName)
            .putExtra(EXTRA_ACTIVITY, entry.shortcut.activityName)
            .putExtra(EXTRA_LABEL, entry.shortcut.label)
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {

        private const val EXTRA_PACKAGE = "package_name"
        private const val EXTRA_ACTIVITY = "activity_name"
        private const val EXTRA_LABEL = "label"
        private const val LOADER_THREAD_NAME = "app-picker-loader"

        fun readResult(data: Intent): AppShortcut? {
            val packageName = data.getStringExtra(EXTRA_PACKAGE) ?: return null
            val activityName = data.getStringExtra(EXTRA_ACTIVITY) ?: return null
            val label = data.getStringExtra(EXTRA_LABEL) ?: return null
            return AppShortcut(packageName, activityName, label)
        }
    }
}

private class AppEntry(val shortcut: AppShortcut, val icon: Drawable)

private class AppAdapter(
    private val context: Context,
    private val entries: List<AppEntry>
) : BaseAdapter() {

    override fun getCount(): Int = entries.size

    override fun getItem(position: Int): AppEntry = entries[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val entry = entries[position]
        view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(entry.icon)
        view.findViewById<TextView>(R.id.app_label).text = entry.shortcut.label
        return view
    }
}
