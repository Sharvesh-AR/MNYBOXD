// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sharveshar.mnyboxd.data.db.TransactionRow
import io.github.sharveshar.mnyboxd.domain.insights.BudgetLevel
import io.github.sharveshar.mnyboxd.domain.model.AccountType
import io.github.sharveshar.mnyboxd.domain.model.TransactionType
import io.github.sharveshar.mnyboxd.ui.components.AmountPill
import io.github.sharveshar.mnyboxd.ui.components.BudgetBar
import io.github.sharveshar.mnyboxd.ui.components.DonutChart
import io.github.sharveshar.mnyboxd.ui.components.FlowLine
import io.github.sharveshar.mnyboxd.ui.components.LegendDot
import io.github.sharveshar.mnyboxd.ui.components.LocalReducedMotion
import io.github.sharveshar.mnyboxd.ui.components.Slice
import io.github.sharveshar.mnyboxd.ui.components.TransactionListItem
import io.github.sharveshar.mnyboxd.ui.components.Wordmark
import io.github.sharveshar.mnyboxd.ui.components.budgetColor
import io.github.sharveshar.mnyboxd.ui.theme.Mnyboxd
import io.github.sharveshar.mnyboxd.ui.theme.Space
import kotlinx.coroutines.delay

private class TourStep(val title: String, val body: String, val visual: @Composable () -> Unit)

private val steps = listOf(
    TourStep("Your money. Your control.", "A simpler way to understand where your money goes.") { BrandVisual() },
    TourStep("Everything at a glance.", "See your balance, daily spending and monthly progress in one place.") { HomeVisual() },
    TourStep("Track it in seconds.", "Record an expense or income quickly and keep your finances up to date.") { QuickAddVisual() },
    TourStep("Stay within your limits.", "Set budgets, track your spending and understand how much you have left.") { BudgetsVisual() },
    TourStep("Understand your habits.", "Explore your spending patterns and make smarter financial decisions.") { InsightsVisual() },
)

/**
 * The first-time tour: five short steps over MNYBOXD's real components, shown once after setup.
 * [onDone] is called by Skip and Get started alike; Android Back only goes back a step (on the
 * first step it leaves the app without finishing the tour, so it shows again next time).
 */
@Composable
fun OnboardingTour(onDone: () -> Unit) {
    val reduced = LocalReducedMotion.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    // Ignores a second tap while a step is still sliding in (timed on the animation clock).
    var settled by remember { mutableStateOf(true) }
    LaunchedEffect(step) {
        delay(300)
        settled = true
    }
    fun go(to: Int) {
        if (!settled) return
        settled = false
        step = to.coerceIn(0, steps.lastIndex)
    }
    BackHandler(enabled = step > 0) { go(step - 1) }
    val last = step == steps.lastIndex
    val c = Mnyboxd.colors

    // Its own surface: the theme's background and text colour, whatever sits underneath.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.xxl),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            StepIndicator(step, steps.size, Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Skip", color = c.muted) }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                if (reduced) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> if (forward) w / 5 else -w / 5 } + fadeIn(tween(260, delayMillis = 60)))
                        .togetherWith(slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { w -> if (forward) -w / 5 else w / 5 } + fadeOut(tween(180)))
                }
            },
            label = "tour",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            val s = steps[index]
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // The feature itself settles in, slightly after its words.
                    val settle = remember { Animatable(if (reduced) 1f else 0.94f) }
                    LaunchedEffect(Unit) { if (!reduced) settle.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
                    Box(Modifier.graphicsLayer { scaleX = settle.value; scaleY = settle.value }) { s.visual() }
                }
                Text(
                    s.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Step ${index + 1} of ${steps.size}. ${s.title}"
                    },
                )
                Spacer(Modifier.height(Space.md))
                Text(s.body, style = MaterialTheme.typography.bodyLarge, color = c.muted, modifier = Modifier.heightIn(min = 48.dp))
            }
        }

        Spacer(Modifier.height(Space.xxl))
        Row(Modifier.fillMaxWidth().padding(bottom = Space.xl), verticalAlignment = Alignment.CenterVertically) {
            // Back keeps its place even when hidden, so Next never jumps.
            val backAlpha by animateFloatAsState(if (step > 0) 1f else 0f, if (reduced) tween(0) else tween(200), label = "back")
            Box(Modifier.widthIn(min = 88.dp)) {
                if (backAlpha > 0f) {
                    TextButton(onClick = { go(step - 1) }, enabled = step > 0, modifier = Modifier.graphicsLayer { alpha = backAlpha }) {
                        Text("Back", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (last) onDone() else go(step + 1) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(52.dp),
            ) {
                AnimatedContent(targetState = last, label = "nextLabel") { isLast ->
                    Text(if (isLast) "Get Started" else "Next", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    }
}

/** Five short bars; the current one is longer and solid. */
@Composable
private fun StepIndicator(step: Int, count: Int, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    Row(modifier.semantics { contentDescription = "Step ${step + 1} of $count" }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == step) 22.dp else 8.dp, if (reduced) tween(0) else tween(280, easing = FastOutSlowInEasing), label = "dot")
            val alpha by animateFloatAsState(if (i <= step) 1f else 0.25f, if (reduced) tween(0) else tween(280), label = "dotAlpha")
            Box(Modifier.width(width).height(4.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(MaterialTheme.colorScheme.onBackground))
        }
    }
}

// --- The visuals: real MNYBOXD components with sample figures ---------------------------------

@Composable
private fun BrandVisual() {
    val reduced = LocalReducedMotion.current
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Wordmark(
        Modifier.graphicsLayer {
            val s = 1.7f + 0.15f * (1f - reveal.value)
            scaleX = s
            scaleY = s
            alpha = reveal.value
            translationY = 12.dp.toPx() * (1f - reveal.value)
        },
    )
}

/** Home in miniature: the month, the balance, today's spending and the month's line with the +. */
@Composable
private fun HomeVisual() {
    val c = Mnyboxd.colors
    PreviewFrame {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("November", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "₹2,48,560",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-1.4).sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.sm))
            AmountPill("↓ −₹345 today")
            Box(Modifier.fillMaxWidth().height(116.dp)) {
                FlowLine(
                    runningTotals = listOf(0L, 1_200_00, 1_850_00, 4_100_00, 5_300_00, 6_100_00, 8_900_00, 9_400_00, 11_800_00, 12_600_00, 14_900_00, 16_200_00),
                    description = "Spending so far this month",
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                )
                AddDisc(Modifier.align(Alignment.BottomCenter), size = 48.dp)
            }
        }
    }
}

/** The + and what it leaves behind: a new expense in the list. */
@Composable
private fun QuickAddVisual() {
    val reduced = LocalReducedMotion.current
    val press = remember { Animatable(1f) }
    val row = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        press.animateTo(0.9f, tween(140, delayMillis = 450))
        press.animateTo(1f, tween(220))
        row.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AddDisc(Modifier.graphicsLayer { scaleX = press.value; scaleY = press.value }, size = 72.dp)
        Spacer(Modifier.height(Space.xxl))
        PreviewFrame(Modifier.graphicsLayer { alpha = row.value; translationY = 16.dp.toPx() * (1f - row.value) }) {
            TransactionListItem(row = sampleRow, onClick = {}, modifier = Modifier.padding(vertical = Space.xs), showTime = false)
        }
    }
}

@Composable
private fun BudgetsVisual() {
    val reduced = LocalReducedMotion.current
    val fill = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) fill.animateTo(1f, tween(700, delayMillis = 150, easing = FastOutSlowInEasing)) }
    val rows = listOf(
        Triple("Food", "₹4,200 of ₹6,000", 0.70f to BudgetLevel.OK),
        Triple("Travel", "₹1,850 of ₹2,000", 0.925f to BudgetLevel.WARNING),
        Triple("Shopping", "₹3,400 of ₹3,000", 1f to BudgetLevel.EXCEEDED),
    )
    PreviewFrame {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            rows.forEach { (name, figures, bar) ->
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text(figures, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = if (bar.second == BudgetLevel.OK) Mnyboxd.colors.muted else budgetColor(bar.second))
                    }
                    Spacer(Modifier.height(Space.sm))
                    BudgetBar(fraction = bar.first * fill.value, level = bar.second, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun InsightsVisual() {
    val chart = Mnyboxd.colors.palette.chart
    val slices = listOf(
        Slice("Food", 9_800_00, chart[0 % chart.size]),
        Slice("Travel", 6_200_00, chart[1 % chart.size]),
        Slice("Bills", 4_900_00, chart[2 % chart.size]),
        Slice("Shopping", 3_400_00, chart[3 % chart.size]),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DonutChart(slices = slices, size = 176.dp, centerLabel = "This month", centerValue = "₹24,300")
        Spacer(Modifier.height(Space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            slices.forEach { LegendDot(it.color, it.label) }
        }
    }
}

/** A quiet frame for a piece of the app: hairline edge, the app's card colour. */
@Composable
private fun PreviewFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Mnyboxd.colors
    Box(
        modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(24.dp)),
    ) { content() }
}

/** MNYBOXD's + as it rests on Home. */
@Composable
private fun AddDisc(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp) {
    val c = Mnyboxd.colors
    Box(modifier.size(size).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(size * 0.44f))
    }
}

private val sampleRow = TransactionRow(
    id = 1, type = TransactionType.EXPENSE, amountPaise = 120_00, description = "Tea and snacks",
    occurredAt = 0L, channel = null, provider = null, accountId = 1, toAccountId = null, categoryId = 1,
    refundOfId = null, instrumentAccountId = null, accountName = "Cash", accountType = AccountType.CASH,
    toAccountName = null, instrumentName = null, categoryName = "Food", categoryIcon = "tea", categoryColor = null,
)
