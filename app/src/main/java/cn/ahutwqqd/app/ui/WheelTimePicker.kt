package cn.ahutwqqd.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.conflate
import java.util.Locale

/** 单行高度，滚动步进就是它 */
private val ITEM_HEIGHT: Dp = 42.dp

/** 滚轮可视高度：上下各留 2 行，中间一行是选中值 */
private val WHEEL_HEIGHT: Dp = 210.dp

/** 选中项停在中间的偏移量 */
private val CENTER_PADDING: Dp = (WHEEL_HEIGHT - ITEM_HEIGHT) / 2

/** 松手后等这么久再补对齐，避开手指抬起的那一帧 */
private const val SETTLE_DELAY_MS = 24L

/** 补对齐动画时长：短一点，跟手、不拖沓 */
private const val SQUASH_DURATION_MS = 110

/** 同一位置最多补对齐几次，防止极端情况下反复微调 */
private const val MAX_ALIGN_ATTEMPTS = 3

/**
 * 滚动式时间选择框。
 *
 * 两列滚轮（时、分），中间一行高亮；拖动停止后自动吸附到最近的一项，
 * 停在哪一项就把哪一项报给上层。
 */
@Composable
fun WheelTimeDialog(
    title: String,
    initialMinutes: Int,
    minuteStep: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hour by remember { mutableIntStateOf((initialMinutes / 60) % 24) }
    var minute by remember { mutableIntStateOf(initialMinutes % 60) }
    // 分钟值不在步进网格上时（比如改过窗口后是 07 分而步进是 5），就近对齐
    val minuteCount = 60 / minuteStep

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Wheel(
                        count = 24,
                        selected = hour,
                        onSelected = { hour = it },
                        formatter = { String.format(Locale.US, "%02d", it) }
                    )
                    Text(
                        ":",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Wheel(
                        count = minuteCount,
                        selected = (minute / minuteStep).coerceIn(0, minuteCount - 1),
                        onSelected = { minute = it * minuteStep },
                        formatter = { String.format(Locale.US, "%02d", it * minuteStep) }
                    )
                }
                Text(
                    "上下滑动选择，中间一行为当前值",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour * 60 + minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun Wheel(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    formatter: (Int) -> String
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { ITEM_HEIGHT.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val lastReported = remember { intArrayOf(selected) }
    val surface = MaterialTheme.colorScheme.surface

    // 当前落在中线上的那一格。
    // 滚动过程中跟着手指走，中间的高亮和字号才会跟着换行；如果只认外面传进来的
    // selected，高亮会跟着格子一起滚出中间，看着就像滚轮卡住不动了。
    val centeredIndex by remember(count, itemHeightPx) {
        derivedStateOf {
            val index = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            (index + if (offset * 2 >= itemHeightPx) 1 else 0).coerceIn(0, count - 1)
        }
    }

    // 停手之后再做一次轻量对齐，并回报选中值。
    //
    // 注意这里用的是普通的 collect 而不是 collectLatest：补对齐动画自己也会把
    // isScrollInProgress 置成 true，collectLatest 会把这当成新事件，直接把正在跑的
    // 对齐取消掉，然后陷入「对齐—取消—再对齐」的抖动。
    // 而普通的 collect 一旦对齐被用户的新手势抢走，MutationMutex 抛出的
    // CancellationException 会顺着收集协程往上冒，把整个 LaunchedEffect 干掉 ——
    // 之后滚轮还能滑却再也不更新数值，这就是「有时候会卡住」的真正原因。
    // 所以：collect + conflate + 就地吃掉这次的取消（ensureActive 只在整条协程
    // 真的被取消时才重新抛出）。
    LaunchedEffect(state, itemHeightPx, count) {
        var alignAttempts = 0
        snapshotFlow { state.isScrollInProgress }
            .conflate()
            .collect { scrolling ->
                if (scrolling) return@collect
                // 等手势事件彻底收尾，避免对齐动画和刚抬起的手指抢同一帧
                delay(SETTLE_DELAY_MS)
                // 这一小段延迟里用户又上手了，就让给手势
                if (state.isScrollInProgress) return@collect
                val index = state.firstVisibleItemIndex
                val offset = state.firstVisibleItemScrollOffset
                val centered = centeredIndex
                if (centered != lastReported[0]) {
                    lastReported[0] = centered
                    onSelected(centered)
                }
                if (offset == 0) {
                    alignAttempts = 0
                    return@collect
                }
                // 停在两行之间时补一小段滚动，让选中行落到中线上；
                // 用短 tween 而不是默认 spring，避免回弹拖沓。
                if (alignAttempts >= MAX_ALIGN_ATTEMPTS) return@collect
                alignAttempts++
                val delta =
                    if (centered > index) itemHeightPx - offset.toFloat() else -offset.toFloat()
                try {
                    state.animateScrollBy(
                        delta,
                        tween(durationMillis = SQUASH_DURATION_MS, easing = FastOutSlowInEasing)
                    )
                } catch (cancelled: CancellationException) {
                    // 对齐被用户的新手势打断——只放弃这一次，不要终止收集
                    currentCoroutineContext().ensureActive()
                }
            }
    }

    Box(
        modifier = Modifier
            .height(WHEEL_HEIGHT)
            .width(84.dp),
        contentAlignment = Alignment.Center
    ) {
        // 中间高亮条，也就是选中行
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    RoundedCornerShape(12.dp)
                )
        )

        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            contentPadding = PaddingValues(vertical = CENTER_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(count, key = { it }) { index ->
                val isSelected = index == centeredIndex
                Box(
                    modifier = Modifier
                        .height(ITEM_HEIGHT)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        formatter(index),
                        style = if (isSelected) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        }
                    )
                }
            }
        }

        // 上下渐隐，让滚轮看起来是圆柱而不是一个列表
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(CENTER_PADDING)
                .background(
                    Brush.verticalGradient(listOf(surface, surface.copy(alpha = 0f)))
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(CENTER_PADDING)
                .background(
                    Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))
                )
        )
    }
}
