// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import io.github.sharveshar.mnyboxd.appContainer
import io.github.sharveshar.mnyboxd.data.db.DatabaseState
import io.github.sharveshar.mnyboxd.reminders.DataSafety
import io.github.sharveshar.mnyboxd.reminders.ExpenseCheckIn
import io.github.sharveshar.mnyboxd.ui.components.OriginPopup
import io.github.sharveshar.mnyboxd.ui.navigation.LocalAppContainer
import io.github.sharveshar.mnyboxd.ui.theme.Mnyboxd
import io.github.sharveshar.mnyboxd.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * What Home may ask, at most one thing per app session, a moment after Home settles and never
 * while Quick Add is open ([active] false): first, once ever, Android 13+'s notification
 * permission for Expense Check-in; otherwise the Data Safety reminder when it's due.
 */
@Composable
fun HomeReminders(active: Boolean, onExportBackup: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current ?: context.appContainer
    val scope = rememberCoroutineScope()
    var safety by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { runCatching { ExpenseCheckIn.schedule(context) } }
    }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(1_200)
        val prefs = container.preferences.preferences.first()
        val now = System.currentTimeMillis()
        container.preferences.startDataSafetyClock(now)

        val askNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && prefs.checkInEnabled && !prefs.notificationPermissionAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (askNotifications) {
            if (!DataSafety.claimSession()) return@LaunchedEffect
            container.preferences.setNotificationPermissionAsked()
            runCatching { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
            return@LaunchedEffect
        }

        if (container.databaseManager.state.value !is DatabaseState.Ready) return@LaunchedEffect
        val transactions = container.repositories.transactions
        val zone = ZoneId.systemDefault()
        val due = DataSafety.isDue(
            today = LocalDate.now(zone),
            zone = zone,
            since = prefs.dataSafetySince ?: now,
            shownAt = prefs.dataSafetyShownAt,
            lastBackupAt = prefs.lastBackupAt,
            lastChangeAt = runCatching { transactions.lastChangedAt() }.getOrNull(),
            transactionCount = runCatching { transactions.observeCount().first() }.getOrDefault(0),
        )
        if (due && DataSafety.claimSession()) {
            // Counted as shown straight away: Maybe Later, Back or a tap outside all wait the full interval.
            container.preferences.setDataSafetyShownAt(now)
            safety = true
        }
    }

    DataSafetyPopup(
        visible = safety,
        onExport = { safety = false; onExportBackup() },
        onLater = { safety = false },
    )
}

/** "Keep your money data safe." A calm reminder in the app's own pop-up style. */
@Composable
private fun DataSafetyPopup(visible: Boolean, onExport: () -> Unit, onLater: () -> Unit) {
    val c = Mnyboxd.colors
    OriginPopup(visible = visible, anchor = null, onDismissRequest = onLater) {
        Column(Modifier.padding(Space.xxl)) {
            Text(
                "Keep your money data safe.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.md))
            Text(
                "Your financial data is stored locally on this device. If you uninstall MNYBOXD, you may lose your records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "Create a backup so you can restore your data whenever you return.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text("Maybe Later") }
                Spacer(Modifier.width(Space.sm))
                Button(onClick = onExport) { Text("Export Backup") }
            }
        }
    }
}
