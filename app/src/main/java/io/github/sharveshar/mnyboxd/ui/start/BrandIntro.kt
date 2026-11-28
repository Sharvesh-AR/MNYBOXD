// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextPainter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.sharveshar.mnyboxd.ui.components.APP_NAME
import io.github.sharveshar.mnyboxd.ui.components.LocalReducedMotion
import io.github.sharveshar.mnyboxd.ui.components.boxdColor
import io.github.sharveshar.mnyboxd.ui.components.wordmarkStyle
import io.github.sharveshar.mnyboxd.ui.components.wordmarkText
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Where Home's wordmark sits on screen, so the opening animation can land exactly on it. */
object BrandIntroAnchor {
    var wordmark: Rect? by mutableStateOf(null)
}

private const val TOTAL_MS = 3_000
private const val REDUCED_MS = 900
private val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun phase(t: Float, start: Float, length: Float) = clamp01((t - start) / length)
private fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f

/**
 * The opening of a new app session, about three seconds: MONEY becomes MNYBOXD. M, N and Y close
 * up; the O lifts out and lands as the O of BOXD; the E's arms curl into the bowls of a B; X and D
 * are drawn in. The finished wordmark then glides into Home's header (or fades, if Home isn't the
 * first screen) while the app appears around it. With reduced motion it's a short fade.
 *
 * Shown over the app, which starts underneath as usual; touches wait until it's done, and Back
 * skips it. Which launches play it is decided by MainActivity.
 */
@Composable
fun BrandIntro(onFinished: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val background = MaterialTheme.colorScheme.background
    val inkColor = MaterialTheme.colorScheme.onBackground
    val muted = boxdColor(inkColor)
    val style = wordmarkStyle(MaterialTheme.typography.titleLarge)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val clock = remember { Animatable(0f) }
    val total = if (reduced) REDUCED_MS else TOTAL_MS

    LaunchedEffect(Unit) {
        clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
        onFinished()
    }
    BackHandler { onFinished() }

    val semi = remember(style) { style.copy(fontWeight = FontWeight.SemiBold) }
    val light = remember(style) { style.copy(fontWeight = FontWeight.Light) }
    val final = remember(style, inkColor) { measurer.measure(wordmarkText(inkColor), style.copy(color = inkColor)) }
    val money = remember(style) { measurer.measure(AnnotatedString("MONEY"), semi) }
    val glyphs = remember(style) {
        fun g(c: String, s: androidx.compose.ui.text.TextStyle) = measurer.measure(AnnotatedString(c), s)
        mapOf("M" to g("M", semi), "O" to g("O", semi), "N" to g("N", semi), "E" to g("E", semi), "Y" to g("Y", semi),
            "o" to g("O", light), "B" to g("B", light), "X" to g("X", light), "D" to g("D", light))
    }
    val spacing = with(density) { style.letterSpacing.toPx() }
    val em = with(density) { style.fontSize.toPx() }
    // Where each letter's ink really is in this font, alone and inside the two words, so every
    // letter lands exactly where the real wordmark draws it and the strokes sit on the glyphs.
    val ink = remember(glyphs) { glyphs.mapValues { (key, layout) -> inkOf(layout, stemRow = if (key == "E" || key == "B") 0.25f else 0.5f) } }
    val finalInk = remember(style) { inkRuns(measurer.measure(wordmarkText(Color.Black), style.copy(color = Color.Black)), 7) }
    val moneyInk = remember(money) { inkRuns(money, 5) }

    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = APP_NAME }
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
    ) {
        val t = clock.value
        val finalW = final.size.width - spacing
        val moneyW = money.size.width - spacing
        val h = final.size.height.toFloat()
        val stageScale = min(1.9f, size.width * 0.7f / finalW)
        val centre = Offset(size.width / 2f, size.height / 2f)
        val header = BrandIntroAnchor.wordmark

        if (reduced) {
            val a = phase(t, 0f, 200f) * (1f - phase(t, 600f, 300f))
            drawRect(background, alpha = 1f - phase(t, 600f, 300f))
            withTransform({ translate(centre.x, centre.y); scale(stageScale, stageScale, Offset.Zero) }) {
                drawText(final, topLeft = Offset(-finalW / 2f, -h / 2f), alpha = a)
            }
            return@Canvas
        }

        // Fly into the header (or fade where there's no header to land on).
        val fly = InOut.transform(phase(t, 2_360f, 600f))
        val landing = header?.let { Offset(it.left + finalW / 2f, it.top + h / 2f) }
        val at = if (landing != null) lerp(centre, landing, fly) else centre
        val scale = if (landing != null) mix(stageScale, 1f, fly) else stageScale
        val fadeAll = if (landing == null) 1f - phase(t, 2_400f, 450f) else 1f
        val backdrop = if (landing != null) 1f - phase(t, 2_700f, 300f) else fadeAll
        drawRect(background, alpha = backdrop)

        withTransform({ translate(at.x, at.y); scale(scale, scale, Offset.Zero) }) {
            val x0Money = -moneyW / 2f
            val x0Final = -finalW / 2f
            val top = -h / 2f
            // Layout origins that put a letter's ink where that letter's ink is in MONEY / MNYBOXD.
            fun mx(j: Int, key: String) = x0Money + (moneyInk?.get(j)?.minus(ink.getValue(key).left) ?: money.getBoundingBox(j).left)
            fun fx(i: Int, key: String) = x0Final + (finalInk?.get(i)?.minus(ink.getValue(key).left) ?: final.getBoundingBox(i).left)

            if (t >= 1_980f) {
                // The real wordmark, exactly as Home draws it.
                drawText(final, topLeft = Offset(x0Final, top), alpha = fadeAll)
                return@withTransform
            }

            val move = InOut.transform(phase(t, 820f, 880f))
            fun intro(j: Int) = FastOutSlowInEasing.transform(phase(t, 70f * j, 360f))
            fun rise(j: Int) = (1f - intro(j)) * em * 0.35f

            // M, N and Y keep their weight and close the gaps.
            drawGlyph(glyphs.getValue("M"), mix(mx(0, "M"), fx(0, "M"), move), top + rise(0), inkColor, intro(0))
            drawGlyph(glyphs.getValue("N"), mix(mx(2, "N"), fx(1, "N"), move), top + rise(2), inkColor, intro(2))
            drawGlyph(glyphs.getValue("Y"), mix(mx(4, "Y"), fx(2, "Y"), move), top + rise(4), inkColor, intro(4))

            // O lifts out in an arc and lands as BOXD's light O.
            val oy = top + rise(1) - em * 0.75f * sin(PI.toFloat() * move)
            val soften = phase(t, 1_000f, 520f)
            drawGlyph(glyphs.getValue("O"), mix(mx(1, "O"), fx(4, "O"), move), oy, inkColor, intro(1) * (1f - soften))
            drawGlyph(glyphs.getValue("o"), mix(mx(1, "o"), fx(4, "o"), move), oy, muted, intro(1) * soften)

            // E: its arms curl into a B's bowls on the way to its place.
            val ex = mix(mx(3, "E"), fx(3, "B"), move)
            val ey = top + rise(3) + em * 0.6f * sin(PI.toFloat() * move)
            val toStroke = phase(t, 820f, 140f)
            val toGlyph = phase(t, 1_560f, 220f)
            drawGlyph(glyphs.getValue("E"), ex, ey, inkColor, intro(3) * (1f - toStroke))
            val curl = InOut.transform(phase(t, 940f, 560f))
            drawCurl(ink.getValue("E"), ink.getValue("B"), ex, ey, curl, lerp(inkColor, muted, curl), toStroke * (1f - toGlyph))
            drawGlyph(glyphs.getValue("B"), fx(3, "B"), top, muted, toGlyph)

            // X and D are drawn in, then handed to the typeface.
            val xIn = FastOutSlowInEasing.transform(phase(t, 1_240f, 360f))
            val xGlyph = phase(t, 1_640f, 220f)
            drawX(ink.getValue("X"), fx(5, "X"), top, xIn, muted, ink.getValue("D").stem, 1f - xGlyph)
            drawGlyph(glyphs.getValue("X"), fx(5, "X"), top, muted, xGlyph)
            val dIn = FastOutSlowInEasing.transform(phase(t, 1_340f, 420f))
            val dGlyph = phase(t, 1_760f, 220f)
            drawD(ink.getValue("D"), fx(6, "D"), top, dIn, muted, 1f - dGlyph)
            drawGlyph(glyphs.getValue("D"), fx(6, "D"), top, muted, dGlyph)
        }
    }
}

private fun DrawScope.drawGlyph(layout: TextLayoutResult, x: Float, y: Float, color: Color, alpha: Float) {
    if (alpha <= 0.001f) return
    drawText(layout, color = color, topLeft = Offset(x, y), alpha = clamp01(alpha))
}

/** A letter's ink inside its own layout (px), and its upright stroke's thickness. */
private class Ink(val left: Float, val top: Float, val right: Float, val bottom: Float, val stem: Float) {
    fun mix(other: Ink, f: Float) = Ink(
        mix(left, other.left, f), mix(top, other.top, f), mix(right, other.right, f), mix(bottom, other.bottom, f), mix(stem, other.stem, f),
    )
}

/**
 * Paints [layout] off screen once and finds its ink: the box, and the stem's thickness on the row
 * [stemRow] of the way down. Works with whatever typeface the phone uses.
 */
private fun inkOf(layout: TextLayoutResult, stemRow: Float): Ink {
    val w = layout.size.width.coerceAtLeast(1)
    val h = layout.size.height.coerceAtLeast(1)
    val bitmap = ImageBitmap(w, h)
    TextPainter.paint(androidx.compose.ui.graphics.Canvas(bitmap), layout)
    val pixels = bitmap.toPixelMap()
    var l = w; var r = -1; var t = h; var b = -1
    for (y in 0 until h) for (x in 0 until w) {
        if (pixels[x, y].alpha > 0.2f) {
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
    }
    if (r < l || b < t) {
        // Nothing drawn (shouldn't happen): a sensible stand-in from the line's own metrics.
        val base = layout.getLineBaseline(0)
        return Ink(w * 0.12f, base - h * 0.5f, w * 0.8f, base, h * 0.05f)
    }
    val row = (t + (b - t) * stemRow).toInt().coerceIn(0, h - 1)
    var x = l
    while (x < w && pixels[x, row].alpha > 0.2f) x++
    return Ink(l.toFloat(), t.toFloat(), r + 1f, b + 1f, (x - l).toFloat().coerceAtLeast(1f))
}

/**
 * The left edge of each letter's ink in a laid-out word, found by painting it off screen; null
 * if the letters don't come out as [count] separate shapes (then layout boxes are used instead).
 */
private fun inkRuns(layout: TextLayoutResult, count: Int): List<Float>? {
    val w = layout.size.width.coerceAtLeast(1)
    val h = layout.size.height.coerceAtLeast(1)
    val bitmap = ImageBitmap(w, h)
    TextPainter.paint(androidx.compose.ui.graphics.Canvas(bitmap), layout)
    val pixels = bitmap.toPixelMap()
    val lefts = mutableListOf<Float>()
    var inside = false
    for (x in 0 until w) {
        var any = false
        for (y in 0 until h) if (pixels[x, y].alpha > 0.2f) { any = true; break }
        if (any && !inside) lefts += x.toFloat()
        inside = any
    }
    return lefts.takeIf { it.size == count }
}

/**
 * An E whose top and bottom arms curl round to the middle bar as [curl] goes 0 → 1, becoming a
 * B: [e] and [b] are the two letters' ink, so it starts on the E and ends on the B. [x], [y]
 * place the letter's layout.
 */
private fun DrawScope.drawCurl(e: Ink, b: Ink, x: Float, y: Float, curl: Float, color: Color, alpha: Float) {
    if (alpha <= 0.001f) return
    val k = e.mix(b, curl)
    val w = k.stem
    val stem = x + k.left + w / 2f
    val top = y + k.top + w / 2f
    val base = y + k.bottom - w / 2f
    val mid = top + (base - top) * 0.47f
    val r1 = (mid - top) / 2f
    val r2 = (base - mid) / 2f
    val bRight = x + b.right - w / 2f
    val a1 = bRight - r1 - (b.right - b.left) * 0.06f
    val a2 = bRight - r2
    val eRight = x + e.right
    val xTop = mix(eRight, a1, curl)
    val xMid = mix(x + e.left + (e.right - e.left) * 0.86f, a2, curl)
    val xBot = mix(eRight, a2, curl)
    val c = color.copy(alpha = color.alpha * clamp01(alpha))
    val line = Stroke(width = w, cap = StrokeCap.Butt, join = StrokeJoin.Round)
    drawLine(c, Offset(stem, top), Offset(stem, base), strokeWidth = w, cap = StrokeCap.Square)
    drawPath(Path().apply {
        moveTo(stem, top)
        lineTo(xTop, top)
        if (curl > 0f) arcTo(Rect(xTop - r1, top, xTop + r1, top + 2 * r1), -90f, 180f * curl, false)
    }, c, style = line)
    drawLine(c, Offset(stem, mid), Offset(xMid, mid), strokeWidth = w)
    drawPath(Path().apply {
        moveTo(stem, base)
        lineTo(xBot, base)
        if (curl > 0f) arcTo(Rect(xBot - r2, base - 2 * r2, xBot + r2, base), 90f, -180f * curl, false)
    }, c, style = line)
}

/** An X drawn stroke by stroke over its own ink as [progress] goes 0 → 1. */
private fun DrawScope.drawX(k: Ink, x: Float, y: Float, progress: Float, color: Color, width: Float, alpha: Float) {
    if (alpha <= 0.001f || progress <= 0f) return
    val l = x + k.left + width * 0.55f
    val r = x + k.right - width * 0.55f
    val top = y + k.top
    val base = y + k.bottom
    val c = color.copy(alpha = color.alpha * clamp01(alpha))
    val first = clamp01(progress * 2f)
    val second = clamp01(progress * 2f - 1f)
    drawLine(c, Offset(l, top), Offset(mix(l, r, first), mix(top, base, first)), strokeWidth = width)
    if (second > 0f) drawLine(c, Offset(r, top), Offset(mix(r, l, second), mix(top, base, second)), strokeWidth = width)
}

/** A D drawn like a pen would over its own ink: the stem, then the bowl from top to bottom. */
private fun DrawScope.drawD(k: Ink, x: Float, y: Float, progress: Float, color: Color, alpha: Float) {
    if (alpha <= 0.001f || progress <= 0f) return
    val w = k.stem
    val stem = x + k.left + w / 2f
    val top = y + k.top + w / 2f
    val base = y + k.bottom - w / 2f
    val r = (base - top) / 2f
    val a = x + k.right - w / 2f - r
    val bar = (a - stem).coerceAtLeast(0f)
    val lengths = floatArrayOf(base - top, bar, (PI * r).toFloat(), bar)
    var left = lengths.sum() * progress
    val c = color.copy(alpha = color.alpha * clamp01(alpha))
    val path = Path()
    val s = min(left, lengths[0]); left -= s
    path.moveTo(stem, top); path.lineTo(stem, top + s)
    if (left > 0f) {
        val b = min(left, lengths[1]); left -= b
        path.moveTo(stem, top); path.lineTo(stem + b, top)
        if (left > 0f) {
            val arc = min(left, lengths[2]); left -= arc
            path.arcTo(Rect(a - r, top, a + r, base), -90f, 180f * (arc / lengths[2]), false)
            if (left > 0f) path.lineTo(a - min(left, lengths[3]), base)
        }
    }
    drawPath(path, c, style = Stroke(width = w, cap = StrokeCap.Square, join = StrokeJoin.Round))
}
