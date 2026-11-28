// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.mnyboxd.BuildConfig
import io.github.sharveshar.mnyboxd.appContainer
import io.github.sharveshar.mnyboxd.ui.components.LocalReducedMotion
import io.github.sharveshar.mnyboxd.ui.components.OriginAnchor
import io.github.sharveshar.mnyboxd.ui.components.Wordmark
import io.github.sharveshar.mnyboxd.ui.components.originAnchor
import io.github.sharveshar.mnyboxd.ui.components.pressScale
import io.github.sharveshar.mnyboxd.ui.components.rememberOriginAnchor
import io.github.sharveshar.mnyboxd.ui.navigation.LocalAppContainer
import io.github.sharveshar.mnyboxd.ui.theme.Mnyboxd
import io.github.sharveshar.mnyboxd.ui.theme.Space
import io.github.sharveshar.mnyboxd.ui.components.LocalWordmarkIntro
import io.github.sharveshar.mnyboxd.ui.components.WordmarkIntro
import io.github.sharveshar.mnyboxd.ui.update.UpdatePopup
import io.github.sharveshar.mnyboxd.update.Releases
import io.github.sharveshar.mnyboxd.update.UpdateState
import kotlinx.coroutines.flow.map

/**
 * Home's top row: the wordmark, and one capsule on the right holding the update arrow (only when
 * an update has been found), the budget bell and the way into Settings.
 */
@Composable
fun HomeHeader(alertCount: Int, bell: OriginAnchor, onAlerts: () -> Unit, onSettings: () -> Unit) {
    val container = LocalAppContainer.current ?: LocalContext.current.appContainer
    val updater = container.updater
    val known by remember(container) { container.preferences.preferences.map { it.updateAvailable } }.collectAsStateWithLifecycle(null)
    val live by updater.state.collectAsStateWithLifecycle()
    // Shown after a check found a newer version, until it's installed. Never checks by itself.
    val update = known?.let { Releases.isNewer(it, BuildConfig.VERSION_NAME) } == true ||
        live.step is UpdateState.Step.Available || live.step is UpdateState.Step.Downloading || live.step is UpdateState.Step.Ready
    val updateAnchor = rememberOriginAnchor()
    var showUpdate by remember { mutableStateOf(false) }
    val c = Mnyboxd.colors
    val reduced = LocalReducedMotion.current
    // A fresh launch's first Home plays the wordmark intro, once; it's used up straight away, so
    // leaving Home mid-way or coming back later shows the plain wordmark.
    val launch = LocalWordmarkIntro.current
    var intro by remember { mutableStateOf(launch?.value == true && !reduced) }
    LaunchedEffect(Unit) { launch?.value = false }

    Row(
        Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter - Space.xs, top = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (intro) {
            WordmarkIntro(onFinished = { intro = false }, modifier = Modifier.semantics { heading() })
        } else {
            Wordmark(Modifier.semantics { heading() })
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .height(48.dp)
                .background(c.card, CircleShape)
                .border(1.dp, c.hairline, CircleShape)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = update,
                enter = if (reduced) fadeIn(tween(0)) else expandHorizontally(spring(dampingRatio = 0.85f, stiffness = 500f)) + fadeIn(tween(220)),
                exit = if (reduced) fadeOut(tween(0)) else shrinkHorizontally(tween(220)) + fadeOut(tween(160)),
            ) {
                CapsuleButton(Icons.Rounded.Upgrade, "Update available", Modifier.originAnchor(updateAnchor)) {
                    showUpdate = true
                    updater.check()
                }
            }
            CapsuleButton(
                Icons.Outlined.NotificationsNone,
                if (alertCount == 0) "Alerts, none" else "Alerts, $alertCount",
                Modifier.originAnchor(bell),
                dot = alertCount > 0,
                onClick = onAlerts,
            )
            // You, and everything about the app: Settings.
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .pressScale(interaction, 0.9f)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(36.dp).background(c.cardElevated, CircleShape).border(1.dp, c.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    UpdatePopup(visible = showUpdate, anchor = updateAnchor, onClose = { showUpdate = false })
}

/** One icon in the header capsule: the full capsule height to tap, the icon at 22dp. */
@Composable
private fun CapsuleButton(icon: ImageVector, description: String, modifier: Modifier = Modifier, dot: Boolean = false, onClick: () -> Unit) {
    val c = Mnyboxd.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxHeight()
            .width(44.dp)
            .pressScale(interaction, 0.88f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            if (dot) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp).size(8.dp).background(c.negative, CircleShape).border(1.5.dp, c.card, CircleShape))
            }
        }
    }
}
