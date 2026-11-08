package io.github.sharveshar.spendly.ui.components

import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shadow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

enum class MomentKind { EXPENSE, INCOME, TRANSFER, UPDATED }

/** What the confirmation after a save says and shows. Built by [momentFor]. */
@Immutable
data class MomentSpec(
    val kind: MomentKind,
    val title: String,
    /** "₹420 on Food", "₹2,000 to HDFC Savings", "₹1,000". */
    val headline: String,
    /** Quieter second line, such as the account an expense was paid from. */
    val detail: String? = null,
    val fromLabel: String? = null,
    val toLabel: String? = null,
)

/**
 * The words for the moment after a save, from what was actually saved. Pure, so it can be tested.
 */
fun momentFor(
    type: TransactionType,
    wasEdit: Boolean,
    amountPaise: Long,
    category: String?,
    account: String?,
    toAccount: String?,
): MomentSpec {
    val amount = Money.format(amountPaise)
    return when {
        wasEdit -> MomentSpec(MomentKind.UPDATED, "Changes Saved", amount, listOfNotNull(category, account).joinToString(" · ").ifEmpty { null })
        type == TransactionType.TRANSFER -> MomentSpec(
            MomentKind.TRANSFER, "Transfer Done!", amount,
            detail = if (account != null && toAccount != null) "$account → $toAccount" else null,
            fromLabel = account, toLabel = toAccount,
        )
        type == TransactionType.REFUND -> MomentSpec(MomentKind.INCOME, "Refund Recorded!", account?.let { "$amount back to $it" } ?: amount)
        type == TransactionType.INCOME -> MomentSpec(MomentKind.INCOME, "Income Added!", account?.let { "$amount to $it" } ?: amount, category)
        else -> MomentSpec(MomentKind.EXPENSE, "Expense Added!", category?.let { "$amount from $it" } ?: amount, account?.let { "Paid with $it" })
    }
}

/** Length of the whole moment, start to settled. */
const val MOMENT_MS = 1_000

/**
 * The confirmation after a transaction is saved. Expense: a small silver ₹ coin spins about its
 * vertical axis as it eases into place, a check appears, and the coin fades. Income: the coin
 * rises from lower down, with a small up arrow. Transfer: dots run from one account to the other.
 *
 * It only ever plays after the save has committed and never decides any value. [onCheck] fires
 * as the check appears (for the haptic), [onDone] when the moment has settled. With animations
 * off it shows the settled frame straight away. Everything animates in the draw phase.
 */
@Composable
fun MoneyMoment(spec: MomentSpec, onCheck: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier, alert: (@Composable () -> Unit)? = null) {
    val reduced = LocalReducedMotion.current
    // The settled frame: coin in place, check shown, before the coin fades.
    val settled = 0.8f
    val t = remember { Animatable(if (reduced) settled else 0f) }
    val check by rememberUpdatedState(onCheck)
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        if (reduced) {
            check()
            delay(650)
            done()
            return@LaunchedEffect
        }
        var checked = false
        t.animateTo(1f, tween(MOMENT_MS, easing = LinearEasing)) {
            if (!checked && value >= CHECK_AT) {
                checked = true
                check()
            }
        }
        if (!checked) check()
        done()
    }

    val c = Spendly.colors
    Column(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = listOfNotNull(spec.title, spec.headline, spec.detail).joinToString(". ")
            }
            .padding(horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(184.dp), contentAlignment = Alignment.Center) {
            when (spec.kind) {
                MomentKind.EXPENSE, MomentKind.INCOME -> CoinStage(t, rising = spec.kind == MomentKind.INCOME)
                MomentKind.TRANSFER -> TransferStage(t, spec)
                MomentKind.UPDATED -> Unit
            }
        }
        Spacer(Modifier.height(Space.lg))
        Text(
            spec.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { rise(phase(t.value, 0.3f, 0.55f)) },
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            spec.headline,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = if (spec.kind == MomentKind.INCOME) c.positive else c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { rise(phase(t.value, 0.36f, 0.6f)) },
        )
        spec.detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).graphicsLayer { rise(phase(t.value, 0.4f, 0.64f)) },
            )
        }
        if (alert != null) {
            Box(Modifier.padding(top = Space.md).graphicsLayer { rise(phase(t.value, 0.45f, 0.7f)) }) { alert() }
        }
        Spacer(Modifier.height(Space.xxl))
        // The check: a quiet charcoal disc that scales in once the money has moved.
        Box(
            Modifier
                .size(56.dp)
                .graphicsLayer {
                    val p = FastOutSlowInEasing.transform(phase(t.value, CHECK_AT, CHECK_AT + 0.22f))
                    alpha = p
                    val s = 0.7f + 0.3f * p
                    scaleX = s
                    scaleY = s
                }
                .background(c.cardElevated, CircleShape)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
        }
    }
}

private const val CHECK_AT = 0.5f

/** 0 before [start], 1 after [end], linear in between. */
internal fun phase(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)

/** Fades in and drifts up a few pixels as [p] goes from 0 to 1. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.rise(p: Float) {
    val e = FastOutSlowInEasing.transform(p)
    alpha = e
    translationY = (1f - e) * 10.dp.toPx()
}

private val SilverLight = Color(0xFFF7F7FA)
private val SilverMid = Color(0xFFC4C4CA)
private val SilverDark = Color(0xFF8A8A91)
private val Engraving = Color(0xFF5A5A61)

/** Spin of the coin in degrees at time [t]: brisk at first, easing to a stop face-on. */
private fun spinAt(t: Float, rising: Boolean): Float =
    (if (rising) -720f else 720f) * FastOutSlowInEasing.transform(phase(t, 0f, 0.7f))

@Composable
private fun CoinStage(t: Animatable<Float, *>, rising: Boolean) {
    val c = Spendly.colors
    val shadow = if (c.isDark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.18f)
    val ring = c.accent
    val measurer = rememberTextMeasurer()
    val glyphStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp, fontWeight = FontWeight.Bold)
    val glyph = remember(glyphStyle) { measurer.measure("₹", glyphStyle) }
    val glyphLight = remember(glyphStyle) { measurer.measure("₹", glyphStyle.copy(color = Color.White.copy(alpha = 0.75f))) }
    Canvas(
        Modifier
            .size(200.dp)
            .graphicsLayer {
                val p = t.value
                val move = FastOutSlowInEasing.transform(phase(p, 0f, 0.55f))
                val away = phase(p, 0.85f, 1f)
                // Eases into place from a little lower (income comes up from further down).
                translationY = (1f - move) * (if (rising) 70.dp else 30.dp).toPx()
                val sc = (0.74f + 0.26f * move) * (1f - 0.08f * away)
                scaleX = sc
                scaleY = sc
                alpha = phase(p, 0f, 0.12f) * (1f - away)
            },
    ) {
        val p = t.value
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.29f
        val thickness = r * 0.17f
        val theta = spinAt(p, rising) * PI.toFloat() / 180f
        val cosT = cos(theta)
        val sinT = sin(theta)
        val rx = r * abs(cosT)
        // The face turned towards you, and the one behind it: the coin's edge spans between.
        val shift = thickness / 2f * sinT * (if (cosT >= 0f) 1f else -1f)
        val front = Offset(center.x + shift, center.y)
        val back = Offset(center.x - shift, center.y)

        // Orbit: a thin ring tilted around the coin. Its far half passes behind the coin.
        val ringAlpha = phase(p, 0.05f, 0.25f) * (1f - phase(p, 0.72f, 0.95f))
        val ringW = size.width * 0.9f
        val ringH = size.height * 0.27f
        val ringTopLeft = Offset(center.x - ringW / 2f, center.y - ringH / 2f + r * 0.18f)
        fun ringHalf(start: Float, alpha: Float) = rotate(-17f, center) {
            drawArc(ring.copy(alpha = alpha * ringAlpha), start, 180f * FastOutSlowInEasing.transform(phase(p, 0.05f, 0.55f)), false, ringTopLeft, Size(ringW, ringH), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (ringAlpha > 0f) ringHalf(180f, 0.28f)

        // Speed lines over the top left while it spins fast, fading as it slows.
        val speed = 1f - phase(p, 0.15f, 0.6f)
        if (speed > 0f && p > 0.03f) {
            val arcBox = Size(r * 2.6f, r * 2.6f)
            val arcOrigin = Offset(center.x - r * 1.3f, center.y - r * 1.3f)
            drawArc(ring.copy(alpha = 0.45f * speed), 200f, 38f, false, arcOrigin, arcBox, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            drawArc(ring.copy(alpha = 0.3f * speed), 205f, 26f, false, arcOrigin + Offset(-r * 0.12f, -r * 0.12f), Size(arcBox.width + r * 0.24f, arcBox.height + r * 0.24f), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
        }

        // Contact shadow.
        val move = FastOutSlowInEasing.transform(phase(p, 0f, 0.55f))
        val sw = r * 2.1f
        drawOval(
            Brush.radialGradient(listOf(shadow.copy(alpha = shadow.alpha * move), Color.Transparent), center = Offset(center.x, center.y + r * 1.42f), radius = sw / 2f),
            topLeft = Offset(center.x - sw / 2f, center.y + r * 1.42f - sw * 0.09f),
            size = Size(sw, sw * 0.18f),
        )

        // Edge: the back face and the band joining it to the front, with fine reeding.
        val edgeLight = abs(sinT)
        val edge = lerp(SilverDark, SilverMid, 0.25f + 0.5f * edgeLight)
        val edgeBrush = Brush.verticalGradient(listOf(lerp(edge, Color.White, 0.35f), edge, lerp(edge, Color.Black, 0.35f)), startY = center.y - r, endY = center.y + r)
        drawOval(edgeBrush, topLeft = Offset(back.x - rx, back.y - r), size = Size(rx * 2f, r * 2f))
        val left = minOf(front.x, back.x)
        val right = maxOf(front.x, back.x)
        if (right - left > 0.5f) {
            drawRect(edgeBrush, topLeft = Offset(left, center.y - r), size = Size(right - left, r * 2f))
            val ridges = 14
            for (i in 1 until ridges) {
                val y = center.y - r + 2f * r * i / ridges
                drawLine(Color.Black.copy(alpha = 0.14f), Offset(left, y), Offset(right, y), 0.8.dp.toPx())
            }
        }

        // Face: brushed silver that catches the light as it turns towards you.
        if (rx > 0.5f) {
            val faceTone = lerp(SilverDark, SilverMid, 0.4f + 0.6f * abs(cosT))
            drawOval(
                Brush.radialGradient(listOf(Color.White, faceTone, lerp(faceTone, Color.Black, 0.2f)), center = Offset(front.x - rx * 0.35f, front.y - r * 0.4f), radius = r * 1.5f),
                topLeft = Offset(front.x - rx, front.y - r),
                size = Size(rx * 2f, r * 2f),
            )
            // Raised rim and an engraved inner ring.
            drawOval(Color.White.copy(alpha = 0.55f), topLeft = Offset(front.x - rx * 0.97f, front.y - r * 0.97f), size = Size(rx * 1.94f, r * 1.94f), style = Stroke(r * 0.03f))
            drawOval(Engraving.copy(alpha = 0.4f), topLeft = Offset(front.x - rx * 0.8f, front.y - r * 0.8f), size = Size(rx * 1.6f, r * 1.6f), style = Stroke(r * 0.035f))
            // The ₹, embossed, narrowing with the face.
            scale(scaleX = abs(cosT), scaleY = 1f, pivot = front) {
                val tl = Offset(front.x - glyph.size.width / 2f, front.y - glyph.size.height / 2f)
                drawText(glyphLight, topLeft = tl + Offset(1.5.dp.toPx(), 1.5.dp.toPx()))
                drawText(glyph, color = Engraving, topLeft = tl)
            }
            // A soft specular sweep across the face.
            drawArc(
                Color.White.copy(alpha = 0.65f * abs(cosT)), 200f, 70f, false,
                Offset(front.x - rx * 0.78f, front.y - r * 0.78f), Size(rx * 1.56f, r * 1.56f), style = Stroke(r * 0.045f, cap = StrokeCap.Round),
            )
        }

        // The near half of the orbit passes in front of the coin.
        if (ringAlpha > 0f) ringHalf(0f, 0.7f)

        // Income: a small arrow rising beside the coin.
        if (rising) {
            val a = FastOutSlowInEasing.transform(phase(p, 0.3f, 0.55f)) * (1f - phase(p, 0.85f, 1f))
            if (a > 0f) {
                val ax = center.x + r * 1.25f
                val ay = center.y - r * 0.9f - (1f - a) * 10.dp.toPx()
                val len = 16.dp.toPx()
                val pos = c.positive.copy(alpha = a)
                drawLine(pos, Offset(ax - len / 2f, ay + len / 2f), Offset(ax + len / 2f, ay - len / 2f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(pos, Offset(ax + len / 2f, ay - len / 2f), Offset(ax - len * 0.05f, ay - len / 2f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(pos, Offset(ax + len / 2f, ay - len / 2f), Offset(ax + len / 2f, ay + len * 0.05f), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun TransferStage(t: Animatable<Float, *>, spec: MomentSpec) {
    val c = Spendly.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            spec.headline,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer { alpha = phase(t.value, 0f, 0.2f) },
        )
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.Top) {
            AccountBubble(spec.fromLabel, Modifier.graphicsLayer { alpha = 1f - 0.35f * phase(t.value, 0.2f, 0.6f) })
            // Five dots; a pulse runs along them from the first account to the second.
            Row(
                Modifier.padding(horizontal = Space.sm).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(5) { i ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .graphicsLayer {
                                val head = phase(t.value, 0.05f, 0.65f) * 7f - 1f
                                val glow = (1f - abs(head - i)).coerceIn(0f, 1f)
                                alpha = 0.25f + 0.75f * glow
                                val s = 1f + 0.5f * glow
                                scaleX = s
                                scaleY = s
                            }
                            .background(c.accent, CircleShape),
                    )
                }
            }
            AccountBubble(spec.toLabel, Modifier.graphicsLayer {
                // The receiving account lights up as the money arrives.
                val arrive = phase(t.value, 0.55f, 0.75f)
                val s = 1f + 0.08f * arrive * (1f - phase(t.value, 0.75f, 0.95f))
                scaleX = s
                scaleY = s
            })
        }
    }
}

@Composable
private fun AccountBubble(label: String?, modifier: Modifier = Modifier) {
    val c = Spendly.colors
    Column(modifier.widthIn(max = 104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(c.cardElevated, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
        if (label != null) {
            Spacer(Modifier.height(Space.xs))
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp), textAlign = TextAlign.Center)
        }
    }
}
