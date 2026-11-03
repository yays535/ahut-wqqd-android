package cn.ahutwqqd.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.ahutwqqd.app.data.SignRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机、应用更新、系统时间变更后补排闹钟。
 *
 * AlarmManager 里的闹钟在重启后会全部丢失，不补排的话「每天自动签到」到第二天就断了。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in HANDLED) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SignRepository.init(appContext)
                val settings = SignRepository.instance.settings.value
                // 开机后把守护服务挂回去，否则系统仍会在后台冻结本应用并清掉闹钟
                if (settings.guardEnabled && settings.autoSignEnabled) {
                    GuardService.start(appContext)
                }
                Scheduler.ensureScheduled(appContext)
            } catch (_: Throwable) {
                // 开机阶段失败不重试，下次打开 App 会再补
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
