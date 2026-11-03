package cn.ahutwqqd.app.work

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import cn.ahutwqqd.app.data.SignRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 常驻守护服务。
 *
 * 解决的问题：vivo / 小米 / OPPO 这类 ROM 会在应用退到后台后冻结它，
 * 并把 AlarmManager 里属于该应用的闹钟一并移除——实测 vivo 上只要退到后台
 * 20 秒，闹钟就没了，进程却还活着。光有精确闹钟根本等不到触发时刻。
 *
 * 办法是让进程一直挂着前台服务：系统把它当作「有可见任务的活跃进程」，
 * 不冻结、不清闹钟。代价是通知栏常驻一条不响不亮的通知。
 *
 * 顺带承担看门狗：闹钟时刻写入 [AppSettings.pendingRunAt]，SignService 接到
 * 闹钟后立刻清 0。若到点 120 秒还没被清，说明闹钟确实被系统清掉了，
 * 这时发一条「签到失败」通知，而不是让用户第二天才发现没签上。
 */
class GuardService : Service() {

    companion object {
        /** 到点后多久仍未接单，判定为「没能启动」 */
        private const val MISS_GRACE_MS = 120_000L

        /** 轮询间隔：文案刷新与看门狗共用，误差不超过这个值 */
        private const val TICK_MS = 15_000L

        /**
         * 服务是否正在运行。
         *
         * 常用于「自动签到指引」页判断守护有没有真的起来——开关是开的、
         * 服务却被系统掐死的情况，只查设置项是看不出来的。
         */
        @Volatile
        var alive: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GuardService::class.java)) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastLabel: String = ""
    private var looping = false

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务必须在 5 秒内 startForeground，否则系统直接判定 ANR
        goForeground()
        alive = true
        startLoop()
        // 被系统回收后由 START_STICKY 重建时 intent 为 null，这里不依赖任何 extra
        return START_STICKY
    }

    private fun goForeground() {
        val label = runCatching {
            Scheduler.describe(SignRepository.instance.settings.value.nextRunAt)
        }.getOrDefault("未安排")
        lastLabel = label
        val notification = Notifier.guardNotification(this, label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 14 起 dataSync 类型每天只有 6 小时配额，常驻守护会被系统掐断，
            // 因此 34+ 改用 specialUse
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(Notifier.ID_GUARD, notification, type)
        } else {
            startForeground(Notifier.ID_GUARD, notification)
        }
    }

    private fun startLoop() {
        if (looping) return
        looping = true
        scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(TICK_MS)
            }
        }
    }

    private suspend fun tick() {
        val repository = SignRepository.instance
        val settings = repository.settings.value
        android.util.Log.i(
            "AHUT",
            "guard tick auto=${settings.autoSignEnabled} guard=${settings.guardEnabled} " +
                "next=${settings.nextRunAt} pending=${settings.pendingRunAt}"
        )

        // 用户关掉了守护或自动签到，服务没有存在的意义，直接退出
        if (!settings.guardEnabled || !settings.autoSignEnabled) {
            stopSelf()
            return
        }

        val label = Scheduler.describe(settings.nextRunAt)
        if (label != lastLabel) {
            lastLabel = label
            Notifier.updateGuard(this, label)
        }

        val pending = settings.pendingRunAt
        if (pending > 0 && System.currentTimeMillis() >= pending + MISS_GRACE_MS) {
            if (settings.notifyOnFailure) Notifier.notifyMissed(this, pending)
            // 清掉标记，避免下一轮重复提醒
            repository.updateSettings { it.copy(pendingRunAt = 0L) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        looping = false
        alive = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
