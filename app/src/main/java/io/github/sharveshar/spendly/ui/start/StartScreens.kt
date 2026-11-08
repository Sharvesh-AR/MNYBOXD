package io.github.sharveshar.spendly.ui.start

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.data.db.DatabaseState
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.xxl),
    ) {
        Spacer(Modifier.height(72.dp))
        AnimatedVisibility(visible, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 }) {
            Column {
                Text("MNYBOXD", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Space.md))
                Text("Every rupee,\naccounted for.", style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.lg))
                Text(
                    "A calm, private expense tracker. Record spending in seconds by keypad or voice, and always know where your money is.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Spendly.colors.muted,
                )
            }
        }
        Spacer(Modifier.height(Space.xxxl))
        AnimatedVisibility(visible, enter = fadeIn(tween(500, delayMillis = 150)) + slideInVertically(tween(500, delayMillis = 150)) { it / 6 }) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                Point(Icons.Rounded.CloudOff, "Offline and on your phone", "No account, no sign-in, no internet permission, no ads or trackers.")
                Point(Icons.Rounded.Lock, "Encrypted at rest", "Your records are stored in an encrypted database, with the key protected by Android Keystore.")
                Point(Icons.Rounded.Mic, "Fast entry", "A keypad built for amounts, plus voice entry you always review before saving.")
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Space.xxxl))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
            Text("Get started", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row {
        IconBadge(icon, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Space.xxs))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Spendly.colors.muted)
        }
    }
}

/**
 * Shown while the encrypted database opens, normally a few milliseconds. The mark appears only if
 * opening takes longer than a blink (so fast starts don't flash it) and sits where Android 12+'s
 * own splash icon was, so the hand-over looks continuous. No artificial delay is ever added.
 */
@Composable
fun SplashScreen() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        show = true
    }
    val c = Spendly.colors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        AnimatedVisibility(show, enter = fadeIn(tween(260)) + androidx.compose.animation.scaleIn(tween(320), initialScale = 0.92f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Canvas(Modifier.size(72.dp)) {
                    // Spendly's mark: three receding bars, as in the app icon.
                    val unit = size.width / 9f
                    listOf(Triple(0f, 9f, c.accent), Triple(2.4f, 6f, c.secondaryAccent), Triple(4.8f, 3f, c.secondaryAccent.copy(alpha = 0.55f))).forEach { (y, w, color) ->
                        drawRoundRect(
                            color,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, (y + 1.2f) * unit),
                            size = androidx.compose.ui.geometry.Size(w * unit, 1.6f * unit),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8f * unit),
                        )
                    }
                }
                Spacer(Modifier.height(Space.lg))
                Text("MNYBOXD", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/**
 * Shown when the database can't be opened. Data is never deleted automatically: erasing
 * requires typing ERASE.
 */
@Composable
fun RecoveryScreen(state: DatabaseState, onRetry: () -> Unit, onErase: suspend () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var erasing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locked = state is DatabaseState.Locked
    val code = (state as? DatabaseState.Failed)?.code
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(Space.xxl),
    ) {
        Spacer(Modifier.height(Space.xxxl))
        IconBadge(Icons.Rounded.WarningAmber, Spendly.colors.warning, size = 56.dp)
        Spacer(Modifier.height(Space.xl))
        Text(
            when {
                locked -> "Your data can't be unlocked"
                code == "UPGRADE" -> "MNYBOXD couldn't update its database"
                code == "STORAGE" -> "Your phone is out of storage"
                else -> "MNYBOXD couldn't open your data"
            },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.md))
        Text(
            when {
                locked -> "Your records are encrypted with a key kept in this phone's secure keystore, and that key is no longer available. " +
                    "This can happen after some system resets. Your encrypted data has not been deleted.\n\n" +
                    "If you have an encrypted MNYBOXD backup, you can erase this unreadable data, start fresh, then restore the backup from Settings."
                code == "UPGRADE" -> "Nothing has been deleted. Try again, or install the latest version of MNYBOXD. If this keeps happening, please report it (see SECURITY.md / GitHub issues)."
                code == "STORAGE" -> "Free up some space on your phone and try again. Nothing has been deleted."
                else -> "Nothing has been deleted. Try again. If it keeps happening, restart your phone. (Error: ${code ?: "unknown"})"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Spendly.colors.muted,
        )
        Spacer(Modifier.height(Space.xl))
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        if (locked) {
            Spacer(Modifier.height(Space.xxl))
            Text("Erase and start fresh", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Space.xs))
            Text("Permanently deletes the unreadable data on this phone. Type ERASE to confirm.", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(typed, { typed = it.take(10) }, singleLine = true, label = { Text("Type ERASE") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.sm))
            Button(
                onClick = { erasing = true; scope.launch { onErase(); erasing = false } },
                enabled = typed == "ERASE" && !erasing,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Erase data") }
        }
    }
}
