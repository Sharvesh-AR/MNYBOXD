package io.github.sharveshar.spendly.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.sharveshar.spendly.AppContainer
import io.github.sharveshar.spendly.LaunchRequest
import io.github.sharveshar.spendly.data.db.DatabaseState
import io.github.sharveshar.spendly.ui.accounts.AccountDetailScreen
import io.github.sharveshar.spendly.ui.accounts.AccountEditScreen
import io.github.sharveshar.spendly.ui.accounts.AccountEditViewModel
import io.github.sharveshar.spendly.ui.accounts.AccountsScreen
import io.github.sharveshar.spendly.ui.accounts.AccountsViewModel
import io.github.sharveshar.spendly.ui.budgets.BudgetsScreen
import io.github.sharveshar.spendly.ui.budgets.BudgetsViewModel
import io.github.sharveshar.spendly.ui.components.LocalSharedTransitionScope
import io.github.sharveshar.spendly.ui.components.ProvideNavAnimatedScope
import io.github.sharveshar.spendly.ui.components.pressScale
import io.github.sharveshar.spendly.ui.components.sharedContainer
import io.github.sharveshar.spendly.ui.entry.EntryScreen
import io.github.sharveshar.spendly.ui.entry.EntryViewModel
import io.github.sharveshar.spendly.ui.home.HomeScreen
import io.github.sharveshar.spendly.ui.home.HomeViewModel
import io.github.sharveshar.spendly.ui.insights.InsightsScreen
import io.github.sharveshar.spendly.ui.insights.InsightsViewModel
import io.github.sharveshar.spendly.ui.navigation.AboutRoute
import io.github.sharveshar.spendly.ui.navigation.AccountDetailRoute
import io.github.sharveshar.spendly.ui.navigation.AccountEditRoute
import io.github.sharveshar.spendly.ui.navigation.AccountsRoute
import io.github.sharveshar.spendly.ui.navigation.BudgetsRoute
import io.github.sharveshar.spendly.ui.navigation.CategoriesRoute
import io.github.sharveshar.spendly.ui.navigation.EntryRoute
import io.github.sharveshar.spendly.ui.navigation.HomeRoute
import io.github.sharveshar.spendly.ui.navigation.InsightsRoute
import io.github.sharveshar.spendly.ui.navigation.SettingsRoute
import io.github.sharveshar.spendly.ui.navigation.TransactionDetailRoute
import io.github.sharveshar.spendly.ui.navigation.TransactionsRoute
import io.github.sharveshar.spendly.ui.navigation.spendlyViewModel
import io.github.sharveshar.spendly.ui.settings.AboutScreen
import io.github.sharveshar.spendly.ui.settings.CategoriesScreen
import io.github.sharveshar.spendly.ui.settings.CategoriesViewModel
import io.github.sharveshar.spendly.ui.settings.SettingsScreen
import io.github.sharveshar.spendly.ui.settings.SettingsViewModel
import io.github.sharveshar.spendly.ui.start.RecoveryScreen
import io.github.sharveshar.spendly.ui.start.SplashScreen
import io.github.sharveshar.spendly.ui.start.WelcomeScreen
import io.github.sharveshar.spendly.ui.theme.Spendly
import io.github.sharveshar.spendly.ui.transactions.TransactionDetailScreen
import io.github.sharveshar.spendly.ui.transactions.TransactionDetailViewModel
import io.github.sharveshar.spendly.ui.transactions.TransactionsScreen
import io.github.sharveshar.spendly.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(TransactionsRoute, TransactionsRoute::class, "Activity", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    Tab(AccountsRoute, AccountsRoute::class, "Accounts", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    Tab(BudgetsRoute, BudgetsRoute::class, "Budgets", Icons.Outlined.DonutLarge, Icons.Rounded.DonutLarge),
    Tab(InsightsRoute, InsightsRoute::class, "Insights", Icons.Outlined.Insights, Icons.Rounded.Insights),
)

private fun NavDestination?.isTopLevel() = this != null && tabs.any { t -> hierarchy.any { it.hasRoute(t.routeClass) } }

@Composable
fun SpendlyApp(container: AppContainer, launchRequests: Flow<LaunchRequest>) {
    val dbState by container.databaseManager.state.collectAsStateWithLifecycle()
    val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Root surface: paints the theme background and sets the default content colour for all text/icons.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    when (val s = dbState) {
        DatabaseState.Opening -> SplashScreen()
        is DatabaseState.Locked, is DatabaseState.Failed -> RecoveryScreen(
            state = s,
            onRetry = { scope.launch { container.databaseManager.open() } },
            onErase = { container.databaseManager.eraseEverything() },
        )
        is DatabaseState.Ready -> {
            val p = prefs
            when {
                p == null -> SplashScreen()
                !p.onboardingDone -> WelcomeScreen(onStart = { scope.launch { container.preferences.setOnboardingDone() } })
                else -> MainShell(launchRequests)
            }
        }
    }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainShell(launchRequests: Flow<LaunchRequest>) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val topLevel = entry?.destination.isTopLevel()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tabPadding = PaddingValues(top = statusInset, bottom = 80.dp + navInset)
    val context = LocalContext.current

    LaunchedEffect(nav) {
        launchRequests.collect { r ->
            nav.navigate(EntryRoute(startVoice = r == LaunchRequest.VOICE)) { launchSingleTop = true }
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { enterFor(initialState, targetState) },
                    exitTransition = { exitFor(initialState, targetState) },
                    popEnterTransition = { popEnterFor(initialState, targetState) },
                    popExitTransition = { popExitFor(initialState, targetState) },
                ) {
                    composable<HomeRoute> {
                        ProvideNavAnimatedScope(this) {
                            TabFrame(onAdd = { nav.navigate(EntryRoute(origin = "fab")) }) {
                                HomeScreen(
                                    viewModel = spendlyViewModel { c, _ -> HomeViewModel(c.repositories) },
                                    contentPadding = tabPadding,
                                    onAdd = { nav.navigate(EntryRoute(origin = "home-add")) },
                                    onVoice = { nav.navigate(EntryRoute(origin = "home-voice", startVoice = true)) },
                                    onOpenTransaction = { nav.navigate(TransactionDetailRoute(it)) },
                                    onSeeAllTransactions = { nav.switchTab(TransactionsRoute) },
                                    onOpenAccount = { nav.navigate(AccountDetailRoute(it)) },
                                    onSeeAccounts = { nav.switchTab(AccountsRoute) },
                                    onBudgets = { nav.switchTab(BudgetsRoute) },
                                    onSettings = { nav.navigate(SettingsRoute) },
                                )
                            }
                        }
                    }
                    composable<TransactionsRoute> {
                        ProvideNavAnimatedScope(this) {
                            TabFrame(onAdd = { nav.navigate(EntryRoute(origin = "fab")) }) {
                                TransactionsScreen(
                                    viewModel = spendlyViewModel { c, h -> TransactionsViewModel(c.repositories, h) },
                                    contentPadding = tabPadding,
                                    onOpen = { nav.navigate(TransactionDetailRoute(it)) },
                                )
                            }
                        }
                    }
                    composable<AccountsRoute> {
                        ProvideNavAnimatedScope(this) {
                            TabFrame(onAdd = { nav.navigate(EntryRoute(origin = "fab")) }) {
                                AccountsScreen(
                                    viewModel = spendlyViewModel { c, _ -> AccountsViewModel(c.repositories) },
                                    contentPadding = tabPadding,
                                    onOpen = { nav.navigate(AccountDetailRoute(it)) },
                                    onAdd = { nav.navigate(AccountEditRoute()) },
                                )
                            }
                        }
                    }
                    composable<BudgetsRoute> {
                        ProvideNavAnimatedScope(this) {
                            TabFrame(onAdd = { nav.navigate(EntryRoute(origin = "fab")) }) {
                                BudgetsScreen(spendlyViewModel { c, h -> BudgetsViewModel(c.repositories, h) }, tabPadding)
                            }
                        }
                    }
                    composable<InsightsRoute> {
                        ProvideNavAnimatedScope(this) {
                            TabFrame(onAdd = { nav.navigate(EntryRoute(origin = "fab")) }) {
                                InsightsScreen(spendlyViewModel { c, h -> InsightsViewModel(c.repositories, h) }, tabPadding)
                            }
                        }
                    }
                    composable<EntryRoute> { back ->
                        val route = back.toRoute<EntryRoute>()
                        ProvideNavAnimatedScope(this) {
                            EntryScreen(
                                viewModel = spendlyViewModel { c, h -> EntryViewModel(c.repositories, c.preferences, h) },
                                startVoice = route.startVoice,
                                onClose = { nav.popBackStack() },
                                onAddAccount = { nav.navigate(AccountEditRoute()) },
                                modifier = if (route.origin.isNotEmpty()) Modifier.sharedContainer("entry-${route.origin}", 24) else Modifier,
                            )
                        }
                    }
                    composable<TransactionDetailRoute> {
                        ProvideNavAnimatedScope(this) {
                            TransactionDetailScreen(
                                viewModel = spendlyViewModel { c, h -> TransactionDetailViewModel(c.repositories, h) },
                                onBack = { nav.popBackStack() },
                                onEdit = { nav.navigate(EntryRoute(editId = it, origin = "detail-edit")) },
                                onRefund = { nav.navigate(EntryRoute(refundOf = it, origin = "detail-refund")) },
                                onOpen = { nav.navigate(TransactionDetailRoute(it)) },
                            )
                        }
                    }
                    composable<AccountDetailRoute> { back ->
                        val id = back.toRoute<AccountDetailRoute>().id
                        ProvideNavAnimatedScope(this) {
                            AccountDetailScreen(
                                accountId = id,
                                accountsViewModel = spendlyViewModel { c, _ -> AccountsViewModel(c.repositories) },
                                transactionsViewModel = spendlyViewModel { c, h -> TransactionsViewModel(c.repositories, h, fixedAccountId = id) },
                                onBack = { nav.popBackStack() },
                                onEdit = { nav.navigate(AccountEditRoute(id)) },
                                onAddHere = { nav.navigate(EntryRoute(accountId = id, origin = "account-add")) },
                                onOpenTransaction = { nav.navigate(TransactionDetailRoute(it)) },
                            )
                        }
                    }
                    composable<AccountEditRoute> {
                        AccountEditScreen(
                            viewModel = spendlyViewModel { c, h -> AccountEditViewModel(c.repositories, h) },
                            onDone = { nav.popBackStack() },
                            onDeleted = { nav.popBackStack(AccountsRoute, inclusive = false) || nav.popBackStack() },
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            viewModel = spendlyViewModel { c, _ -> SettingsViewModel(c.repositories, c.preferences, context.contentResolver) },
                            onBack = { nav.popBackStack() },
                            onCategories = { nav.navigate(CategoriesRoute) },
                            onAbout = { nav.navigate(AboutRoute) },
                        )
                    }
                    composable<CategoriesRoute> {
                        CategoriesScreen(spendlyViewModel { c, _ -> CategoriesViewModel(c.repositories) }, onBack = { nav.popBackStack() })
                    }
                    composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
                }

                AnimatedVisibility(
                    visible = topLevel,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    BottomBar(entry, onSelect = { nav.switchTab(it) })
                }
            }
        }
    }
}

/** Places the Quick Add button above the bottom bar for top-level screens. */
@Composable
private fun TabFrame(onAdd: () -> Unit, content: @Composable () -> Unit) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val haptics = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize()) {
        content()
        val interaction = remember { MutableInteractionSource() }
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onAdd()
            },
            interactionSource = interaction,
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 80.dp + navInset + 16.dp)
                .pressScale(interaction, 0.92f)
                .sharedContainer("entry-fab", 20)
                .size(60.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Quick add", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun BottomBar(entry: NavBackStackEntry?, onSelect: (Any) -> Unit) {
    val haptics = LocalHapticFeedback.current
    NavigationBar(containerColor = Spendly.colors.card, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = entry?.destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(tab.route)
                },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = Spendly.colors.cardElevated,
                    unselectedIconColor = Spendly.colors.muted,
                    unselectedTextColor = Spendly.colors.muted,
                ),
            )
        }
    }
}

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// --- Transitions ------------------------------------------------------------------------------

private fun NavBackStackEntry.isEntry() = destination.hasRoute(EntryRoute::class)
private fun NavBackStackEntry.hasOrigin() = isEntry() && toRoute<EntryRoute>().origin.isNotEmpty()
private fun NavBackStackEntry.isTop() = destination.isTopLevel()

private const val FADE_IN = 220
private const val FADE_OUT = 160

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition = when {
    from.isTop() && to.isTop() -> fadeIn(tween(FADE_IN, delayMillis = 60)) + scaleIn(tween(FADE_IN, delayMillis = 60), initialScale = 0.985f)
    to.hasOrigin() -> fadeIn(tween(FADE_IN))
    to.isEntry() -> slideInVertically(tween(320)) { it / 6 } + fadeIn(tween(FADE_IN))
    else -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) { it / 8 } + fadeIn(tween(FADE_IN))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition = when {
    from.isTop() && to.isTop() -> fadeOut(tween(FADE_OUT))
    else -> fadeOut(tween(FADE_OUT + 120))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterFor(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition =
    fadeIn(tween(FADE_IN))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition = when {
    from.hasOrigin() -> fadeOut(tween(FADE_OUT))
    from.isEntry() -> slideOutVertically(tween(280)) { it / 6 } + fadeOut(tween(FADE_OUT + 60))
    else -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) { it / 8 } + fadeOut(tween(FADE_OUT + 60))
}
