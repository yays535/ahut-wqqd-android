package cn.ahutwqqd.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ahutwqqd.app.util.Permissions
import cn.ahutwqqd.app.work.GuardService
import cn.ahutwqqd.app.work.Scheduler
import java.util.Locale

/**
 * 「自动签到保障」指引页。
 *
 * 存在的理由：自动签到不是把开关打开就会跑。系统会冻结后台应用、清掉定时任务，
 * 而各家 ROM 的自启动管理又是私有实现。把「到底还需要做什么」逐条列出来，
 * 能检测的自动读状态，不能检测的让用户自己确认，避免出现「以为设好了、其实没跑」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 从系统设置页返回时重新读一遍状态
    var stamp by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) stamp++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsOk = remember(stamp) { Permissions.hasNotifications(context) }
    val exactAlarmOk = remember(stamp) { Permissions.canExactAlarm(context) }
    val batteryOk = remember(stamp) { Permissions.ignoresBatteryOptimizations(context) }

    // 关了自动签到，守护这项就没有意义；开了自动却关了守护，则视为缺失
    val guardOk = remember(stamp, settings.autoSignEnabled, settings.guardEnabled) {
        when {
            !settings.autoSignEnabled -> true
            !settings.guardEnabled -> false
            else -> GuardService.alive
        }
    }

    val missing = listOf(
        notificationsOk,
        exactAlarmOk,
        batteryOk,
        guardOk,
        settings.ackAutostart,
        settings.ackBackgroundPower
    ).count { !it }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("自动签到保障") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusBanner(
                autoSignEnabled = settings.autoSignEnabled,
                missing = missing,
                nextLabel = Scheduler.describe(settings.nextRunAt)
            )

            GuideSection("一、系统权限") {
                AutoRow(
                    title = "通知权限",
                    subtitle = "签到结果通过通知告诉你，不开就完全收不到",
                    ok = notificationsOk,
                    action = "去开启",
                    onClick = { Permissions.openNotificationSettings(context) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AutoRow(
                    title = "精确闹钟",
                    subtitle = "允许在指定时刻准点唤醒，关掉后可能延后几分钟",
                    ok = exactAlarmOk,
                    action = "去开启",
                    onClick = { Permissions.openExactAlarmSettings(context) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AutoRow(
                    title = "电池优化白名单",
                    subtitle = "不加入白名单，锁屏后容易被省电策略打断",
                    ok = batteryOk,
                    action = "去申请",
                    onClick = { Permissions.requestIgnoreBatteryOptimizations(context) }
                )
                Spacer(Modifier.height(4.dp))
                Note("以上三项由 App 直接读取系统状态，绿色表示确实已生效。")
            }

            GuideSection("二、厂商后台限制") {
                Text(
                    oemHint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                AckRow(
                    title = "允许自启动",
                    subtitle = "开机或应用被清理后，系统仍可拉起它执行签到",
                    checked = settings.ackAutostart,
                    onCheckedChange = { viewModel.setAck(autostart = it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AckRow(
                    title = "允许后台高耗电",
                    subtitle = "避免锁屏后被冻结，冻结会连带删除定时任务",
                    checked = settings.ackBackgroundPower,
                    onCheckedChange = { viewModel.setAck(backgroundPower = it) }
                )
                Spacer(Modifier.height(4.dp))
                Note("这两项是厂商私有开关，没有公开接口可以查询，需要你自己设置好后打勾。")
            }

            GuideSection("三、后台守护") {
                AutoRow(
                    title = "守护服务运行中",
                    subtitle = if (settings.autoSignEnabled) {
                        "常驻一条低优先级通知，防止系统冻结应用后取消定时"
                    } else {
                        "自动签到已关闭，暂时不需要守护"
                    },
                    ok = guardOk,
                    action = if (settings.guardEnabled) "去启动" else "去开启",
                    onClick = {
                        if (!settings.guardEnabled) viewModel.setGuard(true)
                        viewModel.checkGuard()
                        stamp++
                    }
                )
                Spacer(Modifier.height(4.dp))
                Note("通知栏里那条「晚寝签到守护中」就是这个服务，它在，说明守护是活的。")
            }

            GuideSection("四、退出方式") {
                Bullet("离开应用请按返回键或 Home 键，不要从最近任务里把卡片划掉")
                Bullet("不要点最近任务的「全部清除」，也别让手机管家做深度清理")
                Bullet("万一被清掉了，打开一次 App，定时会立刻重新排好")
                Spacer(Modifier.height(4.dp))
                Note(
                    "划掉卡片在系统里等同于「强制停止」，会把这个应用的所有定时任务一并删除，" +
                        "而且不会自动恢复。这是 Android 的既定行为，应用无法拦截。"
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "自动签到仍在测试阶段",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "它依赖系统的定时唤醒，各家手机的表现差异很大，偶尔可能出现" +
                            "没有按时执行的情况。启用后请留意是否收到签到成功通知，" +
                            "若没收到，请手动签到。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(
                    onClick = {
                        viewModel.checkGuard()
                        stamp++
                        viewModel.toast("已重新检查")
                    }
                ) {
                    Text("重新检查")
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(autoSignEnabled: Boolean, missing: Int, nextLabel: String) {
    val ready = autoSignEnabled && missing == 0
    val color = when {
        !autoSignEnabled -> MaterialTheme.colorScheme.outline
        ready -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                when {
                    !autoSignEnabled -> "自动签到未开启"
                    ready -> "已就绪"
                    else -> "还差 $missing 项"
                },
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !autoSignEnabled -> "回到设置打开「启用定时签到」，否则只会手动签到"
                    ready -> "下次 $nextLabel。保持应用在后台，到点会自动执行"
                    else -> "以下项目逐条完成后，到点才会真正自动签到"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun GuideSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun AutoRow(
    title: String,
    subtitle: String,
    ok: Boolean,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !ok, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GuideDot(ok)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            Text(
                action,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AckRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GuideDot(checked)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun GuideDot(ok: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                CircleShape
            )
    )
}

@Composable
private fun Bullet(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

/** 按品牌给出具体路径，比一句「去手机管家设置」有用得多 */
private fun oemHint(): String {
    val maker = Build.MANUFACTURER.lowercase(Locale.ROOT)
    return when {
        maker.contains("vivo") || maker.contains("iqoo") ->
            "vivo / iQOO：设置 → 电池 → 后台耗电管理 → 找到本应用 → 允许后台高耗电；" +
                "再打开 i管家 → 应用管理 → 自启动管理 → 允许本应用"

        maker.contains("xiaomi") || maker.contains("redmi") ->
            "小米 / Redmi：设置 → 应用设置 → 应用管理 → 找到本应用 → 允许自启动，" +
                "并把省电策略改成「无限制」"

        maker.contains("huawei") || maker.contains("honor") ->
            "华为 / 荣耀：设置 → 电池 → 应用启动管理 → 找到本应用 → 关闭自动管理，" +
                "把自启动、关联启动、后台活动三项都打开"

        maker.contains("oppo") || maker.contains("oneplus") || maker.contains("realme") ->
            "OPPO / 一加 / realme：设置 → 电池 → 应用耗电管理 → 允许后台运行；" +
                "再到应用管理里允许自启动"

        maker.contains("meizu") ->
            "魅族：手机管家 → 权限管理 → 后台管理 → 允许后台运行，并加入自启动白名单"

        maker.contains("samsung") ->
            "三星：设置 → 电池 → 后台使用限制 → 把本应用加入「永不休眠的应用」"

        else ->
            "在系统设置或手机管家里找到本应用，允许「自启动」和「后台活动」，" +
                "并把省电策略设为不限制"
    }
}
