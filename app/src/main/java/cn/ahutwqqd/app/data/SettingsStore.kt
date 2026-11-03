package cn.ahutwqqd.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File

class SettingsStore(context: Context) {

    private val file = File(context.filesDir, "settings.json")

    fun load(): AppSettings = runCatching {
        if (!file.exists()) return AppSettings()
        AppSettings.fromJson(JSONObject(file.readText(Charsets.UTF_8)))
    }.getOrDefault(AppSettings())

    fun save(settings: AppSettings) {
        // 设置里存着 nextRunAt / pendingRunAt，写坏一次就等于把定时任务弄丢，用临时文件替换
        val text = settings.toJson().toString()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }
}
