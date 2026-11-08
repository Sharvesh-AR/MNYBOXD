package io.github.sharveshar.spendly.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.ui.theme.Spendly

/**
 * Spendly's mark: a light disc with a dark "S" drawn as two opposed arcs, like a coin turning.
 * Drawn, not an image, so it's crisp at any size and follows the theme.
 */
@Composable
fun SpendlyMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val disc = Spendly.colors.accent
    val ink = Spendly.colors.onAccent
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(disc, r)
        val stroke = r * 0.30f
        val arc = r * 0.92f
        // Upper bowl opens to the right, lower bowl to the left: an S.
        drawArc(ink, 100f, 230f, false, Offset(center.x - arc / 2f, center.y - arc * 0.98f), Size(arc, arc), style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(ink, -80f, 230f, false, Offset(center.x - arc / 2f, center.y - arc * 0.02f), Size(arc, arc), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
