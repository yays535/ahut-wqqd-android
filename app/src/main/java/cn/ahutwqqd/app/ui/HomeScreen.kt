package cn.ahutwqqd.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ahutwqqd.app.data.AppSettings
import cn.ahutwqqd.app.data.MAX_STUDENTS
import cn.ahutwqqd.app.data.SignStatus
import cn.ahutwqqd.app.data.Student
import cn.ahutwqqd.app.work.Scheduler
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editorStudent by remember { mutableStateOf<Student?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    // 每 30 秒走一次时钟，让「今天 / 明天」和倒计时描述自己更新
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) { viewModel.syncSchedule() }

    val today = remember(tick) { LocalDate.now(ZoneId.of("Asia/Shanghai")) }
    val signedToday = students.count { it.lastSuccessDate == today.toString() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (students.size >= MAX_STUDENTS) {
                        viewModel.toast("最多只能添加 $MAX_STUDENTS 名学生")
                    } else {
                        editorStudent = null
                        showEditor = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加学生") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageHeader(
                    today = today,
                    onOpenLogs = onOpenLogs,
                    onOpenSettings = onOpenSettings
                )
            }
            item {
                SummaryCard(total = students.size, signed = signedToday)
            }
            item {
                AutoSignCard(
                    settings = settings,
                    onToggle = viewModel::setAutoSign,
                    onReschedule = viewModel::reschedule,
                    onOpenGuide = onOpenGuide
                )
            }

            if (students.isEmpty()) {
                item { EmptyState() }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "学生列表",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${students.size} 人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { viewModel.signInAll() },
                            enabled = running.isEmpty() && students.isNotEmpty()
                        ) {
                            Text("全部签到")
                        }
                    }
                }
                items(students, key = { it.id }) { student ->
                    StudentCard(
                        student = student,
                        running = student.id in running,
                        progress = progress[student.id],
                        onClick = {
                            editorStudent = student
                            showEditor = true
                        },
                        onSignIn = { viewModel.signIn(student.id) }
                    )
                }
            }
        }
    }

    if (showEditor) {
        StudentEditSheet(
            initial = editorStudent,
            existing = students,
            onDismiss = { showEditor = false },
            onSave = { student ->
                viewModel.upsert(student)
                showEditor = false
            },
            onDelete = { student ->
                viewModel.delete(student.id)
                showEditor = false
            }
        )
    }
}

@Composable
private fun PageHeader(
    today: LocalDate,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val weekday = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[
        today.dayOfWeek.value - 1
    ]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${today.monthValue} 月 ${today.dayOfMonth} 日 · $weekday",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "晚寝签到",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        HeaderAction(Icons.Filled.DateRange, "签到记录", onOpenLogs)
        Spacer(Modifier.width(4.dp))
        HeaderAction(Icons.Filled.Settings, "设置", onOpenSettings)
    }
}

@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun SummaryCard(total: Int, signed: Int) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(value = total.toString(), label = "学生", modifier = Modifier.weight(1f))
            VerticalDivider()
            StatItem(value = signed.toString(), label = "今日已签", modifier = Modifier.weight(1f))
            VerticalDivider()
            StatItem(
                value = (total - signed).coerceAtLeast(0).toString(),
                label = "待签到",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .height(30.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f))
    )
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun AutoSignCard(
    settings: AppSettings,
    onToggle: (Boolean) -> Unit,
    onReschedule: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "自动签到",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Pill(text = "测试中", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (settings.autoSignEnabled) {
                            "下次 ${Scheduler.describe(settings.nextRunAt)}"
                        } else {
                            "已关闭，只会手动签到"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.autoSignEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
                Switch(
                    checked = settings.autoSignEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (settings.autoSignEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        text = "窗口 ${formatMinutes(settings.windowStart)}–${formatMinutes(settings.windowEnd)}",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Pill(
                        text = "偏移 ${settings.jitterRadiusMeters} 米",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onReschedule,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("重新安排", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenGuide)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (settings.autoSignEnabled) "确认自动签到真的会执行" else "开启后需要做哪些准备",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "逐项检查权限、后台限制与退出方式",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun formatMinutes(minutes: Int): String =
    String.format(Locale.US, "%02d:%02d", (minutes / 60) % 24, minutes % 60)

@Composable
private fun StudentCard(
    student: Student,
    running: Boolean,
    progress: String?,
    onClick: () -> Unit,
    onSignIn: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    student.displayName.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        student.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (student.hasCustomLocation) {
                        Spacer(Modifier.width(6.dp))
                        Pill(text = "自定义位置", color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (!student.enabled) {
                        Spacer(Modifier.width(6.dp))
                        Pill(text = "手动", color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (running) SignStatus.RUNNING else student.status)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (running) progress ?: "正在签到…" else statusText(student),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
            }

            FilledTonalButton(
                onClick = onSignIn,
                enabled = !running,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("签到", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun StatusDot(status: SignStatus) {
    val color = when (status) {
        SignStatus.SUCCESS -> MaterialTheme.colorScheme.secondary
        SignStatus.FAILED -> MaterialTheme.colorScheme.error
        SignStatus.RUNNING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(color, CircleShape)
    )
}

private fun statusText(student: Student): String {
    val zone = ZoneId.of("Asia/Shanghai")
    val today = LocalDate.now(zone).toString()
    // 状态是跨天保留的：昨天失败过，今天不该继续显示昨天的失败原因
    val ranToday = student.lastRunAt > 0 &&
        Instant.ofEpochMilli(student.lastRunAt).atZone(zone).toLocalDate().toString() == today
    return when {
        student.lastSuccessDate == today -> {
            val time = student.lastRunAt.takeIf { it > 0 }?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone)
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            }
            "今日已签到${if (time != null) " · $time" else ""}"
        }
        student.status == SignStatus.FAILED && ranToday ->
            student.lastMessage.ifBlank { "签到失败" }
        student.status == SignStatus.RUNNING -> "正在签到…"
        else -> "今日未签到 · ${student.studentId}"
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "还没有添加学生",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "点右下角添加，支持一次为多人签到",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
