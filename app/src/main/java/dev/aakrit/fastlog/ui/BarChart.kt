package dev.aakrit.fastlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aakrit.fastlog.data.ChartSeries
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * Bar chart of hours fasted per day. Drawn by hand on a Canvas rather than
 * pulling in a charting library, which keeps the dependency list (and the
 * app's permissions surface) unchanged.
 */
@Composable
fun FastingBarChart(
    series: ChartSeries,
    selectedIndex: Int?,
    onSelectBar: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    // Round the top of the axis up to a clean multiple so gridlines read well.
    val axisMax = remember2(series.maxHours) {
        val raw = maxOf(series.maxHours, 4.0)
        (ceil(raw / 6.0) * 6.0).coerceAtLeast(6.0)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(series.buckets.size) {
                detectTapGestures { offset ->
                    val leftPad = with(density) { 34.dp.toPx() }
                    val plotWidth = size.width - leftPad
                    if (plotWidth <= 0f || series.buckets.isEmpty()) return@detectTapGestures
                    val slot = plotWidth / series.buckets.size
                    val index = ((offset.x - leftPad) / slot).toInt()
                    onSelectBar(
                        if (index in series.buckets.indices && index == selectedIndex) null
                        else index.takeIf { it in series.buckets.indices }
                    )
                }
            }
    ) {
        val leftPad = 34.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - bottomPad
        if (plotWidth <= 0f || plotHeight <= 0f || series.buckets.isEmpty()) return@Canvas

        val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

        // Horizontal gridlines + Y labels, every 6 hours.
        val steps = (axisMax / 6.0).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val value = i * 6.0
            val y = plotHeight - (value / axisMax * plotHeight).toFloat()
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            drawTextSafe(measurer, "${value.toInt()}h", labelStyle, Offset(0f, y - 6.dp.toPx()))
        }

        // Dashed average line, only once there is something to average.
        if (series.averageHours > 0.0) {
            val y = plotHeight - (series.averageHours / axisMax * plotHeight).toFloat()
            drawLine(
                color = selectedColor,
                start = Offset(leftPad, y),
                end = Offset(size.width, y),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        // Bars.
        val slot = plotWidth / series.buckets.size
        val barWidth = (slot * 0.62f).coerceAtMost(26.dp.toPx())
        series.buckets.forEachIndexed { index, bucket ->
            if (bucket.hours <= 0.0) return@forEachIndexed
            val barHeight = (bucket.hours / axisMax * plotHeight).toFloat()
            val left = leftPad + slot * index + (slot - barWidth) / 2f
            drawRoundRect(
                color = if (index == selectedIndex) selectedColor else barColor,
                topLeft = Offset(left, plotHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
        }

        // X labels: thin them out so they never collide.
        val every = ceil(series.buckets.size / 7.0).toInt().coerceAtLeast(1)
        val dayFmt = DateTimeFormatter.ofPattern(
            if (series.buckets.size > 14) "d/M" else "EEE",
            Locale.getDefault(),
        )
        series.buckets.forEachIndexed { index, bucket ->
            if (index % every != 0) return@forEachIndexed
            val x = leftPad + slot * index + slot / 2f
            drawTextSafe(
                measurer,
                bucket.date.format(dayFmt),
                labelStyle,
                Offset(x - 12.dp.toPx(), plotHeight + 5.dp.toPx()),
            )
        }
    }
}

/** Text drawing can throw on odd layout passes; a chart is never worth a crash. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextSafe(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    topLeft: Offset,
) {
    runCatching { drawText(measurer, text, topLeft = topLeft, style = style) }
}

@Composable
private fun <T, R> remember2(key: T, block: () -> R): R =
    androidx.compose.runtime.remember(key) { block() }
