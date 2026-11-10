// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.sharveshar.spendly.core.money.Money
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Pure display helper: the amount shown at [fraction] of the way from [from] to [to]. Integer
 * paise throughout; at fraction 1 it is exactly [to], so an animation always lands on the real,
 * saved value.
 */
fun interpolatePaise(from: Long, to: Long, fraction: Float): Long = when {
    fraction >= 1f -> to
    fraction <= 0f -> from
    else -> {
        val value = from + ((to - from).toDouble() * fraction).roundToLong()
        // Between two whole-rupee amounts, count in whole rupees so no stray paise flicker past.
        if (from % 100 == 0L && to % 100 == 0L) (value.toDouble() / 100).roundToLong() * 100 else value
    }
}

/**
 * An amount that counts smoothly to each new value (balances, totals). Only the displayed text
 * animates; screen readers always get the exact final [paise].
 *
 * @param countUpOnFirstShow animate from zero the first time it appears (dashboard hero numbers).
 * @param hold keep showing the previous value until false (e.g. while a coin is still flying to
 *   this balance), then count to the latest value.
 */
@Composable
fun AnimatedMoney(
    paise: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    countUpOnFirstShow: Boolean = false,
    hold: Boolean = false,
    prefix: String = "",
    format: (Long) -> String = { Money.format(it) },
) {
    val reduced = LocalReducedMotion.current
    var from by remember { mutableLongStateOf(if (countUpOnFirstShow && !reduced) 0L else paise) }
    var to by remember { mutableLongStateOf(if (countUpOnFirstShow && !reduced) 0L else paise) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(paise, hold, reduced) {
        if (hold) return@LaunchedEffect
        if (reduced) {
            from = paise; to = paise; progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (paise == to && progress.value >= 1f) return@LaunchedEffect
        from = interpolatePaise(from, to, progress.value)
        to = paise
        progress.snapTo(0f)
        // Bigger jumps get a little longer, within limits, so small edits feel snappy.
        val duration = (420 + 60 * kotlin.math.log10(abs(to - from).toDouble().coerceAtLeast(1.0))).toInt().coerceIn(420, 900)
        progress.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
    }

    val shown = interpolatePaise(from, to, progress.value)
    Text(
        prefix + format(shown),
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        // Assistive tech and tests get the exact final amount, never an in-between frame.
        modifier = modifier.clearAndSetSemantics {
            contentDescription = spokenAmount(paise)
            text = AnnotatedString(prefix + format(paise))
        },
    )
}
