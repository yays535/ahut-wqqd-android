package cn.ahutwqqd.app.data

import org.json.JSONObject
import java.util.UUID

enum class LocationMode { AUTO, CUSTOM }

enum class SignStatus { NONE, SUCCESS, FAILED, RUNNING }

/** 学生列表的人数上限 */
const val MAX_STUDENTS = 6

/**
 * 一个需要签到的学生。
 *
 * 密码不存明文：[passwordCipher] 是 Keystore 加密后的密文（iv:密文 的 base64）。
 * 为 null 表示使用学校统一初始密码。
 */
data class Student(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val studentId: Long,
    val passwordCipher: String? = null,
    val locationMode: LocationMode = LocationMode.AUTO,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /**
     * 是否参与自动签到。**新增学生默认关闭**，需要用户明确打开。
     *
     * 注意这只影响自动签到，手动点「签到」不受它限制。
     */
    val enabled: Boolean = false,
    val status: SignStatus = SignStatus.NONE,
    val lastMessage: String = "",
    val lastRunAt: Long = 0L,
    /** 最近一次签到成功的日期（yyyy-MM-dd），用于自动签到时的幂等判断 */
    val lastSuccessDate: String = ""
) {

    val displayName: String
        get() = name.ifBlank { studentId.toString() }

    val hasCustomLocation: Boolean
        get() = locationMode == LocationMode.CUSTOM && latitude != null && longitude != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("studentId", studentId)
        put("passwordCipher", passwordCipher ?: JSONObject.NULL)
        put("locationMode", locationMode.name)
        put("latitude", latitude ?: JSONObject.NULL)
        put("longitude", longitude ?: JSONObject.NULL)
        put("enabled", enabled)
        put("status", status.name)
        put("lastMessage", lastMessage)
        put("lastRunAt", lastRunAt)
        put("lastSuccessDate", lastSuccessDate)
    }

    companion object {
        fun fromJson(o: JSONObject): Student = Student(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            studentId = o.optLong("studentId"),
            passwordCipher = if (o.isNull("passwordCipher")) null else o.optString("passwordCipher"),
            locationMode = runCatching {
                LocationMode.valueOf(o.optString("locationMode", LocationMode.AUTO.name))
            }.getOrDefault(LocationMode.AUTO),
            latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
            longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
            enabled = o.optBoolean("enabled", true),
            status = runCatching {
                SignStatus.valueOf(o.optString("status", SignStatus.NONE.name))
            }.getOrDefault(SignStatus.NONE),
            lastMessage = o.optString("lastMessage"),
            lastRunAt = o.optLong("lastRunAt"),
            lastSuccessDate = o.optString("lastSuccessDate")
        )
    }
}
