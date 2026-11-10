// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.core.money.Money

/** One character of a formatted amount and the stable key it animates under. */
@Immutable
data class AmountSlot(val key: String, val char: Char)

/**
 * Splits a formatted amount ("−₹12,840.50") into slots keyed so that a digit keeps its key when
 * the number changes: rupee digits by their distance from the decimal point (units, tens, ...),
 * paise by position after it, and the sign and ₹ by position before the number. Grow from
 * ₹9,999 to ₹10,000 and the units digit is still the units digit, so only digits that really
 * changed move.
 */
fun amountSlots(text: String): List<AmountSlot> {
    val firstDigit = text.indexOfFirst { it.isDigit() }
    if (firstDigit < 0) return text.mapIndexed { i, c -> AmountSlot("p$i", c) }
    val dot = text.indexOf('.', firstDigit).let { if (it < 0) text.length else it }
    return text.mapIndexed { i, c ->
        val key = when {
            i < firstDigit -> "p${firstDigit - i}"
            i < dot -> "i${dot - 1 - i}"
            i == dot -> "dot"
            else -> "f${i - dot}"
        }
        AmountSlot(key, c)
    }
}

/**
 * A large amount that changes like a fine mechanical counter: only the digits that changed roll
 * (down when the amount falls, up when it rises), the units first and each place a beat later,
 * each on a faint tile while it moves. The ₹, the sign and the grouping commas never move, and
 * digits use tabular figures, so nothing shifts sideways. A rise counts up through the figures
 * in a few quick steps and briefly takes [riseColor].
 *
 * @param hold keep showing the previous amount until false (for example while Quick Add is still
 *   open over this screen), then roll to the latest value.
 */
@Composable
fun RollingAmount(
    paise: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    hold: Boolean = false,
    riseColor: Color? = null,
) {
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableLongStateOf(paise) }
    var rising by remember { mutableStateOf(false) }
    var counting by remember { mutableStateOf(false) }
    val tint = remember { Animatable(0f) }
    LaunchedEffect(paise, hold) {
        if (hold || paise == shown) return@LaunchedEffect
        val from = shown
        rising = paise > from
        if (reduced || !rising) {
            shown = paise
            return@LaunchedEffect
        }
        // Count up: a few quick steps through the figures, ending exactly on the saved amount.
        counting = true
        if (riseColor != null) tint.snapTo(1f)
        for (i in 1..COUNT_STEPS) {
            shown = interpolatePaise(from, paise, FastOutSlowInEasing.transform(i / COUNT_STEPS.toFloat()))
            delay(COUNT_STEP_MS)
        }
        shown = paise
        counting = false
        if (riseColor != null) tint.animateTo(0f, tween(900, delayMillis = 600))
    }
    val tile = io.github.sharveshar.spendly.ui.theme.Spendly.colors.cardElevated
    val tabular = style.copy(fontFeatureSettings = "tnum")
    val shownColor = if (riseColor != null && tint.value > 0f) androidx.compose.ui.graphics.lerp(color, riseColor, tint.value) else color
    Row(
        // Assistive tech and tests always get the exact, final amount.
        modifier.clearAndSetSemantics {
            contentDescription = spokenAmount(paise)
            text = AnnotatedString(Money.format(paise))
        },
    ) {
        amountSlots(Money.format(shown)).forEach { slot ->
            key(slot.key) {
                if (reduced || !slot.char.isDigit()) {
                    Text(slot.char.toString(), style = tabular, color = shownColor, maxLines = 1)
                } else {
                    val place = slot.key.drop(1).toIntOrNull() ?: 0
                    // A faint tile shows behind a digit while it changes, then fades.
                    val glow = remember { Animatable(0f) }
                    var last by remember { mutableStateOf(slot.char) }
                    LaunchedEffect(slot.char) {
                        if (slot.char != last) {
                            last = slot.char
                            glow.snapTo(1f)
                            glow.animateTo(0f, tween(700, delayMillis = if (counting) 300 else 450))
                        }
                    }
                    AnimatedContent(
                        targetState = slot.char,
                        modifier = Modifier.drawBehind {
                            if (glow.value > 0f) {
                                val inset = 1.dp.toPx()
                                drawRoundRect(
                                    tile.copy(alpha = tile.alpha * glow.value),
                                    topLeft = Offset(inset, size.height * 0.1f),
                                    size = Size(size.width - inset * 2, size.height * 0.8f),
                                    cornerRadius = CornerRadius(8.dp.toPx()),
                                )
                            }
                        },
                        transitionSpec = {
                            // Counting steps are quick; a single change takes its time, units first.
                            val duration = if (counting) 110 else 380
                            val delay = if (counting) 0 else (place * 45).coerceAtMost(240)
                            val spec = tween<androidx.compose.ui.unit.IntOffset>(duration, delayMillis = delay, easing = FastOutSlowInEasing)
                            val fade = tween<Float>((duration * 0.7f).toInt(), delayMillis = delay)
                            // Rising: the new digit comes up from below. Falling: down from above.
                            (slideInVertically(spec) { h -> if (rising) h else -h } + fadeIn(fade))
                                .togetherWith(slideOutVertically(spec) { h -> if (rising) -h else h } + fadeOut(fade))
                                .using(SizeTransform(clip = true))
                        },
                        contentAlignment = Alignment.Center,
                        label = "roll-${slot.key}",
                    ) { ch -> Text(ch.toString(), style = tabular, color = shownColor, maxLines = 1) }
                }
            }
        }
    }
}

private const val COUNT_STEPS = 12
private const val COUNT_STEP_MS = 55L

/**
 * The amount being typed in Quick Add. Each new character appears with a short fade and a slight
 * scale (never a bounce) on a faint tile that fades away; a caret shows where the next digit
 * goes. [empty] shows the muted "0" placeholder, which the first digit replaces the same way.
 */
@Composable
fun TypingAmount(text: String, empty: Boolean, style: TextStyle, color: Color, caretColor: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val tileColor = io.github.sharveshar.spendly.ui.theme.Spendly.colors.cardElevated
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            key(if (empty) "placeholder$i" else "typed$i") {
                val fresh = !reduced && !empty
                val appear = remember { Animatable(if (fresh) 0f else 1f) }
                val tile = remember { Animatable(if (fresh) 1f else 0f) }
                LaunchedEffect(Unit) {
                    if (fresh) {
                        launch { appear.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 900f)) }
                        tile.animateTo(0f, tween(520, delayMillis = 220))
                    }
                }
                Text(
                    ch.toString(),
                    style = style.copy(fontFeatureSettings = "tnum"),
                    color = color,
                    maxLines = 1,
                    modifier = Modifier
                        .drawBehind {
                            if (tile.value > 0f) {
                                drawRoundRect(
                                    tileColor.copy(alpha = tileColor.alpha * tile.value),
                                    topLeft = Offset(-2.dp.toPx(), size.height * 0.08f),
                                    size = Size(size.width + 4.dp.toPx(), size.height * 0.84f),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                )
                            }
                        }
                        .graphicsLayer {
                            val a = appear.value
                            alpha = a
                            val sc = 0.86f + 0.14f * a
                            scaleX = sc
                            scaleY = sc
                            translationY = (1f - a) * 6.dp.toPx()
                        },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Caret(height = with(LocalDensity.current) { style.fontSize.toDp() * 0.82f }, color = caretColor)
    }
}

/** A text caret that blinks gently (steady when animations are off). */
@Composable
fun Caret(height: androidx.compose.ui.unit.Dp, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    // Read in the draw phase only, so blinking never recomposes anything. No animation at all
    // (not even an invisible one) when animations are off.
    val alpha: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "caret")
            .animateFloat(1f, 0.15f, infiniteRepeatable(tween(560, delayMillis = 380), RepeatMode.Reverse), label = "caretAlpha")
    }
    Box(
        modifier
            .width(2.5.dp)
            .height(height)
            .graphicsLayer { this.alpha = alpha.value }
            .background(color, RoundedCornerShape(2.dp)),
    )
}

/** A small pill under a big amount, such as "↓ ₹420 spent today". */
@Composable
fun AmountPill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(io.github.sharveshar.spendly.ui.theme.Spendly.colors.card, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * [value], except while [hold] is true: then the last value seen before holding. Lets a screen
 * keep showing what it showed until, say, Quick Add closes, so the change plays where it can be
 * seen. Display only; the data underneath is always current.
 */
@Composable
fun <T> rememberHeld(value: T, hold: Boolean): T {
    val box = remember { arrayOf<Any?>(value) }
    if (!hold) box[0] = value
    @Suppress("UNCHECKED_CAST")
    return box[0] as T
}
