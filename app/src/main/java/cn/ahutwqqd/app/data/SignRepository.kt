package cn.ahutwqqd.app.data

import android.content.Context
import cn.ahutwqqd.app.engine.AhutEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 学生、设置、日志的唯一数据源。
 *
 * 前台服务与界面共用同一个实例（进程内单例），这样自动签到跑起来时
 * 界面上的状态也会实时更新，不需要额外的进程间通信。
 */
class SignRepository private constructor(context: Context) {

    private val app = context.applicationContext
    private val studentStore = StudentStore(app)
    private val settingsStore = SettingsStore(app)
    private val logStore = LogStore(app)
    private val engine = AhutEngine()

    private val _students = MutableStateFlow(studentStore.load())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _logs = MutableStateFlow(logStore.load())
    val logs: StateFlow<List<RunLog>> = _logs.asStateFlow()

    private val _running = MutableStateFlow<Set<String>>(emptySet())
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    /**
     * 正在签到的学生当前走到第几步，例如 "第 4/6 步 · 开启窗口"。
     *
     * 只放在内存里、不落盘：它是瞬时状态，重启后本来就应该清空。
     */
    private val _progress = MutableStateFlow<Map<String, String>>(emptyMap())
    val progress: StateFlow<Map<String, String>> = _progress.asStateFlow()

    /** 密码解密要用 Keystore，串行化避免并发首次创建密钥时打架 */
    private val cryptoLock = Mutex()
    private val persistLock = Mutex()

    data class Failure(val student: Student, val message: String, val step: Int)

    data class Summary(
        val total: Int,
        val success: Int,
        val failures: List<Failure>
    ) {
        val allOk: Boolean get() = failures.isEmpty() && total > 0

        /** 只有「可重试」的失败才值得在窗口内再跑一轮；密码错误之类的重试也没用 */
        val retryable: Boolean get() = failures.any { it.step != -1 }
    }

    // ---------- 学生 ----------

    /** 新增/编辑学生的结果，用枚举而不是布尔，让界面能区分各种拒绝原因 */
    enum class UpsertResult {
        /** 成功写入 */
        OK,

        /** 学号与列表里另一个学生重复 */
        DUPLICATE_ID,

        /** 新增时人数已达上限 */
        LIMIT_REACHED,

        /** 该学生今天已签到成功，当日不允许改学号 */
        LOCKED_TODAY
    }

    /**
     * 新增或更新一个学生。
     *
     * 学号在列表内唯一：如果另一个学生（id 不同）已经占用了同一个学号，
     * 直接拒绝写入。人数上限 6 人、以及「当日已签不可改学号」也在这里硬拦一次，
     * 防止绕过表单的调用把违规数据写进存储。
     */
    suspend fun upsert(student: Student): UpsertResult {
        val today = today()
        val isNew = _students.value.none { it.id == student.id }
        val existing = _students.value.firstOrNull { it.id == student.id }

        // 当日已签成功的学生，当日不允许改学号（防止改学号后再签一次）
        if (existing != null &&
            existing.lastSuccessDate == today &&
            existing.studentId != student.studentId
        ) {
            return UpsertResult.LOCKED_TODAY
        }

        var result = UpsertResult.OK
        mutateStudents { list ->
            val duplicate = list.any {
                it.id != student.id && it.studentId == student.studentId
            }
            when {
                duplicate -> {
                    result = UpsertResult.DUPLICATE_ID
                    list
                }
                isNew && list.size >= MAX_STUDENTS -> {
                    result = UpsertResult.LIMIT_REACHED
                    list
                }
                else -> {
                    val index = list.indexOfFirst { it.id == student.id }
                    if (index >= 0) list.toMutableList().also { it[index] = student }
                    else (list + student).toMutableList()
                }
            }
        }
        return result
    }

    /** 删除结果：当日已签成功的学生当日不可删除 */
    enum class DeleteResult { OK, LOCKED_TODAY }

    suspend fun delete(id: String): DeleteResult {
        val target = _students.value.firstOrNull { it.id == id } ?: return DeleteResult.OK
        if (target.lastSuccessDate == today()) return DeleteResult.LOCKED_TODAY
        mutateStudents { list -> list.filterNot { it.id == id }.toMutableList() }
        return DeleteResult.OK
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        mutateStudents { list ->
            list.map { if (it.id == id) it.copy(enabled = enabled) else it }.toMutableList()
        }
    }

    private suspend fun mutateStudents(transform: (List<Student>) -> List<Student>) {
        persistLock.withLock {
            val next = transform(_students.value).sortedBy { it.studentId }
            _students.value = next
            studentStore.save(next)
        }
    }

    // ---------- 设置 ----------

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        persistLock.withLock {
            val next = transform(_settings.value)
            _settings.value = next
            settingsStore.save(next)
        }
    }

    // ---------- 日志 ----------

    suspend fun clearLogs() {
        persistLock.withLock {
            _logs.value = emptyList()
            logStore.save(emptyList())
        }
    }

    /** 删除单条记录；只影响这一条，学生状态与今日已签标记都不动 */
    suspend fun deleteLog(id: String) {
        persistLock.withLock {
            val next = _logs.value.filterNot { it.id == id }
            if (next.size != _logs.value.size) {
                _logs.value = next
                logStore.save(next)
            }
        }
    }

    private suspend fun appendLog(log: RunLog) {
        persistLock.withLock {
            val next = (listOf(log) + _logs.value).take(LogStore.MAX_ENTRIES)
            _logs.value = next
            logStore.save(next)
        }
    }

    // ---------- 签到 ----------

    fun passwordOf(student: Student): String = studentStore.passwordOf(student)

    fun cipherOf(plain: String): String? = studentStore.cipherOf(plain)

    /**
     * 批量签到。
     *
     * @param onlyIds 仅签这些人（null 表示全部人）
     * @param skipTodaySuccess 自动签到时为 true：今天已经成功的人直接跳过，避免重复提交
     * @param includeDisabled 手动「全部签到」时为 true：停用自动签到的学生也要签。
     *        `enabled` 只表示是否参与自动签到，不应该剥夺手动签到的权利。
     */
    suspend fun signAll(
        trigger: RunTrigger,
        onlyIds: Set<String>? = null,
        skipTodaySuccess: Boolean = true,
        includeDisabled: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Summary {
        val today = today()
        val runningIds = _running.value
        val pending = _students.value.filter { student ->
            (includeDisabled || student.enabled) &&
                (onlyIds == null || student.id in onlyIds) &&
                student.id !in runningIds &&
                !(skipTodaySuccess && student.lastSuccessDate == today)
        }
        if (pending.isEmpty()) return Summary(0, 0, emptyList())

        val semaphore = Semaphore(MAX_CONCURRENT)
        val failures = java.util.Collections.synchronizedList(mutableListOf<Failure>())
        // 并发跑的时候 done 必须原子自增，否则进度会跳号、甚至出现 2/3 卡住不动
        val done = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            pending.forEach { student ->
                launch {
                    semaphore.withPermit {
                        val outcome = signOne(student, trigger)
                        if (!outcome.success) {
                            failures += Failure(student, outcome.message, outcome.step)
                        }
                        onProgress(done.incrementAndGet(), pending.size)
                    }
                }
            }
        }

        return Summary(
            total = pending.size,
            success = pending.size - failures.size,
            failures = synchronized(failures) { failures.toList() }
        )
    }

    suspend fun signOne(student: Student, trigger: RunTrigger): AhutEngine.Outcome {
        // 今天已经签过了：不再发请求、不再写记录，直接把「已签到」回报给界面。
        // 否则用户手滑连点两下，会多出一条看起来像重新签成功的假记录。
        if (student.lastSuccessDate == today()) {
            return AhutEngine.Outcome(true, "已签到", STEP_DONE, emptyList())
        }
        if (!_running.tryAdd(student.id)) {
            return AhutEngine.Outcome(false, "正在签到中", -1, emptyList())
        }
        try {
            updateStudent(student.id) {
                it.copy(status = SignStatus.RUNNING, lastMessage = "正在签到")
            }

            val password = cryptoLock.withLock { passwordOf(student) }
            val outcome = engine.signIn(
                AhutEngine.Request(
                    studentId = student.studentId,
                    password = password,
                    customLocation = if (student.hasCustomLocation) {
                        student.latitude!! to student.longitude!!
                    } else {
                        null
                    },
                    jitterRadiusMeters = _settings.value.jitterRadiusMeters.toDouble()
                ),
                onStep = { step -> setProgress(student.id, step) }
            )

            val success = outcome.success || isAlreadySigned(outcome)
            val now = System.currentTimeMillis()
            updateStudent(student.id) {
                it.copy(
                    status = if (success) SignStatus.SUCCESS else SignStatus.FAILED,
                    lastMessage = if (success) "签到成功" else outcome.message,
                    lastRunAt = now,
                    lastSuccessDate = if (success) today() else it.lastSuccessDate
                )
            }
            appendLog(
                RunLog.new(
                    at = now,
                    studentId = student.studentId,
                    name = student.displayName,
                    success = success,
                    message = if (success) "签到成功" else outcome.message,
                    step = outcome.step,
                    trigger = trigger
                )
            )
            return outcome.copy(success = success, message = if (success) "签到成功" else outcome.message)
        } finally {
            _running.update { it - student.id }
            _progress.update { it - student.id }
        }
    }

    private fun setProgress(studentKey: String, step: Int) {
        val label = stepLabel(step)
        _progress.update { it + (studentKey to label) }
    }

    /** 把引擎的步骤号翻译成用户能看懂的一句话 */
    private fun stepLabel(step: Int): String = when (step) {
        0 -> "第 1/6 步 · 登录"
        1 -> "第 2/6 步 · 获取任务"
        2 -> "第 3/6 步 · 读取配置"
        3 -> "第 4/6 步 · 开启窗口"
        4 -> "第 5/6 步 · 读取位置"
        5 -> "第 6/6 步 · 提交签到"
        else -> "正在签到"
    }

    /** 学校返回「您今天已完成签到」时按成功处理 */
    private fun isAlreadySigned(outcome: AhutEngine.Outcome): Boolean =
        outcome.errors.any { it.contains("您今天已完成签到") }

    private suspend fun updateStudent(id: String, transform: (Student) -> Student) {
        persistLock.withLock {
            val next = _students.value.map { if (it.id == id) transform(it) else it }
            _students.value = next
            studentStore.save(next)
        }
    }

    companion object {
        private const val MAX_CONCURRENT = 2

        /** 引擎里 step == 6 表示六步全部走完 */
        private const val STEP_DONE = 6

        @Volatile
        private var INSTANCE: SignRepository? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(SignRepository::class.java) {
                    if (INSTANCE == null) INSTANCE = SignRepository(context)
                }
            }
        }

        val instance: SignRepository
            get() = INSTANCE ?: error("SignRepository 未初始化，应在 Application.onCreate 中调用 init()")

        fun today(): String = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
    }
}

private fun MutableStateFlow<Set<String>>.tryAdd(id: String): Boolean {
    while (true) {
        val current = value
        if (id in current) return false
        if (compareAndSet(current, current + id)) return true
    }
}
