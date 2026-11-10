// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.ui.theme.AmountStyle
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly

/**
 * Chart values on screen. The first time, they grow from zero; after that, a data change morphs
 * each bar or slice from what is currently drawn to its new value (matched by [keys]), so a chart
 * never collapses and regrows when one transaction changes. With reduced motion values jump.
 * Read [at] while drawing, so only the canvas redraws during the animation.
 */
@Stable
private class ChartMorph(keys: List<Any>, targets: List<Float>) {
    var keys by mutableStateOf(keys)
    var from by mutableStateOf(List(targets.size) { 0f })
    var to by mutableStateOf(targets)
    val t = Animatable(0f)

    fun at(i: Int): Float {
        val a = from.getOrElse(i) { 0f }
        return a + (to.getOrElse(i) { 0f } - a) * t.value
    }

    /** Starts the next morph from exactly what is drawn now, even mid-animation. */
    fun retarget(newKeys: List<Any>, newTargets: List<Float>) {
        val drawn = keys.indices.associate { keys[it] to at(it) }
        from = newKeys.map { drawn[it] ?: 0f }
        keys = newKeys
        to = newTargets
    }
}

@Composable
private fun rememberChartMorph(targets: List<Float>, keys: List<Any> = targets.indices.toList()): ChartMorph {
    val reduced = LocalReducedMotion.current
    val morph = remember { ChartMorph(keys, targets) }
    LaunchedEffect(keys, targets) {
        if (morph.keys != keys || morph.to != targets) {
            morph.retarget(keys, targets)
            morph.t.snapTo(0f)
        }
        if (reduced) morph.t.snapTo(1f) else morph.t.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    return morph
}

private fun List<Long>.fractionsOf(max: Long): List<Float> = map { if (it <= 0) 0f else it.toFloat() / max }

/**
 * The faint line behind the balance on Home: spending so far this month as one smooth rising
 * stroke, edge to edge, with a soft glow and a bright point at the latest day. Decoration with
 * meaning; it never carries a figure the screen doesn't state in words, and glides to new values.
 */
@Composable
fun FlowLine(runningTotals: List<Long>, description: String, modifier: Modifier = Modifier) {
    val values = if (runningTotals.size >= 2) runningTotals else List(2) { runningTotals.firstOrNull() ?: 0L }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val morph = rememberChartMorph(values.fractionsOf(max))
    val line = Spendly.colors.accent
    Canvas(modifier.clearAndSetSemantics { contentDescription = description }) {
        val n = values.size
        val top = size.height * 0.12f
        val usable = size.height * 0.76f
        // A gentle floor so an empty month is a quiet horizon, not a line on the edge.
        // Runs off the left edge, but stops short of the right so the latest-day point shows whole.
        val right = size.width - 14.dp.toPx()
        val points = (0 until n).map { i ->
            val f = morph.at(i).coerceIn(0f, 1f)
            Offset(right * i / (n - 1), top + usable * (1f - (0.08f + 0.92f * f)))
        }
        val curve = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until n) {
                val a = points[i - 1]
                val b = points[i]
                val mid = (a.x + b.x) / 2f
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        val fade = Brush.horizontalGradient(listOf(line.copy(alpha = 0f), line.copy(alpha = 0.10f), line.copy(alpha = 0.55f)))
        // Glow under a hairline.
        drawPath(curve, Brush.horizontalGradient(listOf(line.copy(alpha = 0f), line.copy(alpha = 0.05f), line.copy(alpha = 0.16f))), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
        drawPath(curve, fade, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
        val end = points.last()
        drawCircle(line.copy(alpha = 0.18f), 7.dp.toPx(), end)
        drawCircle(line.copy(alpha = 0.9f), 2.5.dp.toPx(), end)
    }
}

/**
 * A quiet line for recent spending: a smooth curve with a soft fade beneath it and a dot for the
 * latest day, no axes or labels (the description carries the figures). Glides to new values like
 * the other charts.
 */
@Composable
fun Sparkline(values: List<Long>, description: String, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val morph = rememberChartMorph(values.fractionsOf(max))
    val line = Spendly.colors.accent
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description }) {
        val n = values.size
        if (n < 2) return@Canvas
        val pad = 4.dp.toPx()
        val usable = size.height - pad * 2
        val points = (0 until n).map { i -> Offset(size.width * i / (n - 1), pad + usable * (1f - morph.at(i).coerceIn(0f, 1f))) }
        val curve = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until n) {
                val a = points[i - 1]
                val b = points[i]
                val mid = (a.x + b.x) / 2f
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        val under = Path().apply {
            addPath(curve)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(under, Brush.verticalGradient(listOf(line.copy(alpha = 0.10f), Color.Transparent)))
        drawPath(curve, Brush.horizontalGradient(listOf(line.copy(alpha = 0.12f), line.copy(alpha = 0.85f))), style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(line, 3.dp.toPx(), points.last())
    }
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
    val morph = rememberChartMorph(values.fractionsOf(max))
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(barW / 2.4f)
            values.indices.forEach { i ->
                val x = slot * i + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius)
                val f = morph.at(i)
                val h = if (f <= 0.0001f) 0f else (f * size.height).coerceAtLeast(3.dp.toPx())
                if (h > 0f) drawRoundRect(if (i == highlight) accent else muted, Offset(x, size.height - h), Size(barW, h), radius)
            }
        }
        Spacer(Modifier.height(Space.sm))
        // Labels keep their natural width and are centred under their bar, so "22" never clips.
        Layout(
            content = {
                labels.forEachIndexed { i, l ->
                    Text(
                        if (i == highlight || (i % showLabelEvery == 0 && (highlight == null || showLabelEvery == 1 || kotlin.math.abs(i - highlight) > 1))) l else "",
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
    val n = labels.size
    val morph = rememberChartMorph(List(n) { spent.getOrElse(it) { 0L } }.fractionsOf(max) + List(n) { income.getOrElse(it) { 0L } }.fractionsOf(max))
    val axis = Spendly.colors.hairline
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / n.coerceAtLeast(1)
            val barW = (slot * 0.26f).coerceAtMost(16.dp.toPx())
            val gap = 3.dp.toPx()
            val radius = CornerRadius(barW / 2.4f)
            for (i in 0 until n) {
                val cx = slot * i + slot / 2
                listOf(morph.at(i) to spendColor, morph.at(n + i) to incomeColor).forEachIndexed { j, (f, c) ->
                    val h = if (f <= 0.0001f) 0f else (f * size.height).coerceAtLeast(3.dp.toPx())
                    val x = if (j == 0) cx - gap / 2 - barW else cx + gap / 2
                    if (h > 0) drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), radius)
                }
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
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

/**
 * Donut with a small gap between slices. Tap a slice (or pass [selected]) to bring it forward:
 * it thickens, the others fade back, and the centre names it with its amount and share. Tap it
 * again, or the centre, to go back to the total.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    centerLabel: String,
    centerValue: String,
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val morph = rememberChartMorph(slices.map { it.value.toFloat() / total }, slices.map { it.label })
    val track = Spendly.colors.chartMuted.copy(alpha = 0.5f)
    val reduced = LocalReducedMotion.current
    // Each slice's prominence: 1 = normal or focused, lower = faded behind the focused one.
    val focus = slices.indices.map { i ->
        androidx.compose.animation.core.animateFloatAsState(
            if (selected == null || selected == i) 1f else 0.28f,
            if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "slice$i",
        )
    }
    val grow = slices.indices.map { i ->
        androidx.compose.animation.core.animateFloatAsState(
            if (selected == i) 1f else 0f,
            if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f),
            label = "sliceGrow$i",
        )
    }
    val tap by androidx.compose.runtime.rememberUpdatedState(onSelect)
    val sel by androidx.compose.runtime.rememberUpdatedState(selected)
    val shown = selected?.let { slices.getOrNull(it) }
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics {
                contentDescription = "$centerLabel $centerValue. " + slices.joinToString { "${it.label} ${it.value * 100 / total} percent" }
            }
            .then(
                if (onSelect == null) Modifier else Modifier.pointerInput(slices.size) {
                    detectTapGestures { pos ->
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        val dx = pos.x - c.x
                        val dy = pos.y - c.y
                        val r = kotlin.math.sqrt(dx * dx + dy * dy)
                        val outer = this.size.width / 2f
                        if (r < outer * 0.55f) { tap?.invoke(null); return@detectTapGestures }
                        // Angle clockwise from 12 o'clock, matching how slices are drawn.
                        var deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (deg < 0f) deg += 360f
                        var acc = 0f
                        var hit: Int? = null
                        for (i in slices.indices) {
                            acc += 360f * morph.at(i)
                            if (deg <= acc) { hit = i; break }
                        }
                        tap?.invoke(if (hit == sel) null else hit)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 16.dp.toPx()
            val extra = 6.dp.toPx()
            val inset = stroke / 2 + extra / 2
            val arcSize = Size(this.size.width - stroke - extra, this.size.height - stroke - extra)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            val gapDeg = if (slices.size > 1) 2.5f else 0f
            slices.forEachIndexed { i, s ->
                val sweep = 360f * morph.at(i)
                if (sweep > gapDeg) {
                    val w = stroke + extra * grow[i].value
                    drawArc(
                        s.color.copy(alpha = focus[i].value),
                        start + gapDeg / 2, sweep - gapDeg, false, Offset(inset, inset), arcSize,
                        style = Stroke(w, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        androidx.compose.animation.AnimatedContent(
            targetState = shown,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.scaleIn(tween(220), initialScale = 0.9f))
                    .togetherWith(androidx.compose.animation.fadeOut(tween(120)))
            },
            label = "donutCentre",
        ) { slice ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 34.dp)) {
                if (slice == null) {
                    Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted)
                    Text(centerValue, style = AmountStyle.summary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(slice.label, style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(Money.formatCompact(slice.value), style = AmountStyle.summary, maxLines = 1)
                    Text("${slice.value * 100 / total}%", style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
                }
            }
        }
    }
}

/**
 * Small day bars for a recap: one per day, today (or [highlight]) in full ink, other spent days
 * soft, days still to come as faint stubs. Labels are optional (a week shows M T W...).
 */
@Composable
fun RecapBars(
    values: List<Long>,
    elapsed: Int,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    labels: List<String>? = null,
    height: Dp = 56.dp,
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val morph = rememberChartMorph(values.fractionsOf(max))
    val ink = MaterialTheme.colorScheme.onSurface
    val soft = Spendly.colors.muted.copy(alpha = 0.55f)
    val future = Spendly.colors.chartMuted
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * (if (n > 10) 0.62f else 0.5f)).coerceAtMost(22.dp.toPx())
            val radius = CornerRadius(barW / 2f)
            val stub = 3.dp.toPx()
            values.indices.forEach { i ->
                val x = slot * i + (slot - barW) / 2
                val f = morph.at(i).coerceIn(0f, 1f)
                val h = if (i >= elapsed || f <= 0.0001f) stub else (f * size.height).coerceAtLeast(stub)
                val color = when {
                    i >= elapsed -> future
                    i == highlight -> ink
                    f <= 0.0001f -> future
                    else -> soft
                }
                drawRoundRect(color, Offset(x, size.height - h), Size(barW, h), radius)
            }
        }
        if (labels != null) {
            Spacer(Modifier.height(Space.xs))
            // Labels keep their natural width, centred under their bar, so "22" never clips.
            Layout(
                content = {
                    labels.forEachIndexed { i, l ->
                        Text(
                            l,
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
    /** False while another row is focused: this one fades back. */
    emphasised: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val fraction = if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)
    val morph = rememberChartMorph(listOf(fraction))
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (emphasised) 1f else 0.4f, label = "rowFocus")
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .drawBehind {
                            val w = size.width * morph.at(0).coerceIn(0f, 1f)
                            if (w > 0f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
                        },
                )
            }
        }
    }
}

fun compactAxisDescription(title: String, labels: List<String>, values: List<Long>): String =
    title + ": " + labels.zip(values).joinToString { (l, v) -> "$l ${Money.format(v)}" }
