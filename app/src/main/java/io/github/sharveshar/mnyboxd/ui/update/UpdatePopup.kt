// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.mnyboxd.BuildConfig
import io.github.sharveshar.mnyboxd.appContainer
import io.github.sharveshar.mnyboxd.ui.components.LocalReducedMotion
import io.github.sharveshar.mnyboxd.ui.components.OriginAnchor
import io.github.sharveshar.mnyboxd.ui.components.OriginPopup
import io.github.sharveshar.mnyboxd.ui.navigation.LocalAppContainer
import io.github.sharveshar.mnyboxd.ui.theme.Mnyboxd
import io.github.sharveshar.mnyboxd.update.AppUpdater
import io.github.sharveshar.mnyboxd.update.UpdateState
import io.github.sharveshar.mnyboxd.update.UpdateState.Step
import java.util.Locale

/**
 * The "Software update" pop-up: what's new in the latest release, then a download bar, then
 * Android's installer. It grows out of [anchor] (the Settings row or the arrow on Home). It
 * shows whatever [AppUpdater] is doing; the caller starts a check when it opens it.
 */
@Composable
fun UpdatePopup(visible: Boolean, anchor: OriginAnchor?, onClose: () -> Unit) {
    val context = LocalContext.current
    val updater = (LocalAppContainer.current ?: context.appContainer).updater
    val live by updater.state.collectAsStateWithLifecycle()
    // Keep showing the last state while the pop-up closes, rather than flashing a reset.
    var shown by remember { mutableStateOf(live) }
    if (visible) shown = live

    // "Install unknown apps" has to be allowed once; carry on to the installer on return.
    var waitingForPermission by remember { mutableStateOf(false) }
    fun openInstaller() {
        updater.installIntent()?.let { runCatching { context.startActivity(it) } }
    }
    LifecycleResumeEffect(waitingForPermission) {
        if (waitingForPermission && !updater.needsInstallPermission()) {
            waitingForPermission = false
            openInstaller()
        }
        onPauseOrDispose { }
    }
    fun install() {
        if (updater.needsInstallPermission()) {
            waitingForPermission = true
            runCatching { context.startActivity(updater.installPermissionIntent()) }
        } else {
            openInstaller()
        }
    }
    fun close() {
        onClose()
        updater.dismissed()
    }

    OriginPopup(visible = visible, anchor = anchor, onDismissRequest = ::close) {
        UpdateCard(
            state = shown,
            needsPermission = shown.step is Step.Ready && updater.needsInstallPermission(),
            onDownload = updater::download,
            onCancel = updater::cancel,
            onInstall = ::install,
            onRetry = { if (shown.release != null) updater.download() else updater.check() },
            onClose = ::close,
        )
    }
}

internal data class Action(val label: String, val primary: Boolean, val onClick: () -> Unit)

/** The pop-up's content; internal so screenshot tests can draw it outside a dialog window. */
@Composable
internal fun UpdateCard(
    state: UpdateState,
    needsPermission: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Mnyboxd.colors
    val step = state.step
    val release = state.release
    val name = release?.let { "MNYBOXD ${it.version}" }
    val subtitle = when (step) {
        Step.Idle, Step.Checking -> "Checking for updates…"
        Step.UpToDate -> "You have the latest version, MNYBOXD ${BuildConfig.VERSION_NAME.substringBefore('-')}."
        Step.Available -> "$name is available to download."
        is Step.Downloading -> "Downloading $name…"
        is Step.Ready -> "$name is ready to install."
        is Step.Failed -> step.message
    }
    val actions = when (step) {
        Step.Idle, Step.Checking -> listOf(Action("Cancel", false) { onCancel(); onClose() })
        Step.UpToDate -> listOf(Action("Done", true, onClose))
        Step.Available -> listOf(Action("Download now", true, onDownload), Action("Remind me later", false, onClose))
        is Step.Downloading -> listOf(Action("Cancel", false, onCancel))
        is Step.Ready -> listOf(Action("Install now", true, onInstall), Action("Later", false, onClose))
        is Step.Failed -> listOf(Action("Try again", true, onRetry), Action("Close", false, onClose))
    }
    val reduced = LocalReducedMotion.current
    val ink = MaterialTheme.colorScheme.onSurface

    Column(Modifier.fillMaxWidth().then(if (reduced) Modifier else Modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = 500f)))) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Software update",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
            when (step) {
                Step.Checking -> {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = c.hairline,
                        strokeCap = StrokeCap.Round,
                    )
                }
                is Step.Downloading -> {
                    Spacer(Modifier.height(16.dp))
                    ProgressBar(step.fraction)
                    if (step.total > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("${megabytes(step.bytes)} of ${megabytes(step.total)}", style = MaterialTheme.typography.labelSmall, color = c.muted)
                    }
                }
                is Step.Ready -> if (needsPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android will ask you to allow MNYBOXD to install updates. Allow it, then come back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> Unit
            }
        }

        val showNotes = release != null && step !is Step.UpToDate && (release.highlights.isNotEmpty() || state.image != null)
        if (showNotes && release != null) {
            Hairline()
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
            ) {
                Text("What's new", style = MaterialTheme.typography.labelLarge, color = c.muted)
                AnimatedVisibility(visible = state.image != null, enter = fadeIn(tween(if (reduced) 0 else 300))) {
                    state.image?.let { bitmap ->
                        val picture = remember(bitmap) { bitmap.asImageBitmap() }
                        Image(
                            picture,
                            contentDescription = "What's new in ${release.version}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, c.hairline, RoundedCornerShape(16.dp)),
                        )
                    }
                }
                if (release.highlights.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text("Top features", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = ink)
                    Spacer(Modifier.height(8.dp))
                    release.highlights.forEach { line ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyLarge, color = ink)
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = MaterialTheme.typography.bodyLarge, color = ink)
                        }
                    }
                }
            }
        }

        actions.forEach { action ->
            Hairline()
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = action.onClick)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    action.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (action.primary) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (action.primary) ink else c.muted,
                )
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Mnyboxd.colors.hairline))
}

/** A thin, rounded bar; the fill glides to each new value. */
@Composable
private fun ProgressBar(fraction: Float) {
    val reduced = LocalReducedMotion.current
    val shown by animateFloatAsState(fraction, if (reduced) tween(0) else tween(250), label = "download")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Mnyboxd.colors.hairline)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
