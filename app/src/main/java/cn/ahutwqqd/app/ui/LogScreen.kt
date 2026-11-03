package cn.ahutwqqd.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ahutwqqd.app.data.RunLog
import cn.ahutwqqd.app.data.RunTrigger
import cn.ahutwqqd.app.work.Scheduler
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val grouped = remember(logs) { groupByDate(logs) }
    var confirmingClear by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RunLog?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("签到记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { confirmingClear = true }) {
                            Text(
                                "清空",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
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
        if (logs.isEmpty()) {
            EmptyLogs(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "hint") {
                    Text(
                        "长按任意一条记录，可以只删除那一条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                grouped.forEach { (date, entries) ->
                    item(key = "header-$date") {
                        DateHeader(date = date, entries = entries)
                    }
                    entries.forEach { log ->
                        item(key = "log-${log.id}") {
                            LogRow(log = log, onLongClick = { deleting = log })
                        }
                    }
                }
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

    deleting?.let { target ->
        val whenText = Instant.ofEpochMilli(target.at).atZone(Scheduler.zone)
            .format(DateTimeFormatter.ofPattern("M 月 d 日 HH:mm", Locale.CHINA))
        val resultText = if (target.success) {
            "签到成功"
        } else {
            target.message.ifBlank { "签到失败" }.let {
                if (it.length > 48) it.take(48) + "…" else it
            }
        }
        ConfirmDialog(
            title = "删除这条记录？",
            message = "$whenText · ${target.name} · $resultText\n\n" +
                "只删除这一条历史记录，学生配置和密码不受影响，今天的签到状态也不会改变。",
            confirmText = "删除",
            onConfirm = {
                val id = target.id
                deleting = null
                viewModel.deleteLog(id)
                viewModel.toast("已删除 1 条记录")
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, entries: List<RunLog>) {
    val today = LocalDate.now(Scheduler.zone)
    val title = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA))
    }
    val success = entries.count { it.success }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$success/${entries.size} 成功",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(log: RunLog, onLongClick: () -> Unit) {
    val message = log.message.ifBlank { if (log.success) "签到成功" else "签到失败" }
    var detail by remember { mutableStateOf(false) }
    // 学校接口改版时错误文案可能很长，超过两行就允许展开看全文
    val long = message.length > 40

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (long) detail = true },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (log.success) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.error,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        log.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (log.trigger != RunTrigger.MANUAL) {
                        Spacer(Modifier.width(6.dp))
                        Pill(
                            text = if (log.trigger == RunTrigger.RETRY) "重试" else "自动",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.success) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (long) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点按查看完整信息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                Instant.ofEpochMilli(log.at).atZone(Scheduler.zone)
                    .format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (detail) {
        AlertDialog(
            onDismissRequest = { detail = false },
            title = { Text(if (log.success) "签到详情" else "失败详情") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        log.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun EmptyLogs(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "还没有签到记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "签到一次之后，这里会留下每次的结果",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun groupByDate(logs: List<RunLog>): List<Pair<LocalDate, List<RunLog>>> =
    logs.groupBy { Instant.ofEpochMilli(it.at).atZone(Scheduler.zone).toLocalDate() }
        .toList()
        .sortedByDescending { it.first }
