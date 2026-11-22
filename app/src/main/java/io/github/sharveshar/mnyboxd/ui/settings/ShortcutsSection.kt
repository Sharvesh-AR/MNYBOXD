// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.sharveshar.mnyboxd.shortcut.ShortcutSettings
import io.github.sharveshar.mnyboxd.ui.components.SubScreen
import io.github.sharveshar.mnyboxd.ui.theme.Space
import io.github.sharveshar.mnyboxd.ui.theme.Mnyboxd

/**
 * The optional "Add expense" app-list entry, for the phone's own gestures (back tap,
 * double-pressing the side key and the like) that can only open an app.
 */
@Composable
internal fun ShortcutsSection() {
    val context = LocalContext.current
    // The switch's state lives in Android, not in MNYBOXD: re-read whenever the user comes back.
    var iconOn by remember { mutableStateOf(ShortcutSettings.isAppIconOn(context)) }
    LifecycleResumeEffect(Unit) {
        iconOn = ShortcutSettings.isAppIconOn(context)
        onPauseOrDispose { }
    }

    Group("Phone gestures")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) {
                ShortcutSettings.setAppIconOn(context, !iconOn)
                iconOn = ShortcutSettings.isAppIconOn(context)
            }
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AppShortcut, contentDescription = null, tint = Mnyboxd.colors.muted)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text("“Add expense” app icon", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Adds an icon that opens Quick Add. Pick it in your phone’s gesture settings, such as back tap or double-pressing the side key, if your phone has them.",
                style = MaterialTheme.typography.bodySmall,
                color = Mnyboxd.colors.muted,
            )
        }
        Switch(checked = iconOn, onCheckedChange = null)
    }
}

/** Settings → Shortcut settings: every way to open Quick Add without opening the app first. */
@Composable
fun ShortcutsScreen(onBack: () -> Unit) {
    SubScreen("Shortcut settings", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ShortcutsSection()
            Group("Always available")
            InfoRow(
                Icons.Rounded.GridView,
                "Quick Settings tile",
                "Swipe down twice from the top of the screen, tap the pencil (Edit) and drag “Add expense” into your tiles.",
            )
            InfoRow(
                Icons.Rounded.TouchApp,
                "App shortcuts",
                "Touch and hold the MNYBOXD icon for Add expense and Voice expense. You can drag either one to your home screen.",
            )
            InfoRow(
                Icons.Rounded.Widgets,
                "Home-screen widgets",
                "Touch and hold an empty part of your home screen, tap Widgets and find MNYBOXD.",
            )
            Spacer(Modifier.height(Space.xxxl))
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Mnyboxd.colors.muted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Mnyboxd.colors.muted)
        }
    }
}
