package cn.ahutwqqd.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.ahutwqqd.app.MainActivity
import cn.ahutwqqd.app.R
import cn.ahutwqqd.app.data.AppSettings
import cn.ahutwqqd.app.data.SignRepository

object Notifier {

    const val CHANNEL_SERVICE = "sign_service"
    const val CHANNEL_RESULT = "sign_result"
    const val CHANNEL_GUARD = "sign_guard"

    const val ID_GUARD = 1000
    const val ID_SERVICE = 1001
    const val ID_RESULT = 1002
    const val ID_MISSED = 1004

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_service_desc)
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                context.getString(R.string.channel_result_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_result_desc)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GUARD,
                context.getString(R.string.channel_guard_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_guard_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun serviceNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("晚寝签到")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent(context))
            .build()

    fun updateService(context: Context, text: String, progress: Int? = null, max: Int? = null) {
        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("晚寝签到")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(context))
        if (progress != null && max != null) {
            builder.setProgress(max, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_SERVICE, builder.build())
        }
    }

    /**
     * 一轮签到结束后的汇总通知。全部成功且用户关掉成功提醒时不打扰。
     */
    fun notifySummary(
        context: Context,
        summary: SignRepository.Summary,
        settings: AppSettings,
        retryAt: Long?
    ) {
        val hasFailure = summary.failures.isNotEmpty()
        if (hasFailure && !settings.notifyOnFailure) return
        if (!hasFailure && !settings.notifyOnSuccess) return
        if (summary.total == 0) return

        val title = when {
            !hasFailure -> "签到完成"
            summary.success == 0 -> "签到失败"
            else -> "部分签到失败"
        }

        val text = when {
            !hasFailure -> "${summary.total} 人全部签到成功"
            summary.success == 0 -> "${summary.failures.size} 人签到失败"
            else -> "${summary.success} 人成功，${summary.failures.size} 人失败"
        }

        val detail = buildString {
            if (hasFailure) {
                summary.failures.forEach { failure ->
                    append(failure.student.displayName).append("：").append(failure.message).append('\n')
                }
                if (retryAt != null) {
                    append("将在 ").append(Scheduler.describe(retryAt)).append(" 自动重试")
                } else {
                    append("今日不再重试，可手动补签")
                }
            } else {
                append("签到时间：").append(Scheduler.describe(System.currentTimeMillis()))
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(ID_RESULT, notification) }
    }

    fun cancelService(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_SERVICE) }
    }

    /**
     * 常驻守护通知。
     *
     * 它存在的意义一半是告知、一半是保命：只要这条通知在，进程就是前台服务，
     * 国产 ROM 的省电模块才不会冻结它、才不会把 AlarmManager 里的闹钟清掉。
     */
    fun guardNotification(context: Context, nextLabel: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("晚寝签到守护中")
            .setContentText("下次 $nextLabel · 请勿关闭该任务")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "下次 $nextLabel · 请勿关闭该任务\n" +
                        "若从最近任务划掉、或在手机管家「一键清理」时清掉本应用，" +
                        "系统会连带删除定时签到，当晚就不会自动执行。"
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent(context))
            .build()

    fun updateGuard(context: Context, nextLabel: String) {
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_GUARD, guardNotification(context, nextLabel))
        }
    }

    /** 到点后 120 秒仍没接单：闹钟多半被系统清掉了，必须让用户知道 */
    fun notifyMissed(context: Context, at: Long) {
        val whenText = Scheduler.describe(at)
        val detail = buildString {
            append("原定 ").append(whenText).append(" 自动签到，但到点后 120 秒仍未启动。\n")
            append("常见原因是系统冻结了本应用，或它被强制停止，定时任务随之被取消。\n")
            append("建议：打开应用重新排一次，并确认「后台守护」处于开启状态。")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("签到失败")
            .setContentText("$whenText 的定时任务未能启动")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_MISSED, notification) }
    }

    /** 设置页里的“发一条测试通知”，用来确认通知权限和渠道都正常 */
    fun testNotification(context: Context) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("通知正常")
            .setContentText("晚寝签到结果会通过这个渠道提醒你")
            .setStyle(NotificationCompat.BigTextStyle().bigText("这条是测试通知。签到成功或失败后，你会收到同样样式的一条汇总提醒。"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_RESULT + 1, notification) }
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
