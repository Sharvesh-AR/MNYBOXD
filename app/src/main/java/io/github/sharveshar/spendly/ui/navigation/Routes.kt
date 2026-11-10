// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.sharveshar.spendly.AppContainer
import io.github.sharveshar.spendly.appContainer
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object TransactionsRoute
@Serializable object AccountsRoute
@Serializable object BudgetsRoute
@Serializable object InsightsRoute
@Serializable object SettingsRoute
@Serializable object CategoriesRoute
@Serializable object AboutRoute
@Serializable object ThemesRoute
@Serializable object ShortcutsRoute
@Serializable object ResetRoute
@Serializable object WidgetsRoute

/** Empty, transparent start screen of QuickAddActivity; Quick Add opens on top of it. */
@Serializable object PopupBaseRoute

/**
 * Quick Add / edit form.
 * @param editId transaction to edit (0 = new)
 * @param refundOf expense being refunded (0 = none)
 * @param startVoice open voice capture immediately
 * @param type initial transaction type name
 * @param accountId preselected account (0 = remembered/default)
 */
@Serializable
data class EntryRoute(
    val editId: Long = 0L,
    val refundOf: Long = 0L,
    val startVoice: Boolean = false,
    val type: String = "EXPENSE",
    val accountId: Long = 0L,
    /** Shared-element key of the control that opened the form; "" = no origin (tile, shortcut). */
    val origin: String = "",
)

@Serializable data class TransactionDetailRoute(val id: Long)
@Serializable data class AccountDetailRoute(val id: Long)
@Serializable data class AccountEditRoute(val id: Long = 0L)

/** The account editor opened from inside Quick Add: a full-screen dialog stacked on the sheet. */
@Serializable data class AccountEditSheetRoute(val id: Long = 0L)

/** Lets tests supply a container backed by an in-memory database. Production uses the Application's. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer?> { null }

/** Creates a ViewModel with access to the app container and its SavedStateHandle (nav args). */
@Composable
inline fun <reified VM : ViewModel> spendlyViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = LocalAppContainer.current ?: LocalContext.current.appContainer
    return viewModel(factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}
