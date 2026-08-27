package com.xfqiu.floatball.core

/**
 * 一个快捷应用。存储为单行文本，字段以 [FIELD_SEPARATOR] 分隔，
 * 因此写入前需清洗 label 中的分隔符与换行。
 */
data class AppShortcut(
    val packageName: String,
    val activityName: String,
    val label: String
) {

    fun toStorage(): String =
        listOf(packageName, activityName, sanitize(label)).joinToString(FIELD_SEPARATOR)

    companion object {

        private const val FIELD_SEPARATOR = "|"
        private const val FIELD_COUNT = 3

        fun fromStorage(raw: String): AppShortcut? {
            val fields = raw.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
            if (fields.size != FIELD_COUNT) return null
            if (fields[0].isEmpty() || fields[1].isEmpty()) return null
            return AppShortcut(fields[0], fields[1], fields[2])
        }

        private fun sanitize(label: String): String =
            label.replace(FIELD_SEPARATOR, " ").replace("\n", " ").trim()
    }
}
