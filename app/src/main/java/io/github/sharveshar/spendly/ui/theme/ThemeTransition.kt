package io.github.sharveshar.spendly.ui.theme

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.core.content.edit

/**
 * Applies [target] and crossfades theme changes: the last frame drawn in the old theme is kept
 * as a snapshot and faded out over the new one. The app recomposes once per switch instead of on
 * every animation frame, so switching stays smooth on modest phones and never flashes.
 */
@Composable
fun AnimatedSpendlyTheme(target: SpendlyPalette, reducedMotion: Boolean, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(target) }
    var capturing by remember { mutableStateOf(false) }
    val snapshot = rememberGraphicsLayer()
    val fade = remember { Animatable(0f) }

    LaunchedEffect(target) {
        if (target == shown) return@LaunchedEffect
        if (reducedMotion) {
            shown = target
            return@LaunchedEffect
        }
        // Frame N records the old theme in its draw pass; switch once that frame is done.
        capturing = true
        withFrameNanos { }
        withFrameNanos { }
        capturing = false
        fade.snapTo(1f)
        shown = target
        fade.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                if (capturing) snapshot.record { this@drawWithContent.drawContent() }
                drawContent()
                val a = fade.value
                if (a > 0f) {
                    snapshot.alpha = a
                    drawLayer(snapshot)
                }
            },
    ) {
        SpendlyTheme(shown, content)
    }
}

/**
 * A synchronous copy of the chosen theme, read before the first frame so the window background
 * and splash match the user's theme instead of flashing the default. DataStore stays the source
 * of truth; this mirror is rewritten whenever the choice changes.
 */
object ThemeCache {
    private const val FILE = "spendly_theme_cache"
    private const val KEY = "theme_choice"

    fun read(context: Context): ThemeChoice =
        ThemeChoice.parse(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)) ?: ThemeChoice.DEFAULT

    fun write(context: Context, choice: ThemeChoice) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getString(KEY, null) != choice.name) prefs.edit { putString(KEY, choice.name) }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { clear() }
    }
}
