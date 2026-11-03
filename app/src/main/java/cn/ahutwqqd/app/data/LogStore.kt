package cn.ahutwqqd.app.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 签到历史。只保留最近 [MAX_ENTRIES] 条，按时间倒序返回。
 */
class LogStore(context: Context) {

    private val file = File(context.filesDir, "run_logs.json")

    fun load(): List<RunLog> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText(Charsets.UTF_8))
        (0 until array.length())
            .map { RunLog.fromJson(array.getJSONObject(it)) }
            .sortedByDescending { it.at }
    }.getOrDefault(emptyList())

    fun save(logs: List<RunLog>) {
        val array = JSONArray()
        logs.take(MAX_ENTRIES).forEach { array.put(it.toJson()) }
        // 先写临时文件再替换，避免写到一半被杀导致历史记录整体损坏
        val text = array.toString()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 300
    }
}
