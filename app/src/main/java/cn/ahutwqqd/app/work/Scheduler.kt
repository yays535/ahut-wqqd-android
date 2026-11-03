package cn.ahutwqqd.app.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.ahutwqqd.app.MainActivity
import cn.ahutwqqd.app.data.AppSettings
import cn.ahutwqqd.app.data.SignRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * 每天在签到窗口内随机取一个时刻，用精确闹钟唤醒 [SignService]。
 *
 * 随机值一旦排定就存进设置里，不会因为 App 重启而漂移——
 * 否则每次打开界面都会重新抽签，实际执行时间会不断往后跑。
 */
object Scheduler {

    private const val REQUEST_CODE = 2001
    private const val START_BUFFER_SECONDS = 40L
    private const val END_BUFFER_SECONDS = 300L
    private const val RETRY_MIN_MS = 5 * 60_000L
    private const val RETRY_MAX_MS = 10 * 60_000L

    /** 过点后的这段宽限期内不重排，避免把正在执行的那一轮覆盖掉 */
    private const val IN_FLIGHT_GRACE_MS = 10 * 60_000L

    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    // ---------- 时间计算 ----------

    fun windowStartMillis(date: LocalDate, settings: AppSettings): Long =
        date.atStartOfDay(zone).plusMinutes(settings.windowStart.toLong()).toInstant().toEpochMilli()

    fun windowEndMillis(date: LocalDate, settings: AppSettings): Long =
        date.atStartOfDay(zone).plusMinutes(settings.windowEnd.toLong()).toInstant().toEpochMilli()

    /** 在窗口内随机取一个执行时刻：起点留 40 秒缓冲，终点留 5 分钟收尾 */
    fun pickRunAt(date: LocalDate, settings: AppSettings, random: Random = Random.Default): Long {
        val startSec = settings.windowStart * 60L
        val endSec = settings.windowEnd * 60L
        val earliest = if (settings.picksInsideBufferedWindow) startSec + START_BUFFER_SECONDS else startSec
        val latest = if (settings.picksInsideBufferedWindow) {
            (endSec - END_BUFFER_SECONDS).coerceAtLeast(earliest + 60)
        } else {
            endSec.coerceAtLeast(earliest + 60)
        }
        val pick = if (latest <= earliest) earliest else earliest + random.nextLong(0, latest - earliest + 1)
        return date.atStartOfDay(zone).plusSeconds(pick).toInstant().toEpochMilli()
    }

    fun describe(at: Long): String {
        if (at <= 0) return "未安排"
        val dateTime = Instant.ofEpochMilli(at).atZone(zone)
        val today = LocalDate.now(zone)
        val prefix = when (dateTime.toLocalDate()) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            else -> dateTime.format(DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA))
        }
        return "$prefix ${dateTime.format(formatter)}"
    }

    // ---------- 闹钟 ----------

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context, attempt: Int): PendingIntent {
        val intent = Intent(context, SignService::class.java).apply {
            putExtra(SignService.EXTRA_ATTEMPT, attempt)
            putExtra(SignService.EXTRA_TRIGGER, "AUTO")
        }
        return PendingIntent.getForegroundService(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(context: Context, at: Long, attempt: Int) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, attempt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            // 没有精确闹钟权限时退化为「允许待机时触发」，可能被推迟几分钟
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            return
        }
        // 用 setAlarmClock 而不是 setExactAndAllowWhileIdle：
        // 系统把它归入「用户闹钟」这一档，优先级最高，国产 ROM 的省电策略对它更宽容；
        // 附带好处是状态栏会出现闹钟图标，用户一眼就能看出安排还在不在。
        val show = PendingIntent.getActivity(
            context,
            2002,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        android.util.Log.i("AHUT", "alarm set at=$at attempt=$attempt")
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        android.util.Log.i("AHUT", "cancel alarm requested", Throwable("trace"))
        manager.cancel(pendingIntent(context, 0))
    }

    // ---------- 对外调度 ----------

    /** 排定「下一次」自动签到；已排定且未过期时保持原值不重抽 */
    suspend fun ensureScheduled(context: Context) {
        val repository = SignRepository.instance
        val settings = repository.settings.value
        if (!settings.autoSignEnabled) {
            cancel(context)
            if (settings.nextRunAt != 0L || settings.pendingRunAt != 0L) {
                repository.updateSettings { it.copy(nextRunAt = 0L, pendingRunAt = 0L) }
            }
            return
        }
        val now = System.currentTimeMillis()

        // 还没到点：只把闹钟补回系统，绝不重新随机。
        // 这里曾经用「剩 30 秒以上才算有效」，结果是临到点前打开一次应用就会重抽，
        // 而重抽有可能抽到已经过去的时刻，于是今天被整个跳过——看起来就是「今晚没签」。
        if (settings.nextRunAt > now) {
            setAlarm(context, settings.nextRunAt, settings.retryAttempt)
            // 闹钟被系统清掉时 pendingRunAt 也会被看门狗清零，这里补回来，
            // 否则下一轮看门狗会误判成「已执行过」而漏掉提醒
            if (settings.pendingRunAt != settings.nextRunAt) {
                repository.updateSettings { it.copy(pendingRunAt = settings.nextRunAt) }
            }
            return
        }

        if (settings.nextRunAt > 0) {
            val today = LocalDate.now(zone)
            val missedDate = Instant.ofEpochMilli(settings.nextRunAt).atZone(zone).toLocalDate()
            val windowEnd = windowEndMillis(today, settings)
            // 今天的安排已经过点、但闹钟没被接单（pendingRunAt 还在）：说明闹钟真丢了。
            // 只要还没出今天的窗口，就立刻补一次，别把这一天浪费掉。
            if (missedDate == today && settings.pendingRunAt > 0 && now <= windowEnd) {
                setAlarm(context, now + 3_000, settings.retryAttempt)
                android.util.Log.i("AHUT", "missed alarm re-armed immediately, pending=${
                    settings.pendingRunAt
                }")
                return
            }
            // 刚过点不久：可能正在执行（SignService 已把 pendingRunAt 清 0），别插手
            if (now < settings.nextRunAt + IN_FLIGHT_GRACE_MS) return
        }

        scheduleNext(context)
    }

    /** 重新抽一个时刻。今天窗口已过就排到明天。 */
    suspend fun scheduleNext(context: Context, from: Long = System.currentTimeMillis()): Long {
        val repository = SignRepository.instance
        val settings = repository.settings.value
        if (!settings.autoSignEnabled) {
            cancel(context)
            repository.updateSettings { it.copy(nextRunAt = 0L, pendingRunAt = 0L) }
            return 0L
        }

        val today = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        var target = pickRunAt(today, settings)
        if (target <= from + 5_000) target = pickRunAt(today.plusDays(1), settings)

        setAlarm(context, target, attempt = 0)
        repository.updateSettings {
            it.copy(nextRunAt = target, retryAttempt = 0, pendingRunAt = target)
        }
        return target
    }

    /**
     * 今天已经跑完一轮，直接排到明天。
     *
     * 传「今天窗口结束时刻」作为起点，这样 [pickRunAt] 算出的今天时刻一定不晚于它，
     * 于是必然落到明天，不会在今天稍后又触发一次。
     */
    suspend fun scheduleForTomorrow(context: Context): Long {
        val settings = SignRepository.instance.settings.value
        val end = windowEndMillis(LocalDate.now(zone), settings)
        return scheduleNext(context, from = maxOf(System.currentTimeMillis(), end))
    }

    /**
     * 窗口内失败后的重试。返回排定的时刻，null 表示剩余时间不够、放弃重试。
     */    suspend fun scheduleRetry(context: Context, attempt: Int): Long? {
        val repository = SignRepository.instance
        val settings = repository.settings.value
        val now = System.currentTimeMillis()
        val end = windowEndMillis(LocalDate.now(zone), settings)
        val at = now + Random.nextLong(RETRY_MIN_MS, RETRY_MAX_MS + 1)
        if (at > end - 60_000) return null

        setAlarm(context, at, attempt)
        repository.updateSettings { it.copy(nextRunAt = at, retryAttempt = attempt, pendingRunAt = at) }
        return at
    }
}
