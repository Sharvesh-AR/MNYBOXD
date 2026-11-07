package io.github.sharveshar.spendly.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** One coin flight: where it starts and lands (root coordinates) and the label it carries. */
@Immutable
data class CoinFlightSpec(val from: Rect, val to: Rect, val label: String, val incoming: Boolean)

/** How far the coin has travelled (0 → 1) when it counts as landed; the spring then settles. */
private const val LANDED_AT = 0.97f

/**
 * A small coin that tumbles in 3D as it travels from [CoinFlightSpec.from] to [CoinFlightSpec.to],
 * carrying the amount, then calls [onLanded] once.
 *
 * Money going out (expenses, transfers) arcs over the top and drops onto the account; money
 * coming in (income, refunds) swoops underneath and rises into it, spinning the other way. A
 * soft shadow follows below, smaller and fainter the higher the coin is.
 *
 * It's decoration only: it plays after the transaction is saved and never decides any value. It
 * doesn't take touches. With reduced motion it lands immediately.
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
        var told = false
        // Spring travel: leaves briskly, eases in, and settles without overshooting the account.
        t.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 60f, visibilityThreshold = 0.002f)) {
            if (!told && value >= LANDED_AT) {
                told = true
                landed()
            }
        }
        if (!told) landed()
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .clearAndSetSemantics { },
    ) {
        if (spec == null || reduced) return@BoxWithConstraints
        val c = Spendly.colors
        val face = if (spec.incoming) c.positive else c.warning
        val start = spec.from.center - origin
        val end = spec.to.center - origin
        val lift = 150f
        // Out: an arc over the top. In: a swoop below that rises into the account.
        val control = if (spec.incoming) {
            Offset((start.x + end.x) / 2f + 60f, maxOf(start.y, end.y) + lift)
        } else {
            Offset((start.x + end.x) / 2f, minOf(start.y, end.y) - lift)
        }
        val spin = if (spec.incoming) -540f else 540f
        fun pathAt(p: Float): Offset {
            val q = 1f - p
            return start * (q * q) + control * (2 * q * p) + end * (p * p)
        }
        val shadowColor = Color.Black.copy(alpha = if (c.isDark) 0.45f else 0.22f)

        // Shadow on the "ground" (the straight line between start and end), below the coin.
        Canvas(Modifier.fillMaxSize()) {
            val p = t.value.coerceIn(0f, 1f)
            val pos = pathAt(p)
            val ground = start + (end - start) * p
            val height = abs(pos.y - ground.y).coerceAtMost(lift) / lift
            val coin = 46.dp.toPx()
            val w = coin * (0.9f - 0.35f * height)
            val alpha = (1f - 0.6f * height) * fade(p)
            drawOval(
                Brush.radialGradient(listOf(shadowColor.copy(alpha = shadowColor.alpha * alpha), Color.Transparent), center = Offset(pos.x, pos.y + coin * 0.62f + height * 18.dp.toPx()), radius = w / 2f),
                topLeft = Offset(pos.x - w / 2f, pos.y + coin * 0.62f + height * 18.dp.toPx() - w * 0.18f),
                size = Size(w, w * 0.36f),
            )
        }

        Box(
            Modifier.graphicsLayer {
                val p = t.value
                val pos = pathAt(p.coerceIn(0f, 1f))
                val coin = 46.dp.toPx()
                translationX = pos.x - coin / 2f
                translationY = pos.y - coin / 2f
                // Tumbles: spins edge-over-edge and tilts towards the viewer mid-flight.
                rotationY = spin * p.coerceIn(0f, 1f)
                rotationX = 22f * sin(PI.toFloat() * p.coerceIn(0f, 1f))
                cameraDistance = 10f * density
                val pop = if (p < 0.18f) 0.55f + 2.5f * p else 1f - 0.3f * ((p - 0.18f) / 0.82f).coerceIn(0f, 1f)
                scaleX = pop
                scaleY = pop
                alpha = fade(p)
            },
        ) {
            Canvas(Modifier.size(46.dp)) {
                val r = size.minDimension / 2f
                // Light catches the face as it turns towards the viewer; it darkens edge-on.
                val turn = abs(cos(Math.toRadians((spin * t.value).toDouble()))).toFloat()
                val faceColor = lerp(face.copy(alpha = 1f), Color.Black, 0.22f * (1f - turn))
                val rim = lerp(faceColor, Color.Black, 0.3f)
                drawCircle(rim, r)
                drawCircle(
                    Brush.radialGradient(listOf(lerp(faceColor, Color.White, 0.5f), faceColor, lerp(faceColor, Color.Black, 0.12f)), center = Offset(r * 0.65f, r * 0.55f), radius = r * 1.35f),
                    r * 0.9f,
                )
                drawCircle(lerp(faceColor, Color.Black, 0.25f), r * 0.7f, style = Stroke(width = r * 0.07f))
                // A thin specular arc, top left.
                drawArc(Color.White.copy(alpha = 0.55f * turn), 200f, 70f, false, Offset(r * 0.25f, r * 0.25f), Size(r * 1.5f, r * 1.5f), style = Stroke(width = r * 0.08f))
            }
            Text(
                "₹",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
                color = lerp(face, Color.Black, 0.58f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // The amount appears beside the figure it came from, like a tag, on its own pill so it
        // reads over anything, then drifts up and fades while the coin travels.
        var labelSize by remember { mutableStateOf(IntSize.Zero) }
        val maxX = constraints.maxWidth.toFloat()
        Text(
            spec.label,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
            color = if (spec.incoming) c.positive else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .onSizeChanged { labelSize = it }
                .graphicsLayer {
                    val p = t.value.coerceIn(0f, 1f)
                    val gap = 10.dp.toPx()
                    val edge = 8.dp.toPx()
                    val right = spec.from.right - origin.x + gap
                    translationX = if (right + labelSize.width <= maxX - edge) right else (spec.from.left - origin.x - gap - labelSize.width).coerceAtLeast(edge)
                    translationY = start.y - labelSize.height / 2f - 14.dp.toPx() * p
                    alpha = (1f - p * 1.7f).coerceIn(0f, 1f)
                }
                .background(c.cardElevated, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Fades the coin out over the last moment of its flight. */
private fun fade(p: Float) = if (p > 0.9f) ((1f - p) / 0.1f).coerceIn(0f, 1f) else 1f

/** Remembers where an element is on screen (root coordinates), for coin flights. */
fun Modifier.trackBounds(onBounds: (Rect) -> Unit): Modifier = onGloballyPositioned { onBounds(it.boundsInRoot()) }
