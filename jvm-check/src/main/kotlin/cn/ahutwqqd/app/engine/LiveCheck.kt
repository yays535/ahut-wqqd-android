package cn.ahutwqqd.app.engine

import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 用真实账号跑一遍 AhutEngine 的六步流程。
 *
 * 21:30 之前跑，预期结果是 service 端拒绝：`success=false, step=-1, msg=未到签到时间！`
 * —— 这说明前五步全部通过、提交载荷格式被服务端接受，正是需要的结论。
 */
fun main() = runBlocking {
    val id = System.getenv("LOGIN_ID")?.trim().orEmpty()
    val pwd = System.getenv("LOGIN_PASS")?.trim().orEmpty()
    if (id.isEmpty() || pwd.isEmpty()) {
        println("请通过环境变量提供 LOGIN_ID 与 LOGIN_PASS")
        return@runBlocking
    }

    val request = AhutEngine.Request(
        studentId = id.toLong(),
        password = pwd,
        customLocation = null,
        jitterRadiusMeters = 100.0
    )

    val lines = mutableListOf<String>()
    fun log(text: String) { lines += text; println(text) }

    log("开始：$id")
    val started = System.currentTimeMillis()
    val outcome = AhutEngine().signIn(request) { step: Int -> log("  → 进入 step $step") }

    log("耗时 ${(System.currentTimeMillis() - started) / 1000.0}s")
    log("success=${outcome.success} step=${outcome.step}")
    log("message=${outcome.message}")
    outcome.errors.forEach { log("  error: $it") }
    File("live-result.txt").writeText(lines.joinToString("\n"), Charsets.UTF_8)
}
