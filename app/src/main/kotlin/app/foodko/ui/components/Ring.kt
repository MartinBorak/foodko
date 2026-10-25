package app.foodko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.foodko.ui.theme.successColor

/**
 * How much of [target] the [value] covers, as a ring. Under the target: a green arc on a grey
 * track, the grey being what is left. Over the target: a full ring in [overColor], red by default.
 */
@Composable
fun TargetRing(
    value: Double,
    target: Double,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp,
    overColor: Color = MaterialTheme.colorScheme.error,
) {
    val over = value > target
    val fraction = if (over) 1f else (value / target).toFloat().coerceIn(0f, 1f)
    val progressColor = if (over) overColor else successColor()
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.size(size)) {
        val strokePx = stroke.toPx()
        val style = Stroke(width = strokePx, cap = StrokeCap.Round)
        val inset = strokePx / 2
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        if (fraction > 0f) {
            drawArc(progressColor, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        }
    }
}
