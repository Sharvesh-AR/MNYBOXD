package io.github.sharveshar.spendly.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.ui.entry.SheetMotion
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Where Home wants the + to sit (Home reserves the space; the + itself lives in the shell). */
@Stable
class AddButtonSlot {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy())
        internal set
}

val LocalAddButtonSlot = staticCompositionLocalOf<AddButtonSlot?> { null }

/** Size of the + where it rests on Home, and in the corner on every other tab. */
val AddButtonHomeSize = 64.dp
val AddButtonCornerSize = 56.dp

/** Reserves the + 's place on Home and reports where it is, every time it moves (scrolling too). */
@Composable
fun AddButtonPlace(modifier: Modifier = Modifier) {
    val slot = LocalAddButtonSlot.current
    Box(modifier.size(AddButtonHomeSize).onGloballyPositioned { slot?.coordinates = it })
}

/**
 * The one + in the app. On Home it rests in the middle of the screen, where Home made room for
 * it, and moves with Home as it scrolls. Switch to another tab and it glides down to the corner
 * above the dock, shrinking a little; come back and it glides home. Tap to add, hold to speak.
 * It turns into a × while Quick Add is open, and steps aside on screens that aren't tabs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlidingAddButton(
    slot: AddButtonSlot,
    onHome: Boolean,
    visible: Boolean,
    corner: PaddingValues,
    motion: SheetMotion,
    onAdd: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Spendly.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var container by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    val home by rememberUpdatedState(onHome)
    val shown by rememberUpdatedState(visible)
    val size by animateDpAsState(if (onHome) AddButtonHomeSize else AddButtonCornerSize, if (reduced) tween(0) else spring(dampingRatio = 0.8f, stiffness = 380f), label = "addSize")
    val appear by animateFloatAsState(if (visible) 1f else 0f, if (reduced) tween(0) else spring(dampingRatio = 0.75f, stiffness = 500f), label = "addAppear")
    // The centre of the button; null until its first resting place is known.
    val center = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var placed by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { container = it }) {
        val cornerCenter = with(density) {
            val half = AddButtonCornerSize.toPx() / 2f
            Offset(
                constraints.maxWidth - corner.calculateEndPadding(direction).toPx() - half,
                constraints.maxHeight - corner.calculateBottomPadding().toPx() - half,
            )
        }
        val cornerTarget by rememberUpdatedState(cornerCenter)

        LaunchedEffect(Unit) {
            var lastHome: Boolean? = null
            var gliding = false
            snapshotFlow {
                val target = if (home) {
                    val box = container
                    val place = slot.coordinates
                    if (box != null && place != null && box.isAttached && place.isAttached) {
                        box.localPositionOf(place, Offset.Zero) + Offset(place.size.width / 2f, place.size.height / 2f)
                    } else null
                } else cornerTarget
                Triple(home, shown, target)
            }.collectLatest { (atHome, isShown, target) ->
                if (target == null) return@collectLatest
                when {
                    // First placement, hidden, or animations off: just be there.
                    lastHome == null || !isShown || reduced -> {
                        center.snapTo(target)
                        lastHome = atHome
                        gliding = false
                        placed = true
                    }
                    // Changed tab: glide to the new place (and keep gliding if the target settles).
                    atHome != lastHome || gliding -> {
                        lastHome = atHome
                        gliding = true
                        center.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 260f))
                        gliding = false
                    }
                    // Same place, moved (Home scrolling): follow exactly.
                    else -> center.snapTo(target)
                }
            }
        }

        if (placed && appear > 0.01f) {
            var lastTap by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .offset {
                        val half = size.toPx() / 2f
                        IntOffset((center.value.x - half).roundToInt(), (center.value.y - half).roundToInt())
                    }
                    .size(size)
                    .graphicsLayer {
                        val s = 0.6f + 0.4f * appear
                        scaleX = s
                        scaleY = s
                        alpha = appear
                    }
                    .pressScale(interaction, 0.9f)
                    .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(c.accentHighlight, c.accent)))
                    .combinedClickable(
                        enabled = visible,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Add expense",
                        onLongClickLabel = "Add by voice",
                        onLongClick = {
                            if (motion.progress == 0f) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onVoice()
                            }
                        },
                        onClick = {
                            // One Quick Add at a time, however fast the taps.
                            val now = SystemClock.uptimeMillis()
                            if (motion.progress == 0f && now - lastTap > 600) {
                                lastTap = now
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onAdd()
                            }
                        },
                    )
                    .semantics { contentDescription = "Add expense" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = c.onAccent,
                    modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = 45f * motion.progress },
                )
            }
        }
    }
}
