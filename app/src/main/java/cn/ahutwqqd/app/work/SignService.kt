package cn.ahutwqqd.app.work

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import cn.ahutwqqd.app.data.RunTrigger
import cn.ahutwqqd.app.data.SignRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自动签到执行体。
 *
 * 用前台服务而不是 WorkManager：提交前有 4–10 秒随机等待，多人叠加就是半分钟起，
 * WorkManager 的 Doze 配额会把它掐断。
 */
class SignService : Service() {

    companion object {
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_TRIGGER = "trigger"
        const val MAX_ATTEMPT = 3

        /**
         * 上一轮签到的「最晚结束时刻」。
         *
         * 用时间戳而不是布尔值：vivo 会在后台冻结本应用，协程跑到一半被冻住时
         * `finally` 不执行，[busyUntil] 就一直停在过去；换成布尔值会永久卡死，
         * 导致之后每次闹钟触发都「只发通知、不签到、也不排下一次」。
         * 这里只要「过期时间已过」就视为上一轮已结束，允许重新接管。
         */
        @Volatile
        private var busyUntil: Long = 0L

        /** 一轮签到允许的最长时长，超过就认为它已经死掉、可以重入 */
        private const val BUSY_TIMEOUT_MS = 3 * 60_000L

        private fun tryAcquireBusy(): Boolean = synchronized(this) {
            val now = System.currentTimeMillis()
            if (busyUntil == 0L || now >= busyUntil) {
                busyUntil = now + BUSY_TIMEOUT_MS
                true
            } else {
                false
            }
        }

        private fun releaseBusy() = synchronized(this) { busyUntil = 0L }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val attempt = intent?.getIntExtra(EXTRA_ATTEMPT, 0) ?: 0
        val trigger = when (intent?.getStringExtra(EXTRA_TRIGGER)) {
            "RETRY" -> RunTrigger.RETRY
            "AUTO" -> RunTrigger.AUTO
            else -> RunTrigger.AUTO
        }

        // 无论是否已有任务在跑，都必须先 startForeground，否则会被系统判定为 ANR
        goForeground("准备签到")

        if (!tryAcquireBusy()) {
            // 上一轮确实还在跑（重试闹钟重叠时才会遇到），这次触发忽略。
            // 但绝不能像以前那样悄悄 return：上一轮自己结束时会补排下一次，
            // 这里额外确保排程不丢，防止上一轮已经死掉、busy 却没过期时彻底断掉。
            scope.launch { ensureTomorrowIfStale() }
            return START_NOT_STICKY
        }

        acquireWakeLock()
        scope.launch {
            // 先认领这次闹钟：清掉待执行标记，否则守护服务的看门狗会把
            // 这次正常执行误判成「闹钟没启动」，两分钟后弹出失败通知
            runCatching {
                SignRepository.instance.updateSettings { it.copy(pendingRunAt = 0L) }
            }
            try {
                runOnce(attempt, trigger)
            } finally {
                releaseWakeLock()
                releaseBusy()
                ensureTomorrowIfStale()
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** 兜底：排程丢了就补排明天，避免第二天彻底没有安排 */
    private suspend fun ensureTomorrowIfStale() {
        runCatching {
            val current = SignRepository.instance.settings.value
            val stale = current.nextRunAt == 0L ||
                current.nextRunAt < System.currentTimeMillis()
            if (current.autoSignEnabled && stale) {
                Scheduler.scheduleForTomorrow(this@SignService)
            }
        }
    }

    private suspend fun runOnce(attempt: Int, trigger: RunTrigger) {
        val repository = SignRepository.instance
        val settings = repository.settings.value

        if (!settings.autoSignEnabled) {
            Notifier.cancelService(this)
            return
        }

        updateText("正在为 ${repository.students.value.count { it.enabled }} 人签到", 0, 0)
        val summary = repository.signAll(
            trigger = trigger,
            skipTodaySuccess = true,
            onProgress = { done, total -> updateText("已处理 $done / $total", done, total) }
        )

        if (summary.total == 0) {
            // 今天全部签过了，直接排明天
            Scheduler.scheduleForTomorrow(this)
            Notifier.cancelService(this)
            return
        }

        val retryAt = if (summary.retryable && attempt < MAX_ATTEMPT) {
            Scheduler.scheduleRetry(this, attempt + 1)
        } else {
            null
        }

        // 排了重试就等重试闹钟；否则直接安排明天的随机时刻
        if (retryAt == null) Scheduler.scheduleForTomorrow(this)

        Notifier.notifySummary(this, summary, settings, retryAt)
        Notifier.cancelService(this)
    }

    private fun updateText(text: String, done: Int, total: Int) {
        runCatching {
            if (total > 0) Notifier.updateService(this, text, done, total)
            else Notifier.updateService(this, text)
        }
    }

    private fun goForeground(text: String) {
        val notification = Notifier.serviceNotification(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifier.ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notifier.ID_SERVICE, notification)
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val manager = getSystemService(PowerManager::class.java) ?: return
            wakeLock = manager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ahut:sign")
                .apply {
                    setReferenceCounted(false)
                    acquire(10 * 60 * 1000L)
                }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
        }
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
