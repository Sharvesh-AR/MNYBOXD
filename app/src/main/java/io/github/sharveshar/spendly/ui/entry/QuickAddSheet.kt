// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.ui.components.DialogWindowSetup
import io.github.sharveshar.spendly.ui.components.LocalReducedMotion
import io.github.sharveshar.spendly.ui.components.layeredSurface
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.launch

/**
 * How far the Quick Add sheet is open (0 closed … 1 open), shared with the screen behind it so
 * that screen can recede slightly while the sheet is up. Read it in draw/layout lambdas only.
 */
@Stable
class SheetMotion {
    var progress by mutableFloatStateOf(0f)
}

val LocalSheetMotion = staticCompositionLocalOf<SheetMotion?> { null }

/** Lets sheet content close the sheet with the exit animation (Close button, after Save). */
@Stable
class SheetController internal constructor(private val onClose: () -> Unit) {
    var closing by mutableStateOf(false)
        internal set

    fun close() = onClose()
}

/**
 * The Quick Add container: a tall bottom sheet over a dimmed backdrop, inside a full-screen
 * Dialog window (a navigation dialog destination, so the previous screen stays underneath).
 *
 * It enters with a spring and leaves by reversing it. Back, scrim taps, the drag handle and
 * [SheetController.close] all go through the same exit animation, and [onDismissed] is called
 * exactly once, after the sheet is fully off screen.
 */
@Composable
fun QuickAddSheet(onDismissed: () -> Unit, content: @Composable ColumnScope.(SheetController) -> Unit) {
    DialogWindowSetup(LocalView.current)
    val reduced = LocalReducedMotion.current
    val motion = LocalSheetMotion.current
    val scope = rememberCoroutineScope()
    val dismissed by rememberUpdatedState(onDismissed)

    // Coming back to the sheet (e.g. after adding an account) must not replay the entrance.
    var entered by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (entered) 1f else 0f) }
    var finished by remember { mutableStateOf(false) }

    lateinit var controller: SheetController
    controller = remember {
        SheetController {
            if (!controller.closing) {
                controller.closing = true
                scope.launch {
                    if (reduced) progress.snapTo(0f) else progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
                    if (!finished) {
                        finished = true
                        dismissed()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!entered) {
            if (reduced) progress.snapTo(1f) else progress.animateTo(1f, spring(dampingRatio = 0.84f, stiffness = 340f))
            entered = true
        }
    }
    LaunchedEffect(motion) {
        if (motion != null) snapshotFlow { progress.value }.collect { motion.progress = it }
    }
    DisposableEffect(motion) { onDispose { motion?.progress = 0f } }

    BackHandler(enabled = !controller.closing) { controller.close() }

    val scrim = Spendly.colors.scrim
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight - statusTop - 10.dp
        val sheetHeightPx = with(LocalDensity.current) { sheetHeight.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close Quick Add") {
                    controller.close()
                },
        )
        val drag = rememberDraggableState { delta ->
            if (!controller.closing) scope.launch { progress.snapTo((progress.value - delta / sheetHeightPx).coerceIn(0f, 1f)) }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .height(sheetHeight)
                .graphicsLayer {
                    translationY = (1f - progress.value) * size.height
                }
                .layeredSurface(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), Spendly.colors.background, raised = true)
                // Swallow taps so they never reach the scrim behind the sheet.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            // Drag handle: pull down to dismiss, or let go to spring back.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .draggable(
                        drag,
                        Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (progress.value < 0.72f || velocity > 1800f) controller.close()
                            else progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 420f))
                        },
                    )
                    .semantics { contentDescription = "Drag down to close" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Spendly.colors.muted.copy(alpha = 0.4f)),
                )
            }
            // The sheet sets its own text/icon colour: it may be hosted outside any Surface
            // (QuickAddActivity), where the default would be black on a dark theme.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                content(controller)
            }
        }
    }
}
