package cn.ahutwqqd.app.data

import org.json.JSONObject
import java.util.UUID

enum class RunTrigger { MANUAL, AUTO, RETRY }

data class RunLog(
    /** 稳定唯一标识，用于单独删除某一条记录；旧版本存下的记录在读取时补算 */
    val id: String,
    val at: Long,
    val studentId: Long,
    val name: String,
    val success: Boolean,
    val message: String,
    val step: Int,
    val trigger: RunTrigger
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("at", at)
        put("studentId", studentId)
        put("name", name)
        put("success", success)
        put("message", message)
        put("step", step)
        put("trigger", trigger.name)
    }

    companion object {
        fun new(
            at: Long,
            studentId: Long,
            name: String,
            success: Boolean,
            message: String,
            step: Int,
            trigger: RunTrigger
        ): RunLog = RunLog(
            id = UUID.randomUUID().toString(),
            at = at,
            studentId = studentId,
            name = name,
            success = success,
            message = message,
            step = step,
            trigger = trigger
        )

        fun fromJson(o: JSONObject): RunLog {
            val at = o.optLong("at")
            val studentId = o.optLong("studentId")
            val step = o.optInt("step", 0)
            return RunLog(
                // 2.5 及更早的版本没有 id 字段，用时间 + 学号 + 步骤拼一个确定值，
                // 保证同一条记录每次读出来的 id 相同，删除才能命中
                id = o.optString("id").ifBlank { "legacy-$at-$studentId-$step" },
                at = at,
                studentId = studentId,
                name = o.optString("name"),
                success = o.optBoolean("success"),
                message = o.optString("message"),
                step = step,
                trigger = runCatching { RunTrigger.valueOf(o.optString("trigger", "AUTO")) }
                    .getOrDefault(RunTrigger.AUTO)
            )
        }
    }
}
