package cn.ahutwqqd.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.ahutwqqd.app.data.AppSettings
import cn.ahutwqqd.app.data.MAX_STUDENTS
import cn.ahutwqqd.app.data.RunTrigger
import cn.ahutwqqd.app.data.SignRepository
import cn.ahutwqqd.app.data.Student
import cn.ahutwqqd.app.work.GuardService
import cn.ahutwqqd.app.work.Scheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app.applicationContext
    private val repository = SignRepository.instance

    val students = repository.students
    val settings = repository.settings
    val logs = repository.logs
    val running = repository.running
    val progress = repository.progress

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast = _toast.asSharedFlow()

    // ---------- 学生 ----------

    fun upsert(student: Student) {
        viewModelScope.launch {
            val result = repository.upsert(student)
            when (result) {
                SignRepository.UpsertResult.DUPLICATE_ID ->
                    _toast.tryEmit("学号 ${student.studentId} 已在列表中")
                SignRepository.UpsertResult.LIMIT_REACHED ->
                    _toast.tryEmit("最多只能添加 $MAX_STUDENTS 名学生")
                SignRepository.UpsertResult.LOCKED_TODAY ->
                    _toast.tryEmit("已成功签到的学生当日不可修改学号和删除，如需修改请明日再试")
                SignRepository.UpsertResult.OK -> Unit
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val result = repository.delete(id)
            if (result == SignRepository.DeleteResult.LOCKED_TODAY) {
                _toast.tryEmit("已成功签到的学生当日不可修改学号和删除，如需修改请明日再试")
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    // ---------- 签到 ----------

    fun signIn(id: String) {
        viewModelScope.launch {
            val student = repository.students.value.firstOrNull { it.id == id } ?: return@launch
            if (student.id in repository.running.value) return@launch
            val outcome = repository.signOne(student, RunTrigger.MANUAL)
            _toast.tryEmit("${student.displayName}：${outcome.message}")
        }
    }

    fun signInAll() {
        viewModelScope.launch {
            if (repository.students.value.isEmpty()) {
                _toast.tryEmit("还没有添加学生")
                return@launch
            }
            if (repository.running.value.isNotEmpty()) {
                _toast.tryEmit("正在签到中，请稍候")
                return@launch
            }
            // 手动全部签到不区分是否参与自动，停用的学生也一起签
            val summary = repository.signAll(
                trigger = RunTrigger.MANUAL,
                skipTodaySuccess = true,
                includeDisabled = true
            )
            _toast.tryEmit(
                when {
                    summary.total == 0 -> "今天都已签到"
                    summary.failures.isEmpty() -> "${summary.total} 人全部签到成功"
                    else -> "${summary.success} 人成功，${summary.failures.size} 人失败"
                }
            )
        }
    }

    // ---------- 设置 ----------

    fun setAutoSign(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(autoSignEnabled = enabled, retryAttempt = 0) }
            if (enabled) {
                Scheduler.scheduleNext(appContext)
                if (repository.settings.value.guardEnabled) GuardService.start(appContext)
            } else {
                Scheduler.cancel(appContext)
                repository.updateSettings {
                    it.copy(nextRunAt = 0L, pendingRunAt = 0L, retryAttempt = 0)
                }
                GuardService.stop(appContext)
            }
        }
    }

    /**
     * 后台守护开关。
     *
     * 关掉之后依然会排闹钟，但系统很可能在后台把闹钟清掉——这个开关只影响
     * 「愿不愿意用一条常驻通知换取可靠性」。
     */
    fun setGuard(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(guardEnabled = enabled) }
            if (enabled && repository.settings.value.autoSignEnabled) {
                GuardService.start(appContext)
            } else {
                GuardService.stop(appContext)
            }
        }
    }

    fun setWindow(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            val start = startMinutes.coerceIn(0, 23 * 60 + 58)
            val end = endMinutes.coerceIn(start + 1, 23 * 60 + 59)
            repository.updateSettings {
                it.copy(windowStart = start, windowEnd = end, retryAttempt = 0)
            }
            Scheduler.scheduleNext(appContext)
        }
    }

    fun setRadius(meters: Int) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(jitterRadiusMeters = meters.coerceIn(MIN_RADIUS, MAX_RADIUS))
            }
        }
    }

    fun setNotify(success: Boolean? = null, failure: Boolean? = null) {        viewModelScope.launch {
            repository.updateSettings {
                it.copy(
                    notifyOnSuccess = success ?: it.notifyOnSuccess,
                    notifyOnFailure = failure ?: it.notifyOnFailure
                )
            }
        }
    }

    /** 明暗模式：SYSTEM / LIGHT / DARK */
    fun setThemeMode(mode: String) {
        viewModelScope.launch { repository.updateSettings { it.copy(themeMode = mode) } }
    }

    /** 配色风格：BLUE / TEAL / VIOLET / SUNSET / ROSE / GRAPHITE */
    fun setThemeStyle(style: String) {
        viewModelScope.launch { repository.updateSettings { it.copy(themeStyle = style) } }
    }

    /** 指引页里两项厂商设置只能由用户自己确认，勾选结果记下来 */
    fun setAck(autostart: Boolean? = null, backgroundPower: Boolean? = null) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(
                    ackAutostart = autostart ?: it.ackAutostart,
                    ackBackgroundPower = backgroundPower ?: it.ackBackgroundPower
                )
            }
        }
    }

    /** 指引页的「重新检查」：顺手把守护服务与闹钟补一遍 */
    fun checkGuard() {
        viewModelScope.launch {
            runCatching { Scheduler.ensureScheduled(appContext) }
            val now = repository.settings.value
            if (now.autoSignEnabled && now.guardEnabled && !GuardService.alive) {
                GuardService.start(appContext)
            }
        }
    }

    /** 重新随机一次今天的执行时刻 */
    fun reschedule() {
        viewModelScope.launch {
            val at = Scheduler.scheduleNext(appContext)
            _toast.tryEmit(if (at > 0) "已安排在 ${Scheduler.describe(at)}" else "自动签到已关闭")
        }
    }

    fun clearLogs() {
        viewModelScope.launch { repository.clearLogs() }
    }

    fun deleteLog(id: String) {
        viewModelScope.launch { repository.deleteLog(id) }
    }

    fun passwordOf(student: Student): String = repository.passwordOf(student)

    fun cipherOf(plain: String): String? = repository.cipherOf(plain)

    fun toast(text: String) {
        _toast.tryEmit(text)
    }

    /** 设置项变化后重新对一下闹钟，避免界面显示与实际不一致 */
    fun syncSchedule() {
        viewModelScope.launch { runCatching { Scheduler.ensureScheduled(appContext) } }
    }

    companion object {
        const val MIN_RADIUS = 20
        const val MAX_RADIUS = 500
    }
}
