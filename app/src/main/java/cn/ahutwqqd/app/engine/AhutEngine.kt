package cn.ahutwqqd.app.engine

import cn.ahutwqqd.core.AhutSignature
import cn.ahutwqqd.core.Jitter
import cn.ahutwqqd.core.Py
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * 学校考勤系统的接口地址与常量。
 *
 * 这些地址与参数顺序来自原仓库 main.py / main_sync.py 的 WEB_DICT，
 * 学校接口变更时必须先抓包再改。
 */
object AhutConfig {
    const val HOST = "https://xskq.ahut.edu.cn"
    const val API_BASE = "$HOST/api"

    val TOKEN_API = "$API_BASE/flySource-auth/oauth/token"
    val TASK_ID_API =
        "$API_BASE/flySource-yxgl/dormSignTask/getStudentTaskPage?userDataType=student&current=1&size=15"
    val API_LOG_API = "$API_BASE/flySource-base/apiLog/save?menuTitle=%E6%99%9A%E5%AF%9D%E7%AD%BE%E5%88%B0"
    val SIGN_IN_API = "$API_BASE/flySource-yxgl/dormSignRecord/stuSign"

    fun wechatConfigApi(taskId: String, studentId: Long): String =
        "$API_BASE/flySource-base/wechat/getWechatMpConfig" +
            "?configUrl=$HOST/wise/pages/ssgl/dormsign" +
            "?taskId=$taskId&autoSign=1&scanSign=0&userId=$studentId"

    fun locationApi(taskId: String, date: String): String =
        "$API_BASE/flySource-yxgl/dormSignTask/getTaskByIdForApp?taskId=$taskId&signDate=$date"

    // 微信客户端固定凭据（学校系统机制，非本项目私有；改动会破坏与客户端的一致性）
    const val BASIC_AUTH =
        "Basic Zmx5c291cmNlX3dpc2VfYXBwOkRBNzg4YXNkVURqbmFzZF9mbHlzb3VyY2VfZHNkYWREQUlVaXV3cWU="

    private val UAS = listOf(
        "Mozilla/5.0 (Linux; Android 15; MIX Fold 4 Build/TKQ1.240502.001; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.137 Mobile Safari/537.36 " +
            "MicroMessenger/8.0.61.2660(0x28003D37) WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64",
        "Mozilla/5.0 (Linux; Android 15; LYA-AL10 Build/HUAWEILYA-AL10; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.137 Mobile Safari/537.36 " +
            "MicroMessenger/8.0.61.2660(0x28003D37) WeChat/arm64 Weixin NetType/5G Language/zh_CN ABI/arm64",
        "Mozilla/5.0 (Linux; Android 15; SM-S938B Build/TP1A.240205.004; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.137 Mobile Safari/537.36 " +
            "MicroMessenger/8.0.61.2660(0x28003D37) WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.61(0x18003D29) NetType/WIFI Language/zh_CN",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.61(0x18003D29) NetType/5G Language/zh_CN"
    )

    fun userAgent(random: Random): String = UAS[random.nextInt(UAS.size)]

    const val MAX_RETRIES = 4
    const val MAX_TOKEN_RETRIES = 3
}

/**
 * 六步签到状态机，从 main_sync.py 移植。
 *
 * 0 取 token → 1 取 taskId → 2 取微信配置 → 3 开时间窗 → 4 取宿舍位置 → 5 提交签到
 * step == 6 表示全程成功；step == -1 表示终止且不重试。
 */
class AhutEngine(private val random: Random = Random.Default) {

    data class Request(
        val studentId: Long,
        val password: String,
        val passwordIsMd5: Boolean = false,
        val customLocation: Pair<Double, Double>? = null,
        val jitterRadiusMeters: Double = Jitter.DEFAULT_RADIUS_METERS
    )

    data class Outcome(
        val success: Boolean,
        val message: String,
        val step: Int,
        val errors: List<String>
    )

    private class Session(val request: Request, val userAgent: String) {
        var token: String? = null
        var taskId: String = ""
        var roomId: String = ""
        var dormLatitude: Double = 0.0
        var dormLongitude: Double = 0.0
        var username: String = ""

        /** 本会话独立的 cookie，登录后服务端下发，后续每一步都要带上 */
        val cookies: MutableMap<String, String> = LinkedHashMap()
    }

    private data class Step(val success: Boolean, val msg: String = "", val next: Int)

    suspend fun signIn(
        request: Request,
        onStep: (Int) -> Unit = {}
    ): Outcome = withContext(Dispatchers.IO) {
        val session = Session(request, AhutConfig.userAgent(random))
        var step = 0
        var retries = 0
        var tokenRetries = 0
        val errors = LinkedHashSet<String>()

        while (retries < AhutConfig.MAX_RETRIES && step in 0..5) {
            onStep(step)
            val result = when (step) {
                0 -> stepToken(session)
                1 -> stepTaskId(session)
                2 -> stepWechatConfig(session)
                3 -> stepApiLog(session)
                4 -> stepLocation(session)
                else -> stepSubmit(session)
            }
            step = result.next
            if (!result.success) {
                if (result.msg.isNotBlank()) errors += result.msg
                if (step == 0 && tokenRetries < AhutConfig.MAX_TOKEN_RETRIES) {
                    tokenRetries++
                } else {
                    retries++
                }
            }
            delay(random.nextLong(500, 2001))
        }

        if (step == 6) {
            Outcome(true, "签到成功", step, errors.toList())
        } else {
            Outcome(false, errors.lastOrNull() ?: "签到失败", step, errors.toList())
        }
    }

    // ---------- 各步骤 ----------

    private fun stepToken(s: Session): Step {
        val params = linkedMapOf(
            "tenantId" to "000000",
            "username" to s.request.studentId.toString(),
            "password" to if (s.request.passwordIsMd5) {
                s.request.password
            } else {
                AhutSignature.passwordMd5(s.request.password)
            },
            "type" to "account",
            "grant_type" to "password",
            "scope" to "all"
        )
        val query = params.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        val url = "${AhutConfig.TOKEN_API}?$query"
        val (_, json) = http("POST", url, baseHeaders(s), null, s.cookies)

        val refresh = json.optString("refresh_token")
        if (refresh.isNotEmpty()) {
            s.token = refresh
            s.username = json.optString("userName")
            return Step(true, "", 1)
        }
        val raw = json.optString("error_description", "未知错误")
        val desc = if (raw.contains("Bad credentials") || raw.contains("用户名或密码错误")) {
            "密码错误"
        } else {
            raw
        }
        return Step(false, desc, -1)
    }

    private fun stepTaskId(s: Session): Step {
        val url = AhutConfig.TASK_ID_API
        val (_, json) = http("GET", url, authHeaders(s, url), null, s.cookies)
        if (json.optInt("code") == 200) {
            val data = json.optJSONObject("data")
            val records = data?.optJSONArray("records")
            // 先按原实现的路径取；取不到再在整棵返回里找，防止学校把 taskId 挪到别的层级后整条链路直接废掉
            val taskId = records?.optJSONObject(0)?.taskIdString()
                ?.takeIf { it.isNotEmpty() }
                ?: findTaskId(json)
            return when {
                !taskId.isNullOrEmpty() -> {
                    s.taskId = taskId
                    Step(true, "", 2)
                }
                records == null || records.length() == 0 ->
                    Step(false, "今天没有可签到的任务（任务列表为空）", 1)
                else ->
                    Step(false, "任务数据里没有 taskId｜${describeTask(json)}", 1)
            }
        }
        val msg = json.optString("msg")
        return if (isAuthFailure(msg)) {
            s.token = null
            Step(false, "token 失效", 0)
        } else {
            Step(false, msg, 1)
        }
    }

    /**
     * 读取 taskId。
     *
     * 学校的 taskId 是 32 位十六进制字符串（实测 `ebf794a5c9f6bff2575114ada2339658`），
     * 早期版本按 Long 解析，遇到字符串直接归零，导致整条链路卡在第 2 步。
     */
    private fun JSONObject.taskIdString(): String = normalizeTaskId(opt("taskId"))

    private fun normalizeTaskId(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is Number -> if (value.toLong() > 0) value.toString() else ""
        is String -> value.trim()
            .takeIf { it.isNotEmpty() && it != "0" && !it.equals("null", ignoreCase = true) }
            .orEmpty()
        else -> ""
    }

    /** 广度优先在整棵 JSON 里找第一个可用的 taskId，只在上面的标准路径失败时兜底。 */
    private fun findTaskId(root: JSONObject): String? {
        val queue = ArrayDeque<Any>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 500) {
            visited++
            when (val node = queue.removeFirst()) {
                is JSONObject -> {
                    node.taskIdString().takeIf { it.isNotEmpty() }?.let { return it }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = node.opt(key)
                        if (key.equals("taskId", ignoreCase = true)) {
                            normalizeTaskId(value).takeIf { it.isNotEmpty() }?.let { return it }
                        }
                        value?.let { queue.add(it) }
                    }
                }
                is org.json.JSONArray -> for (i in 0 until node.length()) {
                    node.opt(i)?.let { queue.add(it) }
                }
                else -> Unit
            }
        }
        return null
    }

    /** 诊断用的值预览：容器只报个数，避免打印出一大坨。 */
    private fun previewValue(value: Any?): String = when {
        value == null || value === JSONObject.NULL -> "null"
        value is JSONObject -> "{" + value.length() + "}"
        value is org.json.JSONArray -> "[" + value.length() + "]"
        else -> value.toString()
    }

    /**
     * 把任务接口的返回摊平成一行，用来在字段改名时一眼看出真实结构。
     * 只在解析失败时调用，所以可以放心写详细。
     */
    private fun describeTask(json: JSONObject): String {
        val sb = StringBuilder()
        fun dump(label: String, obj: JSONObject?) {
            if (obj == null || sb.length > 700) return
            sb.append(label).append('{')
            val keys = obj.keys()
            var first = true
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.opt(key)
                val text = previewValue(value)
                if (!first) sb.append("，")
                first = false
                sb.append(key).append('=').append(if (text.length > 40) text.take(40) + "…" else text)
                if (sb.length > 700) {
                    sb.append("…")
                    return
                }
            }
            sb.append("} ")
        }
        val data = json.optJSONObject("data")
        dump("返回", json)
        dump("数据", data)
        dump("首条", data?.optJSONArray("records")?.optJSONObject(0))
        return sb.toString().trim()
    }

    private fun stepWechatConfig(s: Session): Step {
        val url = AhutConfig.wechatConfigApi(s.taskId, s.request.studentId)
        val (_, json) = http("GET", url, authHeaders(s, url), null, s.cookies)
        if (json.optInt("code") == 200) return Step(true, "", 3)
        val msg = json.optString("msg")
        return if (isAuthFailure(msg)) {
            s.token = null
            Step(false, "token 失效", 0)
        } else {
            Step(false, msg, 2)
        }
    }

    private fun stepApiLog(s: Session): Step {
        val url = AhutConfig.API_LOG_API
        val (status, json) = http("POST", url, authHeaders(s, url), null, s.cookies)
        return if (status == 200) {
            Step(true, "", 4)
        } else {
            Step(false, json.optString("msg").ifBlank { "开启签到时间窗口失败" }, 3)
        }
    }

    private fun stepLocation(s: Session): Step {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
        val url = AhutConfig.locationApi(s.taskId, today)
        val (_, json) = http("GET", url, authHeaders(s, url), null, s.cookies)
        if (json.optInt("code") == 200) {
            val dorm = json.optJSONObject("data")?.optJSONObject("dormitoryRegisterVO")
            s.dormLatitude = dorm?.optString("locationLat")?.toDoubleOrNull() ?: 0.0
            s.dormLongitude = dorm?.optString("locationLng")?.toDoubleOrNull() ?: 0.0
            s.roomId = dorm?.optString("roomId").orEmpty()
            // 原实现会带着 (0,0) 继续提交，等于往几内亚湾签到，必然失败且看不出原因。
            // 这里在「自动定位」模式下提前中断，给出可操作的提示。
            if (s.request.customLocation == null &&
                s.dormLatitude == 0.0 && s.dormLongitude == 0.0
            ) {
                return Step(false, "未获取到宿舍登记位置，请检查宿舍登记信息或改用自定义位置", -1)
            }
            return Step(true, "", 5)
        }
        val msg = json.optString("msg")
        if (isAuthFailure(msg)) {
            s.token = null
            return Step(false, "token 失效", 0)
        }
        // 与原实现一致：拿不到宿舍信息也继续，用 0,0 或自定义坐标提交
        return Step(false, "", 5)
    }

    private suspend fun stepSubmit(s: Session): Step {
        // 提交前随机等待，模拟人工操作，同时避免多人同一秒打接口
        delay(random.nextLong(4000, 10001))

        val base = s.request.customLocation ?: (s.dormLatitude to s.dormLongitude)
        val (signLat, signLng) = Jitter.locationInCircle(
            base.first, base.second, s.request.jitterRadiusMeters, random
        )
        val accuracy = Math.round(random.nextDouble(25.0, 35.0) * 100) / 100.0
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()

        val stuTaskId = AhutSignature.generateStuTaskId(
            signLat, signLng, accuracy, today, s.taskId
        )
        val signCode = AhutSignature.generateSignCode(System.currentTimeMillis())

        val body = buildString {
            append("{\"signType\":0")
            append(",\"taskId\":\"").append(s.taskId).append('"')
            append(",\"signLat\":").append(Py.floatStr(signLat))
            append(",\"signLng\":").append(Py.floatStr(signLng))
            append(",\"locationAccuracy\":").append(Py.floatStr(accuracy))
            append(",\"stuTaskId\":\"").append(stuTaskId).append('"')
            append(",\"scanCode\":\"\",\"scanType\":\"\"")
            append(",\"roomId\":\"").append(s.roomId).append('"')
            append(",\"signKey\":\"").append(s.roomId).append('"')
            append(",\"signCode\":\"").append(signCode).append('"')
            append('}')
        }

        val url = AhutConfig.SIGN_IN_API
        val (_, json) = http("POST", url, authHeaders(s, url), body, s.cookies)
        val msg = json.optString("msg")

        if (json.optInt("code") == 200 || msg.contains("您今天已完成签到")) {
            return Step(true, "", 6)
        }
        if (isAuthFailure(msg)) {
            s.token = null
            return Step(false, "token 失效", 0)
        }
        if (msg.contains("未到签到时间")) {
            return Step(false, msg, -1)
        }
        return Step(false, msg.ifBlank { "签到失败" }, 5)
    }

    // ---------- 工具 ----------

    private fun isAuthFailure(msg: String): Boolean =
        msg.contains("请求未授权") || msg.contains("缺失身份信息") || msg.contains("鉴权失败")

    private fun baseHeaders(s: Session): MutableMap<String, String> = mutableMapOf(
        "User-Agent" to s.userAgent,
        "authorization" to AhutConfig.BASIC_AUTH,
        "Content-Type" to "application/json;charset=UTF-8",
        "X-Requested-With" to "com.tencent.mm",
        "Origin" to AhutConfig.HOST,
        "Referer" to "${AhutConfig.HOST}/wise/pages/ssgl/dormsign?&userId=${s.request.studentId}"
    )

    private fun authHeaders(s: Session, url: String): MutableMap<String, String> {
        val headers = baseHeaders(s)
        s.token?.let {
            headers["flysource-auth"] = "bearer $it"
            headers["flysource-sign"] = AhutSignature.generateSign(url, it)
        }
        return headers
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun http(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        cookies: MutableMap<String, String>
    ): Pair<Int, JSONObject> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (cookies.isNotEmpty()) {
                    setRequestProperty(
                        "Cookie",
                        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    )
                }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                } else if (method == "POST") {
                    doOutput = true
                    outputStream.use { it.write(ByteArray(0)) }
                }
            }
            val status = conn.responseCode
            // 收集服务端下发的会话 cookie（登录成功时才会给，缺了它后续请求会被当成未登录）
            conn.headerFields.forEach { (name, values) ->
                if (name != null && name.equals("Set-Cookie", ignoreCase = true)) {
                    values?.forEach { storeCookie(cookies, it) }
                }
            }
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
            status to parse(text)
        } catch (e: Exception) {
            0 to JSONObject().put("code", -1).put("msg", e.message ?: "网络异常")
        } finally {
            conn?.disconnect()
        }
    }

    /** 从一条 Set-Cookie 里取出 name=value，忽略 Path/Expires 等属性 */
    private fun storeCookie(jar: MutableMap<String, String>, raw: String) {
        val pair = raw.substringBefore(';').trim()
        val idx = pair.indexOf('=')
        if (idx <= 0) return
        val name = pair.substring(0, idx).trim()
        val value = pair.substring(idx + 1).trim()
        if (name.isNotEmpty()) jar[name] = value
    }

    private fun parse(text: String): JSONObject = runCatching { JSONObject(text) }
        .getOrElse { JSONObject().put("code", -1).put("msg", text) }
}
