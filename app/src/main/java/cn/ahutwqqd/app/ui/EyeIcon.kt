package cn.ahutwqqd.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * 眼睛图标（密码显示 / 隐藏）。
 *
 * 只为一个图标引入 material-icons-extended 会让依赖包膨胀二十多兆，
 * 这里直接用 Canvas 画，形状按 24dp 网格等比缩放。
 */
@Composable
fun EyeIcon(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null
) {
    Canvas(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }
    ) {
        val s = size.minDimension / 24f
        val center = Offset(size.width / 2, size.height / 2)
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun p(x: Float, y: Float) = Offset(center.x + (x - 12f) * s, center.y + (y - 12f) * s)

        val outline = Path().apply {
            val left = p(2.6f, 12f)
            val top = p(12f, 4.8f)
            val right = p(21.4f, 12f)
            val bottom = p(12f, 19.2f)
            moveTo(left.x, left.y)
            quadraticTo(top.x, top.y, right.x, right.y)
            quadraticTo(bottom.x, bottom.y, left.x, left.y)
        }
        drawPath(outline, color = tint, style = stroke)
        drawCircle(color = tint, radius = 3.3f * s, center = center, style = stroke)

        if (!visible) {
            val from = p(4.2f, 4.2f)
            val to = p(19.8f, 19.8f)
            drawLine(
                color = tint,
                start = from,
                end = to,
                strokeWidth = 1.9f * s,
                cap = StrokeCap.Round
            )
        }
    }
}
