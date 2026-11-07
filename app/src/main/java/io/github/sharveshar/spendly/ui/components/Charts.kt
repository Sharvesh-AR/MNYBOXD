package io.github.sharveshar.spendly.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.ui.theme.AmountStyle
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly

@Composable
private fun rememberEntrance(key: Any?): Animatable<Float, *> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    return anim
}

/**
 * Simple vertical bars. Negative values (net refunds) are drawn as empty. [highlight] marks one
 * bar (for example today) in the accent colour; the rest stay muted.
 */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    height: Dp = 132.dp,
    showLabelEvery: Int = 1,
    description: String,
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
    val track = Spendly.colors.chartMuted.copy(alpha = 0.45f)
    val progress = rememberEntrance(values)
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(barW / 2.4f)
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius)
                val h = if (v <= 0) 0f else (v.toFloat() / max * size.height * progress.value).coerceAtLeast(3.dp.toPx())
                if (h > 0f) drawRoundRect(if (i == highlight) accent else muted, Offset(x, size.height - h), Size(barW, h), radius)
            }
        }
        Spacer(Modifier.height(Space.sm))
        // Labels keep their natural width and are centred under their bar, so "22" never clips.
        Layout(
            content = {
                labels.forEachIndexed { i, l ->
                    Text(
                        if (i == highlight || (i % showLabelEvery == 0 && (highlight == null || kotlin.math.abs(i - highlight) > 1))) l else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == highlight) MaterialTheme.colorScheme.onSurface else Spendly.colors.muted,
                        maxLines = 1,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val slot = width.toFloat() / labels.size.coerceAtLeast(1)
            val placeables = measurables.map { it.measure(Constraints()) }
            layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
                placeables.forEachIndexed { i, p ->
                    val x = (slot * i + slot / 2 - p.width / 2f).roundToInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                    p.place(x, 0)
                }
            }
        }
    }
}

/** Two bars per period: spending (muted) and income (accent). */
@Composable
fun PairedBarChart(
    spent: List<Long>,
    income: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    description: String,
) {
    val max = (spent + income).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val spendColor = Spendly.colors.negative.copy(alpha = 0.8f)
    val incomeColor = MaterialTheme.colorScheme.primary
    val progress = rememberEntrance(spent to income)
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = labels.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.26f).coerceAtMost(16.dp.toPx())
            val gap = 3.dp.toPx()
            val radius = CornerRadius(barW / 2.4f)
            for (i in 0 until n) {
                val cx = slot * i + slot / 2
                listOf(spent.getOrElse(i) { 0 } to spendColor, income.getOrElse(i) { 0 } to incomeColor).forEachIndexed { j, (v, c) ->
                    val h = if (v <= 0) 0f else (v.toFloat() / max * size.height * progress.value).coerceAtLeast(3.dp.toPx())
                    val x = if (j == 0) cx - gap / 2 - barW else cx + gap / 2
                    if (h > 0) drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), radius)
                }
            }
            drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            LegendDot(spendColor, "Spent")
            LegendDot(incomeColor, "Income")
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(Space.xs + Space.xxs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
    }
}

data class Slice(val label: String, val value: Long, val color: Color)

/** Donut with thin rounded arcs and a small gap between slices. */
@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, size: Dp = 148.dp, centerLabel: String, centerValue: String) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val progress = rememberEntrance(slices.map { it.value })
    val track = Spendly.colors.chartMuted.copy(alpha = 0.5f)
    Box(modifier.size(size).clearAndSetSemantics {
        contentDescription = "$centerLabel $centerValue. " + slices.joinToString { "${it.label} ${it.value * 100 / total} percent" }
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            val gapDeg = if (slices.size > 1) 3f else 0f
            slices.forEach { s ->
                val sweep = 360f * s.value / total * progress.value
                if (sweep > gapDeg) {
                    drawArc(s.color, start + gapDeg / 2, sweep - gapDeg, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Space.xl)) {
            Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted)
            Text(centerValue, style = AmountStyle.compact, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Horizontal proportion row used for category and payment-method breakdowns. */
@Composable
fun ProportionRow(
    label: String,
    value: Long,
    total: Long,
    color: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val fraction = if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)
    val progress = rememberEntrance(value)
    Row(modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Space.sm))
                Text(Money.format(value), style = AmountStyle.compact)
                Spacer(Modifier.width(Space.sm))
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
            }
            Spacer(Modifier.height(Space.xs + Space.xxs))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Spendly.colors.chartMuted.copy(alpha = 0.6f))) {
                Box(Modifier.fillMaxWidth(fraction * progress.value).height(4.dp).clip(CircleShape).background(color))
            }
        }
    }
}

fun compactAxisDescription(title: String, labels: List<String>, values: List<Long>): String =
    title + ": " + labels.zip(values).joinToString { (l, v) -> "$l ${Money.format(v)}" }
