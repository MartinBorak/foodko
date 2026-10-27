package app.foodko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.foodko.domain.TargetKind
import app.foodko.ui.theme.crossedColor
import app.foodko.ui.theme.progressColor

/**
 * How much of [target] the [value] covers, as a ring. Before the target: a blue arc on a grey
 * track, the grey being what is left. Once [crossed][TargetKind.crossed]: a full ring, red past a
 * limit, green for a reached goal.
 */
@Composable
fun TargetRing(
    value: Double,
    target: Double,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp,
    kind: TargetKind = TargetKind.Limit,
) {
    val crossed = kind.crossed(value, target)
    val fraction = if (crossed) 1f else (value / target).toFloat().coerceIn(0f, 1f)
    val arcColor = if (crossed) crossedColor(kind) else progressColor()
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.size(size)) {
        val strokePx = stroke.toPx()
        val style = Stroke(width = strokePx, cap = StrokeCap.Round)
        val inset = strokePx / 2
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        if (fraction > 0f) {
            drawArc(arcColor, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        }
    }
}
