package io.github.sharveshar.spendly.ui.components

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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
        wasEdit -> MomentSpec(MomentKind.UPDATED, "Changes saved", amount, listOfNotNull(category, account).joinToString(" · ").ifEmpty { null })
        type == TransactionType.TRANSFER -> MomentSpec(
            MomentKind.TRANSFER, "Transfer done", amount,
            detail = if (account != null && toAccount != null) "$account → $toAccount" else null,
            fromLabel = account, toLabel = toAccount,
        )
        type == TransactionType.REFUND -> MomentSpec(MomentKind.INCOME, "Refund recorded", account?.let { "$amount back to $it" } ?: amount)
        type == TransactionType.INCOME -> MomentSpec(MomentKind.INCOME, "Income added", account?.let { "$amount to $it" } ?: amount, category)
        else -> MomentSpec(MomentKind.EXPENSE, "Expense added", category?.let { "$amount on $it" } ?: amount, account?.let { "from $it" })
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
    val shadow = if (c.isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.18f)
    val ring = c.accent
    Box(Modifier.size(184.dp), contentAlignment = Alignment.Center) {
        // Orbit: a thin swoosh that draws itself around the coin, then fades.
        Canvas(Modifier.fillMaxSize()) {
            val p = t.value
            val grow = FastOutSlowInEasing.transform(phase(p, 0.05f, 0.6f))
            val alpha = phase(p, 0.05f, 0.25f) * (1f - phase(p, 0.7f, 0.95f))
            if (alpha > 0f) {
                rotate(-16f) {
                    val w = size.width * 0.96f
                    val h = size.height * 0.34f
                    drawArc(
                        Brush.sweepGradient(listOf(ring.copy(alpha = 0f), ring.copy(alpha = 0.55f * alpha), ring.copy(alpha = 0f))),
                        startAngle = 200f,
                        sweepAngle = 320f * grow,
                        useCenter = false,
                        topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                        size = Size(w, h),
                        style = Stroke(width = 1.25.dp.toPx()),
                    )
                }
            }
            // Soft contact shadow under the coin.
            val move = FastOutSlowInEasing.transform(phase(p, 0f, 0.55f))
            val fade = 1f - phase(p, 0.82f, 1f)
            val sw = size.width * 0.42f * (0.7f + 0.3f * move)
            drawOval(
                Brush.radialGradient(listOf(shadow.copy(alpha = shadow.alpha * move * fade), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.86f), radius = sw / 2f),
                topLeft = Offset((size.width - sw) / 2f, size.height * 0.86f - sw * 0.1f),
                size = Size(sw, sw * 0.2f),
            )
        }
        // The coin: silver, embossed ₹, turning about its vertical axis.
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer {
                    val p = t.value
                    val move = FastOutSlowInEasing.transform(phase(p, 0f, 0.55f))
                    val away = phase(p, 0.82f, 1f)
                    translationY = (1f - move) * (if (rising) 64.dp else 28.dp).toPx() - (if (rising) 6.dp.toPx() * move else 0f)
                    val s = (0.72f + 0.28f * move) * (1f - 0.08f * away)
                    scaleX = s
                    scaleY = s
                    rotationY = spinAt(p, rising)
                    rotationX = 8f
                    cameraDistance = 14f * density
                    alpha = (phase(p, 0f, 0.12f)) * (1f - away)
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                // Face-on the coin catches the light; edge-on it dims.
                val turn = abs(cos(spinAt(t.value, rising) * PI.toFloat() / 180f))
                drawCircle(Brush.linearGradient(listOf(SilverLight, SilverDark, SilverMid), start = Offset.Zero, end = Offset(size.width, size.height)), r)
                val face = lerp(SilverDark, SilverMid, 0.45f + 0.55f * turn)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White, face, lerp(face, Color.Black, 0.18f)), center = Offset(r * 0.66f, r * 0.56f), radius = r * 1.4f),
                    r * 0.88f,
                )
                drawCircle(Engraving.copy(alpha = 0.45f), r * 0.74f, style = Stroke(r * 0.03f))
                drawCircle(Color.White.copy(alpha = 0.55f), r * 0.775f, style = Stroke(r * 0.018f))
                // A thin specular arc, top left.
                drawArc(
                    Color.White.copy(alpha = 0.7f * turn), 195f, 80f, false,
                    Offset(r * 0.2f, r * 0.2f), Size(r * 1.6f, r * 1.6f), style = Stroke(width = r * 0.05f),
                )
            }
            Text(
                "₹",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold,
                    // Embossed: a light edge below-right of the engraving.
                    shadow = Shadow(Color.White.copy(alpha = 0.7f), Offset(2f, 2f), 1.5f),
                ),
                color = Engraving,
            )
        }
        if (rising) {
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = c.positive,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 34.dp)
                    .size(22.dp)
                    .graphicsLayer {
                        val p = FastOutSlowInEasing.transform(phase(t.value, 0.3f, 0.55f))
                        alpha = p * (1f - phase(t.value, 0.85f, 1f))
                        translationY = (1f - p) * 10.dp.toPx()
                    },
            )
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
