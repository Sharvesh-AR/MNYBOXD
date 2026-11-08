package io.github.sharveshar.spendly.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.ui.components.SubScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.sharveshar.spendly.shortcut.ButtonShortcut
import io.github.sharveshar.spendly.shortcut.ShortcutButton
import io.github.sharveshar.spendly.shortcut.ShortcutSettings
import io.github.sharveshar.spendly.ui.components.OriginPopup
import io.github.sharveshar.spendly.ui.components.SegmentedSelector
import io.github.sharveshar.spendly.ui.components.originAnchor
import io.github.sharveshar.spendly.ui.components.rememberOriginAnchor
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly

/** "Press volume down 3 times" */
internal fun ButtonShortcut.describe(): String {
    val button = when (button) {
        ShortcutButton.VOLUME_DOWN -> "volume down"
        ShortcutButton.VOLUME_UP -> "volume up"
    }
    return if (presses == 2) "Press $button twice" else "Press $button $presses times"
}

/**
 * Ways to open Quick Add from anywhere: an optional "Add expense" app-list entry for the phone's
 * own gestures and, in the volumeShortcut build only, a volume-button pattern (opt-in
 * accessibility service; see [ShortcutSettings.isButtonShortcutAvailable]).
 */
@Composable
internal fun ShortcutsSection(
    shortcut: ButtonShortcut,
    onButtonChange: (ShortcutButton) -> Unit,
    onPressesChange: (Int) -> Unit,
    onTurnOff: () -> Unit = {},
) {
    val context = LocalContext.current
    // Both states live in Android, not in Spendly: re-read whenever the user comes back.
    var serviceOn by remember { mutableStateOf(ShortcutSettings.isButtonShortcutOn(context)) }
    var iconOn by remember { mutableStateOf(ShortcutSettings.isAppIconOn(context)) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val anchor = rememberOriginAnchor()
    LifecycleResumeEffect(Unit) {
        serviceOn = ShortcutSettings.isButtonShortcutOn(context)
        iconOn = ShortcutSettings.isAppIconOn(context)
        onPauseOrDispose { }
    }

    // Turning off happens in the service itself (disableSelf); re-check once it has had a moment.
    var turningOff by remember { mutableStateOf(false) }
    LaunchedEffect(turningOff) {
        if (turningOff) {
            repeat(10) {
                kotlinx.coroutines.delay(200)
                serviceOn = ShortcutSettings.isButtonShortcutOn(context)
                if (!serviceOn) return@LaunchedEffect
            }
            turningOff = false
        }
    }

    if (ShortcutSettings.isButtonShortcutAvailable) {
        Group("Volume-button shortcut (optional)")
        Row(
            Modifier
                .fillMaxWidth()
                .originAnchor(anchor)
                .clickable(role = Role.Button, onClickLabel = if (serviceOn) "Manage" else "Set up") { explain = true }
                .heightIn(min = 64.dp)
                .padding(horizontal = Space.gutter, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = null, tint = Spendly.colors.muted)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text("Volume button shortcut", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (serviceOn) "${shortcut.describe()} on any screen to open the Quick Add pop-up"
                    else "Open the Quick Add pop-up from any screen with a volume button. Tap to set up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Spendly.colors.muted,
                )
            }
            Spacer(Modifier.width(Space.md))
            Text(
                if (serviceOn) "On" else "Off",
                style = MaterialTheme.typography.labelLarge,
                color = if (serviceOn) MaterialTheme.colorScheme.primary else Spendly.colors.muted,
            )
        }
        Column(Modifier.padding(horizontal = Space.gutter)) {
            Caption("Button")
            SegmentedSelector(
                options = ShortcutButton.entries,
                selected = shortcut.button,
                label = { if (it == ShortcutButton.VOLUME_DOWN) "Volume down" else "Volume up" },
                onSelect = onButtonChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Caption("Presses")
            SegmentedSelector(
                options = ButtonShortcut.PRESS_CHOICES,
                selected = shortcut.presses,
                label = { if (it == 2) "Twice" else "$it times" },
                onSelect = onPressesChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
        }
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
        Icon(Icons.Rounded.AppShortcut, contentDescription = null, tint = Spendly.colors.muted)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text("“Add expense” app icon", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Adds an icon that opens Quick Add. Pick it in your phone’s gesture settings, such as back tap or double-pressing the side key, if your phone has them.",
                style = MaterialTheme.typography.bodySmall,
                color = Spendly.colors.muted,
            )
        }
        Switch(checked = iconOn, onCheckedChange = null)
    }

    if (ShortcutSettings.isButtonShortcutAvailable) {
        OriginPopup(visible = explain, anchor = anchor, onDismissRequest = { explain = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(Space.xxl)) {
                Text(if (serviceOn) "Volume button shortcut is on" else "Add expenses with a volume button", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Space.md))
                Text(
                    if (serviceOn) {
                        "${shortcut.describe()} quickly, on any screen, to open the Quick Add pop-up. Turn it off here at any time: MNYBOXD then stops receiving button presses until you switch it on again in Accessibility settings."
                    } else {
                        "${shortcut.describe()} quickly, on any screen, to open Quick Add. On the lock screen you’ll be asked to unlock first.\n\n" +
                            "Android only lets apps notice button presses outside their own screens through Accessibility settings. " +
                            "MNYBOXD uses that access for one thing: checking whether a volume button was pressed. " +
                            "It can’t see your screen, it ignores every other key, and with no internet permission nothing can leave your phone. " +
                            "Android will show a general warning about accessibility access when you switch it on."
                    },
                    color = Spendly.colors.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Space.md))
                Text(
                    "Your volume buttons keep working as usual. When MNYBOXD sees the pattern it opens Quick Add and puts the volume back where it was. " +
                        "It works while the screen is on and stays out of the way during calls.",
                    color = Spendly.colors.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!serviceOn) {
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "In Accessibility settings, open “MNYBOXD volume shortcut” (often under Downloaded or Installed apps) and switch it on.\n\n" +
                            "If Android says “Restricted setting”: open App info, tap ⋮, choose Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(Space.xl))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (serviceOn) {
                        TextButton(onClick = {
                            explain = false
                            context.launch(ShortcutSettings.accessibilitySettingsIntent())
                        }) { Text("Accessibility settings") }
                        Spacer(Modifier.width(Space.sm))
                        Button(onClick = {
                            explain = false
                            onTurnOff()
                            turningOff = true
                        }) { Text("Turn off") }
                    } else {
                        TextButton(onClick = { context.launch(ShortcutSettings.appInfoIntent(context)) }) { Text("App info") }
                        Spacer(Modifier.width(Space.sm))
                        Button(onClick = {
                            explain = false
                            context.launch(ShortcutSettings.accessibilitySettingsIntent())
                        }) { Text("Accessibility settings") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Spendly.colors.muted,
        modifier = Modifier.padding(top = Space.sm, bottom = Space.xs),
    )
}

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Heavily customised builds can lack these settings screens; nothing else to open.
    }
}

/** Settings → Shortcut settings: every way to open Quick Add without opening the app first. */
@Composable
fun ShortcutsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    SubScreen("Shortcut settings", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ShortcutsSection(
                shortcut = prefs.buttonShortcut,
                onButtonChange = viewModel::setShortcutButton,
                onPressesChange = viewModel::setShortcutPresses,
                onTurnOff = viewModel::turnOffButtonShortcut,
            )
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
        Icon(icon, contentDescription = null, tint = Spendly.colors.muted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
        }
    }
}
