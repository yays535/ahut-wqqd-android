package cn.ahutwqqd.core

import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * 安徽工业大学考勤系统的三个签名算法，从 main_sync.py 逐行移植。
 *
 * 这些函数与学校系统是精确对齐的，任何改动都必须先有抓包证据，
 * 并且必须重跑 SignatureGoldenTest 与 Python 参考实现比对。
 */
object AhutSignature {

    private val WEEK = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private val MONTH = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private const val TIMEZONE_LABEL = "GMT+0800 (中国标准时间)"

    /** 密码摘要：generate_params 中的 password 字段 */
    fun passwordMd5(plain: String): String = Py.md5Hex(plain)

    /**
     * 请求头 flysource-sign 的值。
     *
     * 算法：md5(path + "?sign=" + md5(tsMillis + token)) + "1." + base64(tsMillis)
     *
     * @param timestampMs 仅测试注入用，正常调用取当前毫秒
     */
    fun generateSign(
        url: String,
        token: String?,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        if (token.isNullOrEmpty()) return ""
        val path = URI(url).rawPath ?: ""
        val innerHash = Py.md5Hex("$timestampMs$token")
        val finalHash = Py.md5Hex("$path?sign=$innerHash")
        return "$finalHash" + "1." + Py.b64(timestampMs.toString())
    }

    /**
     * 签到表单 stuSign 的 signCode 字段：对「固定格式的东八区时间字符串」做 md5。
     *
     * 关键点：星期与月份是英文缩写、日期补零、时区串里带中文，
     * 必须用固定数组而非日期格式化 API，否则会随系统语言变化。
     */
    fun generateSignCode(timestampMs: Long): String {
        val dt = Instant.ofEpochMilli(timestampMs).atOffset(ZoneOffset.ofHours(8))
        val time = String.format(
            Locale.US, "%02d:%02d:%02d", dt.hour, dt.minute, dt.second
        )
        val timeStr = String.format(
            Locale.US, "%s %s %02d %d %s %s",
            WEEK[dt.dayOfWeek.value - 1],
            MONTH[dt.monthValue - 1],
            dt.dayOfMonth,
            dt.year,
            time,
            TIMEZONE_LABEL
        )
        return Py.md5Hex(timeStr)
    }

    /**
     * 签到表单 stuSign 的 stuTaskId 字段：对「紧凑 JSON」做 md5。
     *
     * 注意三处必须与 Python 完全一致：
     *  1. 键顺序固定为 latitude/longitude/locationAccuracy/signDate/taskId/fileId
     *  2. 分隔符是 "," 和 ":"，不能有空格
     *  3. 三个数值都以字符串形式写入，且格式等同 Python 的 str(float)
     */
    fun generateStuTaskId(
        latitude: Double,
        longitude: Double,
        locationAccuracy: Double,
        signDate: String,
        taskId: String,
        fileId: String = ""
    ): String {
        val json = buildString {
            append("{\"latitude\":\"").append(Py.floatStr(latitude)).append("\",")
            append("\"longitude\":\"").append(Py.floatStr(longitude)).append("\",")
            append("\"locationAccuracy\":\"").append(Py.floatStr(locationAccuracy)).append("\",")
            append("\"signDate\":\"").append(signDate).append("\",")
            // 学校的 taskId 是 32 位十六进制字符串，不是数字，这里必须带引号
            append("\"taskId\":\"").append(taskId).append("\",")
            append("\"fileId\":\"").append(fileId).append("\"}")
        }
        return Py.md5Hex(json)
    }
}
