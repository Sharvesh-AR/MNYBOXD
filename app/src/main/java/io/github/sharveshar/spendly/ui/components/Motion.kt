package io.github.sharveshar.spendly.ui.components

import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.currentValueOf
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.sharveshar.spendly.ui.theme.Spendly

/** Shared motion specs so timing feels consistent across the app. */
object Motion {
    fun <T> spatial() = spring<T>(dampingRatio = 0.86f, stiffness = 420f)
    val boundsSpring = spring(dampingRatio = 0.88f, stiffness = 380f, visibilityThreshold = Rect.VisibilityThreshold)
}

/**
 * True while "Hide app contents" is on. Every window Spendly opens (dialogs and popups as well as
 * the activity) must then carry FLAG_SECURE, because the flag doesn't extend to child windows.
 */
val LocalSecureWindows = staticCompositionLocalOf { false }

fun Window.setSecure(secure: Boolean) {
    if (secure) addFlags(WindowManager.LayoutParams.FLAG_SECURE) else clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

/** True when the user turned animations off (Developer options / Accessibility "Remove animations"). */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
}

// ---------------------------------------------------------------------------------------------
// Shared-element plumbing for navigation (container transforms between screens).
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun ProvideNavAnimatedScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedScope provides scope, content = content)
}

/**
 * Morphs this element's bounds into/out of the element with the same [key] on the next/previous
 * screen (for example a transaction row into its detail sheet). No-op when motion is reduced or
 * no transition scope is present.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedContainer(key: Any, cornerDp: Int = 20): Modifier = composed {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    if (shared == null || animated == null || LocalReducedMotion.current) return@composed this
    with(shared) {
        this@composed.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = BoundsTransform { _, _ -> Motion.boundsSpring },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cornerDp.dp)),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Origin-anchored popups: dialogs that grow out of the element that opened them.
// ---------------------------------------------------------------------------------------------

@Stable
class OriginAnchor {
    var bounds by mutableStateOf<Rect?>(null)
        internal set
}

@Composable
fun rememberOriginAnchor() = remember { OriginAnchor() }

/** Records where this element is on screen so a popup can expand from it. */
fun Modifier.originAnchor(anchor: OriginAnchor): Modifier = onGloballyPositioned { anchor.bounds = it.boundsInWindow() }

/**
 * A modal surface that expands from [anchor]'s bounds into place, and collapses back into the
 * anchor when dismissed (container-transform style). Falls back to a quick fade/scale from
 * centre when the anchor is unknown, and to an instant change when motion is reduced.
 *
 * Keep [visible] true while open; set it false to play the closing animation. [onDismissRequest]
 * is called for back presses and scrim taps.
 */
@Composable
fun OriginPopup(
    visible: Boolean,
    anchor: OriginAnchor?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val scrim = Spendly.colors.scrim
    val progress = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    if (visible) mounted = true

    LaunchedEffect(visible) {
        if (visible) {
            if (reduced) progress.snapTo(1f) else progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
        } else if (mounted) {
            if (reduced) progress.snapTo(0f) else progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
            mounted = false
        }
    }
    if (!mounted) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window animation would fight our own transform, so disable it.
        DialogWindowSetup(LocalView.current)
        var cardBounds by remember { mutableStateOf<Rect?>(null) }
        val p = progress.value
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim.copy(alpha = scrim.alpha * p))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest)
                .systemBarsPadding()
                .imePadding()
                .padding(20.dp),
            contentAlignment = alignment,
        ) {
            Box(
                modifier
                    .widthIn(max = 520.dp)
                    .onGloballyPositioned { cardBounds = it.boundsInWindow() }
                    .graphicsLayer {
                        val from = anchor?.bounds
                        val to = cardBounds
                        if (from != null && to != null && to.width > 0 && to.height > 0) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            val sx = from.width / to.width
                            val sy = from.height / to.height
                            scaleX = sx + (1f - sx) * p
                            scaleY = sy + (1f - sy) * p
                            translationX = (from.left - to.left) * (1f - p)
                            translationY = (from.top - to.top) * (1f - p)
                        } else {
                            scaleX = 0.92f + 0.08f * p
                            scaleY = 0.92f + 0.08f * p
                        }
                        alpha = (p * 1.6f).coerceAtMost(1f)
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(Spendly.colors.cardElevated)
                    // Swallow taps so they don't reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onSurface) {
                    Box(Modifier.graphicsLayer { alpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f) }, content = content)
                }
            }
        }
    }
}

/**
 * Prepares a Compose Dialog's window for Spendly's own animations: no window animation or dim
 * (we draw our own scrim), FLAG_SECURE when "Hide app contents" is on, and system bar icons
 * that stay readable on the current theme.
 */
@Composable
fun DialogWindowSetup(view: android.view.View) {
    val secure = LocalSecureWindows.current
    val dark = Spendly.colors.isDark
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    LaunchedEffect(window, secure, dark) {
        window.setWindowAnimations(0)
        window.setDimAmount(0f)
        window.setSecure(secure)
        // Let the keyboard resize the window (delivered as IME insets) instead of panning it.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** Drives a one-time staggered entrance for a screen's sections. */
@Stable
class StaggerState internal constructor(internal val progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>)

/**
 * Sections fade and rise into place one after another the first time a screen is shown. It plays
 * once per screen per session (not on every tab switch) and never with reduced motion.
 */
@Composable
fun rememberStagger(): StaggerState {
    val reduced = LocalReducedMotion.current
    var played by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            played = true
            progress.animateTo(1f, androidx.compose.animation.core.tween(720, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        }
    }
    return remember { StaggerState(progress) }
}

/** Item [index]'s part of a [StaggerState] entrance (read in the draw phase: no recomposition). */
fun Modifier.staggered(state: StaggerState, index: Int): Modifier = graphicsLayer {
    val p = ((state.progress.value - index * 0.07f) / 0.5f).coerceIn(0f, 1f)
    alpha = p
    translationY = (1f - p) * 20.dp.toPx()
}

/** Gentle press-down scale for tappable surfaces. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (isPressed && !reduced) pressed else 1f,
        spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * The app's touch feedback, used instead of Material's ripple: no highlight or flash, just a
 * quick spring "press" of whatever was touched, released with a slight bounce. Provided as
 * LocalIndication, so every clickable gets it. Skipped when animations are off.
 */
object PressIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        PressIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 7
}

private class PressIndicationNode(
    private val source: androidx.compose.foundation.interaction.InteractionSource,
) : Modifier.Node(), androidx.compose.ui.node.DrawModifierNode, androidx.compose.ui.node.CompositionLocalConsumerModifierNode {
    private val amount = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = 0
            source.interactions.collect { i ->
                when (i) {
                    is androidx.compose.foundation.interaction.PressInteraction.Press -> pressed++
                    is androidx.compose.foundation.interaction.PressInteraction.Release,
                    is androidx.compose.foundation.interaction.PressInteraction.Cancel,
                    -> pressed = (pressed - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                if (currentValueOf(LocalReducedMotion)) return@collect
                val down = pressed > 0
                launch {
                    amount.animateTo(
                        if (down) 0.965f else 1f,
                        if (down) spring(dampingRatio = 1f, stiffness = 1400f) else spring(dampingRatio = 0.5f, stiffness = 700f),
                    )
                }
            }
        }
    }

    override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
        val s = amount.value
        if (s == 1f) {
            drawContent()
        } else {
            scale(s) { this@draw.drawContent() }
        }
    }
}
