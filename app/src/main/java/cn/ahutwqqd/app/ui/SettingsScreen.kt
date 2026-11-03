package cn.ahutwqqd.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ahutwqqd.app.BuildConfig
import cn.ahutwqqd.app.util.Permissions
import cn.ahutwqqd.app.work.Notifier
import cn.ahutwqqd.app.work.Scheduler
import kotlinx.coroutines.delay

/** 窗口最短跨度：低于这个值随机空间太小，容易和「未到签到时间」撞上 */
private const val MIN_WINDOW_MINUTES = 10

/** 一天里的最后一分钟（23:59），窗口结束时间最多到这里 */
private const val LAST_MINUTE = 23 * 60 + 59

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit, onOpenGuide: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val hasLogs = logs.isNotEmpty()
    val context = LocalContext.current

    // 从系统设置页返回时刷新权限状态
    var permStamp by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permStamp++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsOk = remember(permStamp) { Permissions.hasNotifications(context) }
    val exactAlarmOk = remember(permStamp) { Permissions.canExactAlarm(context) }
    val batteryOk = remember(permStamp) { Permissions.ignoresBatteryOptimizations(context) }

    // 每 30 秒走一次表，让「今天 / 明天 21:47」随实际时间更新
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    val nextLabel = remember(tick, settings.nextRunAt) { Scheduler.describe(settings.nextRunAt) }

    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            SectionCard(title = "自动签到") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenGuide)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "自动签到保障",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "检查权限与后台限制，确认到点会执行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "去检查 ›",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "启用定时签到",
                    subtitle = if (settings.autoSignEnabled) "下次 $nextLabel" else "关闭后不会自动执行",
                    checked = settings.autoSignEnabled,
                    onCheckedChange = viewModel::setAutoSign
                )

                if (settings.autoSignEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TimeRow(
                        label = "窗口开始",
                        value = formatMinutes(settings.windowStart),
                        onClick = { editingStart = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TimeRow(
                        label = "窗口结束",
                        value = formatMinutes(settings.windowEnd),
                        onClick = { editingEnd = true }
                    )
                    Text(
                        "点这两行分别修改开始和结束。每天在窗口内随机取一个时刻执行，" +
                            "起点留 40 秒缓冲、终点留 5 分钟收尾，最短间隔 10 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = viewModel::reschedule) { Text("重新随机安排") }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "后台守护",
                    subtitle = if (settings.guardEnabled) {
                        "常驻一条通知，防止系统冻结后取消定时"
                    } else {
                        "已关闭，系统可能在后台取消定时"
                    },
                    checked = settings.guardEnabled,
                    onCheckedChange = viewModel::setGuard
                )
                Text(
                    "国产系统会在应用退到后台后冻结它，并把定时闹钟一并删掉。" +
                        "开启守护后应用以常驻服务的形式留在后台，闹钟才留得住。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "定位偏移") {
                Text(
                    "签到坐标以宿舍（或自定义位置）为圆心随机偏移，模拟真实 GPS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                RadiusSlider(value = settings.jitterRadiusMeters, onCommit = viewModel::setRadius)
            }

            SectionCard(title = "通知") {
                SettingSwitchRow(
                    title = "签到成功提醒",
                    subtitle = "全部成功时发一条通知",
                    checked = settings.notifyOnSuccess,
                    onCheckedChange = { viewModel.setNotify(success = it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "签到失败提醒",
                    subtitle = "失败时立即通知，并带上失败原因",
                    checked = settings.notifyOnFailure,
                    onCheckedChange = { viewModel.setNotify(failure = it) }
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { Notifier.testNotification(context) }) {
                        Text("发一条测试通知")
                    }
                }
            }

            SectionCard(title = "外观") {
                Text(
                    "明暗模式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        // 用主色系而不是默认的 secondaryContainer：后者在这套配色里
                        // 固定是绿色（留给「签到」这类动作按钮和成功态），换了主题之后
                        // 选中胶囊还是一片绿，看着像没跟上主题
                        FilterChip(
                            selected = ThemeMode.of(settings.themeMode) == mode,
                            onClick = { viewModel.setThemeMode(mode.key) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "配色",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                ThemeStyle.entries.chunked(3).forEach { rowStyles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowStyles.forEach { style ->
                            StyleSwatch(
                                style = style,
                                selected = ThemeStyle.of(settings.themeStyle) == style,
                                onClick = { viewModel.setThemeStyle(style.key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionCard(title = "系统权限") {
                Text(
                    "这三项直接决定自动签到能不能按时跑起来，建议全部开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                PermissionRow(
                    title = "通知权限",
                    ok = notificationsOk,
                    hint = "未开启，收不到签到结果",
                    onClick = { Permissions.openNotificationSettings(context) }
                )
                PermissionRow(
                    title = "精确闹钟",
                    ok = exactAlarmOk,
                    hint = "未开启，可能延后几分钟触发",
                    onClick = { Permissions.openExactAlarmSettings(context) }
                )
                PermissionRow(
                    title = "电池优化白名单",
                    ok = batteryOk,
                    hint = "未加入，后台容易被系统清理",
                    onClick = { Permissions.requestIgnoreBatteryOptimizations(context) }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "小米 / 华为 / OPPO / vivo 还需要在「手机管家」里允许自启动和后台活动，" +
                        "否则到点可能不执行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            SectionCard(title = "数据") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasLogs) { confirmingClear = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "清空签到记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasLogs) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            if (hasLogs) "共 ${logs.size} 条，只删除本地历史，不影响学生配置"
                            else "暂无签到记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (hasLogs) 1f else 0.5f
                            )
                        )
                    }
                    Text(
                        "清空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasLogs) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    )
                }
            }

            SectionCard(title = "关于") {
                InfoRow("应用", "AHUT晚寝签到 ${BuildConfig.VERSION_NAME}")
                InfoRow("账号", "学号与密码只存在本机，密码经 Keystore 加密")
                InfoRow("联网", "仅访问学校考勤系统 xskq.ahut.edu.cn")
                InfoRow("说明", "非官方工具，自动签到仍在测试阶段")

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Text(
                    "使用须知",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                DisclaimerItem(
                    "1",
                    "本工具依赖学校考勤系统的现有接口。一旦校方调整接口、增加校验或变更签到规则，" +
                        "相关功能可能随时失效，也无法保证能及时适配。"
                )
                Spacer(Modifier.height(8.dp))
                DisclaimerItem(
                    "2",
                    "本工具为个人学习之作，与学校官方无关。请自行判断使用方式是否合规，" +
                        "因使用本工具而产生的任何后果，由使用者自行承担。"
                )
            }
        }
    }

    if (confirmingClear) {
        ConfirmDialog(
            title = "清空签到记录？",
            message = "将删除本机保存的 ${logs.size} 条签到历史，学生配置和密码不受影响。此操作无法撤销。",
            confirmText = "清空",
            onConfirm = {
                confirmingClear = false
                viewModel.clearLogs()
                viewModel.toast("已清空签到记录")
            },
            onDismiss = { confirmingClear = false }
        )
    }

    if (editingStart) {
        WheelTimeDialog(
            title = "窗口开始时间",
            initialMinutes = settings.windowStart,
            onDismiss = { editingStart = false },
            onConfirm = { raw ->
                editingStart = false
                val start = raw.coerceIn(0, LAST_MINUTE - MIN_WINDOW_MINUTES)
                val end = maxOf(start + MIN_WINDOW_MINUTES, settings.windowEnd)
                viewModel.setWindow(start, end)
                when {
                    start != raw -> viewModel.toast("开始时间最晚可设为 ${formatMinutes(start)}")
                    end != settings.windowEnd -> viewModel.toast("结束时间已顺延到 ${formatMinutes(end)}")
                }
            }
        )
    }

    if (editingEnd) {
        WheelTimeDialog(
            title = "窗口结束时间",
            initialMinutes = settings.windowEnd,
            onDismiss = { editingEnd = false },
            onConfirm = { raw ->
                editingEnd = false
                val end = raw.coerceIn(MIN_WINDOW_MINUTES, LAST_MINUTE)
                val start = minOf(settings.windowStart, end - MIN_WINDOW_MINUTES)
                viewModel.setWindow(start, end)
                when {
                    end != raw -> viewModel.toast("结束时间最早可设为 ${formatMinutes(end)}")
                    start != settings.windowStart -> viewModel.toast("开始时间已提前到 ${formatMinutes(start)}")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
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
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun RadiusSlider(value: Int, onCommit: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "偏移半径",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                "${local.toInt()} 米",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local.toInt()) },
        valueRange = AppViewModel.MIN_RADIUS.toFloat()..AppViewModel.MAX_RADIUS.toFloat(),
        modifier = Modifier.fillMaxWidth()
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "${AppViewModel.MIN_RADIUS} 米",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${AppViewModel.MAX_RADIUS} 米",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun PermissionRow(title: String, ok: Boolean, hint: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    CircleShape
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (ok) "已开启" else hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        }
        Text(
            if (ok) "去查看" else "去开启",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DisclaimerItem(index: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$index.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StyleSwatch(
    style: ThemeStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(style.previewColor(), CircleShape)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                style.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
