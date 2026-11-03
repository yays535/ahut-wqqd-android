package cn.ahutwqqd.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 学生列表的本地存储。
 *
 * 刻意不用 Room：这里的数据量是个位数到几十条，一个 JSON 文件足够，
 * 也省掉注解处理器，构建更轻。密码字段在写入前已由 [Crypto] 加密。
 */
class StudentStore(context: Context) {

    private val file = File(context.filesDir, "students.json")

    fun load(): List<Student> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText(Charsets.UTF_8))
        (0 until array.length()).map { Student.fromJson(array.getJSONObject(it)) }
            .sortedBy { it.studentId }
    }.getOrDefault(emptyList())

    fun save(students: List<Student>) {
        val array = JSONArray()
        students.forEach { array.put(it.toJson()) }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(array.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    /** 明文密码；未设置时回落到学校初始密码 */
    fun passwordOf(student: Student): String {
        val cipher = student.passwordCipher ?: return DEFAULT_PASSWORD
        return Crypto.decrypt(cipher) ?: DEFAULT_PASSWORD
    }

    /** 加密后写回；空串代表「用初始密码」 */
    fun cipherOf(plain: String): String? =
        if (plain.isBlank()) null else Crypto.encrypt(plain)

    companion object {
        const val DEFAULT_PASSWORD = "Ahgydx@920"
    }
}
