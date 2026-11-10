// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide messages with an optional action (Undo), shown above the navigation dock. Lives in the
 * app shell, so a message can outlive the screen that triggered it (e.g. a deleted transaction's
 * detail screen closes, then "Undo" appears on the list underneath).
 */
@Stable
class AppMessages(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    /** Shows [message] with an Undo button; [onUndo] runs in the shell's scope if it's tapped. */
    fun showUndo(message: String, onUndo: suspend () -> Unit) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
}

val LocalAppMessages = staticCompositionLocalOf<AppMessages?> { null }
