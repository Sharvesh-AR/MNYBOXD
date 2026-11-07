package io.github.sharveshar.spendly.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlin.math.abs
import kotlin.math.cos

/** One coin flight: where it starts and lands (root coordinates) and the label it carries. */
@Immutable
data class CoinFlightSpec(val from: Rect, val to: Rect, val label: String, val incoming: Boolean)

/**
 * A small coin that flips as it arcs from [CoinFlightSpec.from] to [CoinFlightSpec.to], carrying
 * the amount, then calls [onLanded]. It's decoration only: it plays after the transaction is
 * saved and never decides any value. With reduced motion it lands immediately.
 */
@Composable
fun CoinFlightOverlay(spec: CoinFlightSpec?, onLanded: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val landed by rememberUpdatedState(onLanded)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val t = remember { Animatable(0f) }

    LaunchedEffect(spec) {
        if (spec == null) return@LaunchedEffect
        if (reduced) {
            landed()
            return@LaunchedEffect
        }
        t.snapTo(0f)
        t.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        landed()
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .clearAndSetSemantics { },
    ) {
        if (spec == null || reduced) return@Box
        val c = Spendly.colors
        val face = if (spec.incoming) c.positive else c.warning
        val start = spec.from.center - origin
        val end = spec.to.center - origin
        // Quadratic arc that rises above both points.
        val control = Offset((start.x + end.x) / 2f, minOf(start.y, end.y) - 140f)
        Box(
            Modifier.graphicsLayer {
                val p = t.value
                val q = 1f - p
                val pos = start * (q * q) + control * (2 * q * p) + end * (p * p)
                val coin = 50.dp.toPx()
                translationX = pos.x - coin / 2f
                translationY = pos.y - coin / 2f
                rotationY = 540f * p
                cameraDistance = 12f * density
                val pop = if (p < 0.2f) 0.6f + 2f * p else 1f - 0.35f * ((p - 0.2f) / 0.8f)
                scaleX = pop
                scaleY = pop
                alpha = if (p > 0.92f) (1f - p) / 0.08f else 1f
            },
        ) {
            Canvas(Modifier.size(50.dp)) {
                val r = size.minDimension / 2f
                // The edge shows when the coin turns side-on, like a real coin.
                val turn = abs(cos(Math.toRadians((540f * t.value).toDouble()))).toFloat()
                val faceColor = lerp(face.copy(alpha = 1f), Color.Black, 0.18f * (1f - turn))
                drawCircle(Brush.radialGradient(listOf(lerp(faceColor, Color.White, 0.45f), faceColor), center = Offset(r * 0.7f, r * 0.6f), radius = r * 1.3f), r)
                drawCircle(lerp(faceColor, Color.Black, 0.25f), r * 0.78f, style = Stroke(width = r * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.35f), r, style = Stroke(width = r * 0.06f))
            }
            Text(
                "₹",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                color = lerp(face, Color.Black, 0.55f),
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
            )
        }
        // The amount rises from where the coin started and fades as the coin lands.
        Text(
            spec.label,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = if (spec.incoming) c.positive else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                val p = t.value
                translationX = start.x - 40.dp.toPx()
                translationY = start.y - 48.dp.toPx() - 36.dp.toPx() * p
                alpha = (1f - p * 1.3f).coerceIn(0f, 1f)
            },
        )
    }
}

/** Remembers where an element is on screen (root coordinates), for coin flights. */
fun Modifier.trackBounds(onBounds: (Rect) -> Unit): Modifier = onGloballyPositioned { onBounds(it.boundsInRoot()) }
