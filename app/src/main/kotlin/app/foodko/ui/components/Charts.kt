package app.foodko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.foodko.domain.MacroSplit
import app.foodko.domain.Numbers
import kotlin.math.max

/**
 * Daily bars with an optional horizontal target line. [labels] are drawn under a few bars only,
 * so pass one per bar and let the chart thin them out.
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    target: Double?,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    formatValue: (Double) -> String = { Numbers.kcal(it) },
) {
    val barColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.error
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface
    val labelStyle = MaterialTheme.typography.labelSmall
    val tooltipStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val locale = currentLocale()
    // Tapped bar; cleared when the data changes (range switch).
    var selected by remember(values) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values) {
                detectTapGestures { offset ->
                    if (values.isEmpty()) return@detectTapGestures
                    val i = (offset.x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)
                    selected = if (selected == i) null else i
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val labelHeight = 16.dp.toPx()
        val topPad = 30.dp.toPx() // room for the tooltip above the tallest bar
        val plotHeight = size.height - labelHeight - topPad
        val maxValue = max(values.max(), target ?: 0.0).takeIf { it > 0 } ?: 1.0
        val scale = plotHeight / maxValue
        val slot = size.width / values.size
        val barWidth = slot * 0.7f

        values.forEachIndexed { i, v ->
            val h = (v * scale).toFloat()
            val x = i * slot + (slot - barWidth) / 2
            val color = when {
                i == selected -> selectedColor
                target != null && v > target -> overColor
                else -> barColor
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, topPad + plotHeight - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 4),
            )
        }

        selected?.let { i ->
            val text = measurer.measure(formatValue(values[i]), tooltipStyle.copy(color = tooltipFg))
            val padX = 8.dp.toPx()
            val padY = 4.dp.toPx()
            val w = text.size.width + 2 * padX
            val h = text.size.height + 2 * padY
            val barTop = topPad + plotHeight - (values[i] * scale).toFloat()
            val x = (i * slot + slot / 2 - w / 2).coerceIn(0f, size.width - w)
            val y = (barTop - h - 4.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(tooltipBg, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx()))
            drawText(text, topLeft = Offset(x + padX, y + padY))
        }

        if (target != null && target > 0) {
            val y = topPad + plotHeight - (target * scale).toFloat()
            drawLine(
                color = targetColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            val text = measurer.measure(Numbers.kcal(target, locale), labelStyle.copy(color = labelColor))
            drawText(text, topLeft = Offset(size.width - text.size.width, y - text.size.height - 2.dp.toPx()))
        }

        // Thin the labels: aim for at most ~6.
        val step = max(1, values.size / 6)
        labels.forEachIndexed { i, label ->
            if (i % step != 0 && i != values.lastIndex) return@forEachIndexed
            val text = measurer.measure(label, labelStyle.copy(color = labelColor))
            val x = (i * slot + slot / 2 - text.size.width / 2).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(x, size.height - labelHeight))
        }
    }
}

/** Time series line with points, plus an optional smoothed second series. */
@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    smoothed: List<Pair<Long, Double>>? = null,
    height: Dp = 200.dp,
    startLabel: String = "",
    endLabel: String = "",
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val smoothColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val locale = currentLocale()

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val labelHeight = 16.dp.toPx()
        val leftPad = 40.dp.toPx()
        val topPad = 8.dp.toPx()
        val plotW = size.width - leftPad
        val plotH = size.height - labelHeight - topPad

        val all = points + smoothed.orEmpty()
        val minX = all.minOf { it.first }
        val maxX = all.maxOf { it.first }
        val rawMin = all.minOf { it.second }
        val rawMax = all.maxOf { it.second }
        val pad = max((rawMax - rawMin) * 0.15, 0.5)
        val minY = rawMin - pad
        val maxY = rawMax + pad

        fun px(x: Long) = if (maxX == minX) leftPad + plotW / 2 else leftPad + ((x - minX).toDouble() / (maxX - minX) * plotW).toFloat()
        fun py(y: Double) = topPad + ((maxY - y) / (maxY - minY) * plotH).toFloat()

        // Horizontal guides at min/max with labels.
        listOf(rawMin, rawMax).distinct().forEach { v ->
            val y = py(v)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            val text = measurer.measure(Numbers.entered(v, locale), labelStyle.copy(color = labelColor))
            drawText(text, topLeft = Offset(0f, (y - text.size.height / 2).coerceIn(0f, size.height - text.size.height)))
        }

        smoothed?.takeIf { it.size > 1 }?.let { s ->
            val path = Path()
            s.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
            drawPath(path, smoothColor.copy(alpha = 0.6f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        }

        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        val dotRadius = if (points.size > 60) 1.5.dp.toPx() else 3.dp.toPx()
        points.forEach { (x, y) -> drawCircle(lineColor, radius = dotRadius, center = Offset(px(x), py(y))) }

        if (startLabel.isNotEmpty()) {
            val t = measurer.measure(startLabel, labelStyle.copy(color = labelColor))
            drawText(t, topLeft = Offset(leftPad, size.height - labelHeight))
        }
        if (endLabel.isNotEmpty()) {
            val t = measurer.measure(endLabel, labelStyle.copy(color = labelColor))
            drawText(t, topLeft = Offset(size.width - t.size.width, size.height - labelHeight))
        }
    }
}

/** Stacked horizontal bar of energy share with a legend. */
@Composable
fun MacroSplitBar(split: MacroSplit, proteinLabel: String, carbsLabel: String, fatLabel: String, modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    val parts = listOf(
        Triple(proteinLabel, split.proteinPct, colors[0]),
        Triple(carbsLabel, split.carbsPct, colors[1]),
        Triple(fatLabel, split.fatPct, colors[2]),
    )
    val locale = currentLocale()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
            parts.forEach { (_, pct, color) ->
                if (pct > 0) Box(Modifier.weight(pct.toFloat()).fillMaxWidth().height(16.dp).background(color))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            parts.forEach { (label, pct, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(4.dp))
                    Text("$label ${Numbers.format(pct, 0, locale)} %", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
