package cn.ahutwqqd.core

import java.security.MessageDigest
import java.util.Base64

/**
 * 与 Python 侧逐字节对齐的基础工具。
 *
 * 这里的每个函数都对应 main_sync.py 中的用法，差异会直接导致签名错误，
 * 因此全部由 SignatureGoldenTest 用 Python 生成的向量锁死。
 */
object Py {

    private val HEX = "0123456789abcdef".toCharArray()

    /** 等价于 hashlib.md5(s.encode('utf-8')).hexdigest()，小写十六进制 */
    fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(32)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v shr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** 等价于 base64.b64encode(s.encode('utf-8')).decode('utf-8') */
    fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    /**
     * 等价于 Python 的 str(float)。
     *
     * JVM 的 Double.toString 与 Python 都用「最短往返表示」，但科学计数法的阈值不同：
     * Python 在 1e-4 <= |v| < 1e16 区间输出定点形式，JVM 在 |v| < 1e-3 或 >= 1e7 就用 E 记法。
     * 坐标量级（-180~180）本不该触发，但宿舍坐标 + 随机偏移可能产生 0.000x 这类值，
     * 所以这里把 JVM 的 E 记法展开回定点，保证 md5 输入一致。
     */
    fun floatStr(v: Double): String {
        if (v.isNaN()) return "nan"
        if (v == Double.POSITIVE_INFINITY) return "inf"
        if (v == Double.NEGATIVE_INFINITY) return "-inf"
        val raw = v.toString().replace('e', 'E')
        val e = raw.indexOf('E')
        if (e < 0) return raw
        val abs = kotlin.math.abs(v)
        return if (abs >= 1e-4 && abs < 1e16) expandScientific(raw) else raw
    }

    /** 把 1.234E-4 这类写法展开成 0.0001234 */
    private fun expandScientific(raw: String): String {
        val negative = raw.startsWith("-")
        val body = if (negative) raw.substring(1) else raw
        val e = body.indexOf('E')
        val mantissa = body.substring(0, e)
        val exponent = body.substring(e + 1).toInt()
        val digits = mantissa.replace(".", "")
        val pointIndex = (mantissa.indexOf('.').takeIf { it >= 0 } ?: mantissa.length) + exponent
        val sb = StringBuilder()
        if (negative) sb.append('-')
        when {
            pointIndex <= 0 -> {
                sb.append("0.")
                repeat(-pointIndex) { sb.append('0') }
                sb.append(digits)
            }
            pointIndex >= digits.length -> {
                sb.append(digits)
                repeat(pointIndex - digits.length) { sb.append('0') }
                sb.append(".0")
            }
            else -> {
                sb.append(digits, 0, pointIndex)
                sb.append('.')
                sb.append(digits, pointIndex, digits.length)
            }
        }
        return sb.toString()
    }
}
