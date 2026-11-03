package cn.ahutwqqd.core

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 与 Python 参考实现（仓库 ahut_wqqd 的 main_sync.py）逐字节比对。
 *
 * 向量由 tools/gen_golden.py 生成，重新生成即可覆盖新场景：
 *   python tools/gen_golden.py ../ahut_wqqd sign-core/src/test/resources/golden.json
 */
class SignatureGoldenTest {

    private val golden: JSONObject by lazy {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("golden.json")) {
            "缺少 golden.json，请先运行 tools/gen_golden.py"
        }
        JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    @Test
    @DisplayName("密码 md5 与 Python 一致")
    fun passwordMd5() {
        val cases = golden.getJSONArray("password_md5")
        repeat(cases.length()) { i ->
            val c = cases.getJSONObject(i)
            val pwd = c.getString("pwd")
            assertEquals(
                c.getString("expected"),
                AhutSignature.passwordMd5(pwd),
                "密码 '$pwd' 的 md5 不一致"
            )
        }
    }

    @Test
    @DisplayName("URL 签名与 Python 完全一致（固定时间戳）")
    fun generateSign() {
        val cases = golden.getJSONArray("generate_sign")
        repeat(cases.length()) { i ->
            val c = cases.getJSONObject(i)
            assertEquals(
                c.getString("expected"),
                AhutSignature.generateSign(
                    c.getString("url"),
                    c.getString("token"),
                    c.getLong("ts_ms")
                ),
                "URL ${c.getString("url")} / token ${c.getString("token").take(8)}… 的签名不一致"
            )
        }
    }

    @Test
    @DisplayName("signCode 时间串签名与 Python 一致")
    fun generateSignCode() {
        val cases = golden.getJSONArray("sign_code")
        repeat(cases.length()) { i ->
            val c = cases.getJSONObject(i)
            assertEquals(
                c.getString("expected"),
                AhutSignature.generateSignCode(c.getLong("ts_ms")),
                "时间戳 ${c.getLong("ts_ms")} 的 signCode 不一致"
            )
        }
    }

    @Test
    @DisplayName("stuTaskId 紧凑 JSON 签名与 Python 一致")
    fun generateStuTaskId() {
        val cases = golden.getJSONArray("stu_task_id")
        repeat(cases.length()) { i ->
            val c = cases.getJSONObject(i)
            val lat = c.getString("lat").toDouble()
            val lng = c.getString("lng").toDouble()
            val acc = c.getString("acc").toDouble()
            assertEquals(
                c.getString("expected"),
                AhutSignature.generateStuTaskId(lat, lng, acc, c.getString("date"), c.getString("taskId")),
                "坐标 ($lat, $lng, $acc) 的 stuTaskId 不一致"
            )
        }
    }

    @Test
    @DisplayName("浮点数转字符串与 Python str(float) 一致")
    fun floatStr() {
        val cases = golden.getJSONArray("stu_task_id")
        repeat(cases.length()) { i ->
            val c = cases.getJSONObject(i)
            val expect = c.getJSONArray("expect_strs")
            assertEquals(expect.getString(0), Py.floatStr(c.getString("lat").toDouble()), "纬度格式不一致")
            assertEquals(expect.getString(1), Py.floatStr(c.getString("lng").toDouble()), "经度格式不一致")
            assertEquals(expect.getString(2), Py.floatStr(c.getString("acc").toDouble()), "精度格式不一致")
        }
    }
}
