package cn.ahutwqqd.app

import android.app.Application
import cn.ahutwqqd.app.data.SignRepository
import cn.ahutwqqd.app.work.Notifier
import cn.ahutwqqd.app.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AhutApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SignRepository.init(this)
        Notifier.ensureChannels(this)

        // 每次冷启动补排一次闹钟：系统清理后台、用户手动停止、时区变化都可能把它弄丢
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            runCatching { Scheduler.ensureScheduled(this@AhutApp) }
        }
    }
}
