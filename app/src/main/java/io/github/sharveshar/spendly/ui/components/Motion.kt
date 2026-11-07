package io.github.sharveshar.spendly.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
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
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { w ->
            LaunchedEffect(w) {
                w.setWindowAnimations(0)
                w.setDimAmount(0f)
            }
        }
        var cardBounds by remember { mutableStateOf<Rect?>(null) }
        val p = progress.value
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f * p))
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
                Box(Modifier.graphicsLayer { alpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f) }, content = content)
            }
        }
    }
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
