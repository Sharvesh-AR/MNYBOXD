package io.github.sharveshar.spendly.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import io.github.sharveshar.spendly.ui.components.pressScale
import io.github.sharveshar.spendly.ui.components.sharedContainer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import io.github.sharveshar.spendly.AppTab
import io.github.sharveshar.spendly.ui.components.LocalReducedMotion
import io.github.sharveshar.spendly.ui.navigation.DockOrder
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.FloatingWindow
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import io.github.sharveshar.spendly.ui.components.AppMessages
import io.github.sharveshar.spendly.ui.components.LocalAddButtonSlot
import io.github.sharveshar.spendly.ui.components.GlidingAddButton
import io.github.sharveshar.spendly.ui.components.AddButtonCornerSize
import io.github.sharveshar.spendly.ui.components.AddButtonSlot
import io.github.sharveshar.spendly.ui.components.DialogWindowSetup
import io.github.sharveshar.spendly.ui.components.LocalAppMessages
import io.github.sharveshar.spendly.ui.components.layeredSurface
import io.github.sharveshar.spendly.ui.entry.LocalSheetMotion
import io.github.sharveshar.spendly.ui.entry.SheetMotion
import io.github.sharveshar.spendly.ui.navigation.AccountEditSheetRoute
import io.github.sharveshar.spendly.ui.theme.Space
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
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import io.github.sharveshar.spendly.ui.navigation.ResetRoute
import io.github.sharveshar.spendly.ui.navigation.ShortcutsRoute
import io.github.sharveshar.spendly.ui.navigation.ThemesRoute
import io.github.sharveshar.spendly.ui.navigation.WidgetsRoute
import io.github.sharveshar.spendly.ui.navigation.TransactionDetailRoute
import io.github.sharveshar.spendly.ui.navigation.TransactionsRoute
import io.github.sharveshar.spendly.ui.navigation.spendlyViewModel
import io.github.sharveshar.spendly.ui.settings.AboutScreen
import io.github.sharveshar.spendly.ui.settings.CategoriesScreen
import io.github.sharveshar.spendly.ui.settings.CategoriesViewModel
import io.github.sharveshar.spendly.ui.settings.SettingsScreen
import io.github.sharveshar.spendly.ui.settings.SettingsViewModel
import io.github.sharveshar.spendly.ui.settings.ResetScreen
import io.github.sharveshar.spendly.ui.settings.ResetViewModel
import io.github.sharveshar.spendly.ui.settings.ShortcutsScreen
import io.github.sharveshar.spendly.ui.settings.ThemesScreen
import io.github.sharveshar.spendly.ui.settings.WidgetsScreen
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

private data class Tab(val key: AppTab, val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(AppTab.HOME, HomeRoute, HomeRoute::class, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(AppTab.ACTIVITY, TransactionsRoute, TransactionsRoute::class, "Activity", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    Tab(AppTab.ACCOUNTS, AccountsRoute, AccountsRoute::class, "Accounts", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    Tab(AppTab.BUDGETS, BudgetsRoute, BudgetsRoute::class, "Budgets", Icons.Outlined.DonutLarge, Icons.Rounded.DonutLarge),
    Tab(AppTab.INSIGHTS, InsightsRoute, InsightsRoute::class, "Insights", Icons.Outlined.Insights, Icons.Rounded.Insights),
)

private fun tabFor(key: AppTab) = tabs.first { it.key == key }

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
                else -> MainShell(
                    launchRequests = launchRequests,
                    dockOrder = p.dockOrder,
                    onDockReorder = { order -> scope.launch { container.preferences.setDockOrder(order) } },
                )
            }
        }
    }
    }
}

/** Height of the floating navigation dock, and its gap above the system navigation area. */
private val DockHeight = 64.dp
private val DockMargin = 12.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainShell(launchRequests: Flow<LaunchRequest>, dockOrder: List<AppTab>, onDockReorder: (List<AppTab>) -> Unit) {
    val nav = rememberNavController()
    val top by nav.currentBackStackEntryAsState()
    // The screen underneath any sheet decides whether the dock and Quick Add button show, so
    // they stay in place (dimmed) behind Quick Add instead of sliding away. Dialogs (Quick Add)
    // sit on top of it, and opening a full screen from a dialog closes the dialog first, so the
    // last full-screen entry seen is always the one underneath.
    val baseHolder = remember { arrayOfNulls<NavBackStackEntry>(1) }
    top?.let { if (it.destination !is FloatingWindow) baseHolder[0] = it }
    val base = baseHolder[0]
    val topLevel = base?.destination.isTopLevel()
    val selectedTab = tabs.firstOrNull { t -> base?.destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }?.key
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bandHeight = DockHeight + DockMargin + navInset
    // Tab screens scroll clear of the dock (and, off Home, the + above it): nothing ends up hidden.
    val tabPadding = PaddingValues(top = statusInset, bottom = bandHeight + Space.md)
    val tabPaddingWithAdd = PaddingValues(top = statusInset, bottom = bandHeight + Space.md + AddButtonCornerSize + Space.md)
    val addSlot = remember { AddButtonSlot() }
    val onHome = selectedTab == AppTab.HOME
    val context = LocalContext.current
    val sheetMotion = remember { SheetMotion() }
    val snackbar = remember { SnackbarHostState() }
    val shellScope = rememberCoroutineScope()
    val messages = remember { AppMessages(snackbar, shellScope) }

    fun openQuickAdd(route: EntryRoute) {
        // One Quick Add at a time: repeated taps or launches never stack sheets.
        if (nav.currentBackStackEntry?.destination?.hasRoute(EntryRoute::class) == true) return
        nav.navigate(route) { launchSingleTop = true }
    }

    LaunchedEffect(nav) {
        launchRequests.collect { r ->
            when (r) {
                LaunchRequest.QuickAdd -> openQuickAdd(EntryRoute())
                LaunchRequest.Voice -> openQuickAdd(EntryRoute(startVoice = true))
                is LaunchRequest.Open -> nav.switchTab(tabFor(r.tab).route)
                is LaunchRequest.OpenAccount -> {
                    nav.switchTab(AccountsRoute)
                    nav.navigate(AccountDetailRoute(r.id)) { launchSingleTop = true }
                }
            }
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalSheetMotion provides sheetMotion, LocalAppMessages provides messages, LocalAddButtonSlot provides addSlot) {
            val background = MaterialTheme.colorScheme.background
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                // The app recedes slightly while Quick Add is open (driven by the sheet's own motion).
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = sheetMotion.progress
                            if (p > 0f) {
                                val scale = 1f - 0.05f * p
                                scaleX = scale
                                scaleY = scale
                                shape = RoundedCornerShape((28f * p).dp)
                                clip = true
                            }
                        }
                        .background(background),
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = HomeRoute,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { enterFor(initialState, targetState) },
                        exitTransition = { exitFor(initialState, targetState) },
                        popEnterTransition = { popEnterFor() },
                        popExitTransition = { popExitFor(initialState, targetState) },
                    ) {
                        composable<HomeRoute> {
                            ProvideNavAnimatedScope(this) {
                                HomeScreen(
                                    viewModel = spendlyViewModel { c, _ -> HomeViewModel(c.repositories) },
                                    contentPadding = tabPadding,
                                    onOpenTransaction = { nav.navigate(TransactionDetailRoute(it)) },
                                    onSeeAllTransactions = { nav.switchTab(TransactionsRoute) },
                                    onSeeAccounts = { nav.switchTab(AccountsRoute) },
                                    onBudgets = { nav.switchTab(BudgetsRoute) },
                                    onSettings = { nav.navigate(SettingsRoute) },
                                )
                            }
                        }
                        composable<TransactionsRoute> {
                            ProvideNavAnimatedScope(this) {
                                TransactionsScreen(
                                    viewModel = spendlyViewModel { c, h -> TransactionsViewModel(c.repositories, h) },
                                    contentPadding = tabPaddingWithAdd,
                                    onOpen = { nav.navigate(TransactionDetailRoute(it)) },
                                )
                            }
                        }
                        composable<AccountsRoute> {
                            ProvideNavAnimatedScope(this) {
                                AccountsScreen(
                                    viewModel = spendlyViewModel { c, _ -> AccountsViewModel(c.repositories) },
                                    contentPadding = tabPaddingWithAdd,
                                    onOpen = { nav.navigate(AccountDetailRoute(it)) },
                                    onAdd = { nav.navigate(AccountEditRoute()) },
                                )
                            }
                        }
                        composable<BudgetsRoute> {
                            ProvideNavAnimatedScope(this) {
                                BudgetsScreen(spendlyViewModel { c, h -> BudgetsViewModel(c.repositories, h) }, tabPaddingWithAdd)
                            }
                        }
                        composable<InsightsRoute> {
                            ProvideNavAnimatedScope(this) {
                                InsightsScreen(spendlyViewModel { c, h -> InsightsViewModel(c.repositories, h) }, tabPaddingWithAdd)
                            }
                        }
                        quickAddDestinations(nav)
                        composable<TransactionDetailRoute> {
                            ProvideNavAnimatedScope(this) {
                                TransactionDetailScreen(
                                    viewModel = spendlyViewModel { c, h -> TransactionDetailViewModel(c.repositories, h) },
                                    onBack = { nav.popBackStack() },
                                    onEdit = { openQuickAdd(EntryRoute(editId = it)) },
                                    onRefund = { openQuickAdd(EntryRoute(refundOf = it)) },
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
                                    onAddHere = { openQuickAdd(EntryRoute(accountId = id)) },
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
                                onThemes = { nav.navigate(ThemesRoute) },
                                onShortcuts = { nav.navigate(ShortcutsRoute) },
                                onWidgets = { nav.navigate(WidgetsRoute) },
                                onCategories = { nav.navigate(CategoriesRoute) },
                                onAbout = { nav.navigate(AboutRoute) },
                                onReset = { nav.navigate(ResetRoute) },
                            )
                        }
                        composable<CategoriesRoute> {
                            CategoriesScreen(spendlyViewModel { c, _ -> CategoriesViewModel(c.repositories) }, onBack = { nav.popBackStack() })
                        }
                        composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
                        composable<ShortcutsRoute> {
                            ShortcutsScreen(
                                viewModel = spendlyViewModel { c, _ -> SettingsViewModel(c.repositories, c.preferences, context.contentResolver) },
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable<WidgetsRoute> { WidgetsScreen(onBack = { nav.popBackStack() }) }
                        composable<ResetRoute> {
                            ResetScreen(viewModel = spendlyViewModel { c, _ -> ResetViewModel(c) }, onBack = { nav.popBackStack() })
                        }
                        composable<ThemesRoute> {
                            ThemesScreen(
                                viewModel = spendlyViewModel { c, _ -> SettingsViewModel(c.repositories, c.preferences, context.contentResolver) },
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }

                    SnackbarHost(
                        snackbar,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (topLevel) bandHeight + Space.sm else navInset + Space.sm),
                    )
                    // Content fades out under the dock instead of being cut off by it.
                    AnimatedVisibility(
                        visible = topLevel,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(120)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(bandHeight + Space.xl)
                                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background.copy(alpha = 0.94f), background))),
                        )
                    }
                    AnimatedVisibility(
                        visible = topLevel,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { it } + fadeIn(),
                        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
                    ) {
                        BottomDock(
                            order = dockOrder,
                            selected = selectedTab,
                            onSelect = { nav.switchTab(tabFor(it).route) },
                            onReorder = onDockReorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = DockMargin, end = DockMargin, bottom = navInset + DockMargin),
                        )
                    }
                    // The one +: resting mid-Home, gliding to the corner above the dock on other tabs.
                    GlidingAddButton(
                        slot = addSlot,
                        onHome = onHome,
                        visible = topLevel,
                        corner = PaddingValues(end = DockMargin + Space.xs, bottom = bandHeight + Space.md),
                        motion = sheetMotion,
                        onAdd = { openQuickAdd(EntryRoute()) },
                        onVoice = { openQuickAdd(EntryRoute(startVoice = true)) },
                    )
                }
            }
        }
    }
}

private val sheetDialog = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
)

/**
 * Quick Add and the account editor it can open. Both are dialog destinations, so the screen
 * underneath stays where it was and Quick Add keeps its form while a new account is added.
 * Shared by the main app and QuickAddActivity (the pop-up used by shortcuts and widgets).
 */
fun NavGraphBuilder.quickAddDestinations(nav: NavHostController) {
    dialog<EntryRoute>(dialogProperties = sheetDialog) { back ->
        val route = back.toRoute<EntryRoute>()
        EntryScreen(
            viewModel = spendlyViewModel { c, h -> EntryViewModel(c.repositories, c.preferences, h) },
            startVoice = route.startVoice,
            onClose = { nav.popIfTop(back) },
            onAddAccount = { nav.navigate(AccountEditSheetRoute()) },
        )
    }
    dialog<AccountEditSheetRoute>(dialogProperties = sheetDialog) { back ->
        DialogWindowSetup(LocalView.current)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AccountEditScreen(
                viewModel = spendlyViewModel { c, h -> AccountEditViewModel(c.repositories, h) },
                onDone = { nav.popIfTop(back) },
                onDeleted = { nav.popIfTop(back) },
            )
        }
    }
}

/** Pops [entry] only if it's still on top, so a double tap can never pop the screen beneath. */
fun NavHostController.popIfTop(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

/**
 * Floating navigation dock, icons only. Tap a tab to open it. Touch and hold a tab to lift it, slide it along
 * the dock and let go to drop it there: the others move out of the way and the order is saved.
 * The selection pill follows the open tab wherever it goes. Screen readers hear each tab's name
 * and get "Move before/after" actions instead of dragging.
 */
@Composable
private fun BottomDock(
    order: List<AppTab>,
    selected: AppTab?,
    onSelect: (AppTab) -> Unit,
    onReorder: (List<AppTab>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Spendly.colors
    val haptics = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val select by rememberUpdatedState(onSelect)
    val reorder by rememberUpdatedState(onReorder)
    val currentSelected by rememberUpdatedState(selected)
    // What's on screen: the saved order, or the order just dropped while it's being saved.
    var shown by remember { mutableStateOf(order) }
    var dragging by remember { mutableStateOf<AppTab?>(null) }
    var dragStart by remember { mutableIntStateOf(0) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(order) { if (dragging == null) shown = order }

    BoxWithConstraints(
        modifier
            .height(DockHeight)
            .layeredSurface(RoundedCornerShape(24.dp), c.cardElevated, raised = true)
            .padding(horizontal = 4.dp),
    ) {
        val count = shown.size
        val slot = maxWidth / count
        val slotPx = constraints.maxWidth.toFloat() / count
        // Each tab's animated position, observable so the selection pill can follow it.
        val xs = remember { mutableStateMapOf<AppTab, Animatable<Float, AnimationVector1D>>() }
        // Where a dropped tab was let go, so it glides from the finger into its new slot.
        val landings = remember { HashMap<AppTab, Float>() }
        val drop by remember(slotPx, count) { derivedStateOf { DockOrder.dropIndex(dragStart, dragPx, slotPx, count) } }
        // Where each tab is heading: its slot in the order the dock would have if dropped now.
        val preview = if (dragging != null) DockOrder.move(shown, dragStart, drop) else shown
        val maxX = slotPx * (count - 1)
        fun liveX(tab: AppTab): Float? =
            if (tab == dragging) (dragStart * slotPx + dragPx).coerceIn(0f, maxX) else xs[tab]?.value

        fun move(from: Int, to: Int) {
            val next = DockOrder.move(shown, from, to)
            if (next != shown) {
                shown = next
                reorder(next)
            }
        }

        // The selection pill chases the open tab, also while that tab is being dragged.
        val pill = remember { Animatable(Float.NaN) }
        LaunchedEffect(selected, slotPx) {
            snapshotFlow { selected?.let { liveX(it) } }.collectLatest { target ->
                if (target == null) return@collectLatest
                if (pill.value.isNaN() || reduced) pill.snapTo(target) else pill.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 520f))
            }
        }
        if (selected != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(((if (pill.value.isNaN()) 0f else pill.value) + 3.dp.toPx()).roundToInt(), 0) }
                    .size(width = slot - 6.dp, height = DockHeight - 14.dp)
                    .graphicsLayer { alpha = if (pill.value.isNaN()) 0f else 1f }
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.accent.copy(alpha = if (c.isDark) 0.16f else 0.14f)),
            )
        }

        shown.forEachIndexed { index, tabKey ->
            key(tabKey) {
                val tab = tabFor(tabKey)
                val isSelected = tabKey == selected
                val isDragged = tabKey == dragging
                val target = slotPx * preview.indexOf(tabKey)
                val x = remember { Animatable(target) }
                DisposableEffect(x) {
                    xs[tabKey] = x
                    onDispose { xs.remove(tabKey) }
                }
                LaunchedEffect(target, isDragged) {
                    if (!isDragged) {
                        landings.remove(tabKey)?.let { x.snapTo(it) }
                        if (reduced) x.snapTo(target) else x.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 480f))
                    }
                }
                val lift by animateFloatAsState(if (isDragged && !reduced) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 520f), label = "dockLift")
                val tint by animateColorAsState(if (isSelected || isDragged) c.accent else c.muted, spring(stiffness = 600f), label = "dockTint")
                val scale by animateFloatAsState(if (isSelected) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "dockScale")
                val interaction = remember { MutableInteractionSource() }
                val before = shown.getOrNull(index - 1)?.let { tabFor(it).label }
                val after = shown.getOrNull(index + 1)?.let { tabFor(it).label }
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((if (isDragged) liveX(tabKey) ?: 0f else x.value).roundToInt(), 0) }
                        .zIndex(if (isDragged) 1f else 0f)
                        .width(slot)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val s = 1f + 0.12f * lift
                            scaleX = s
                            scaleY = s
                            shadowElevation = 10.dp.toPx() * lift
                            shape = RoundedCornerShape(18.dp)
                            clip = false
                        }
                        .drawBehind {
                            if (lift > 0f) {
                                drawRoundRect(
                                    lerp(c.cardElevated, c.accent, 0.1f).copy(alpha = lift),
                                    topLeft = Offset(3.dp.toPx(), 7.dp.toPx()),
                                    size = Size(size.width - 6.dp.toPx(), size.height - 14.dp.toPx()),
                                    cornerRadius = CornerRadius(18.dp.toPx()),
                                )
                            }
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .indication(interaction, ripple(bounded = true))
                        .pointerInput(tabKey, slotPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val press = PressInteraction.Press(down.position)
                                interaction.tryEmit(press)
                                val held = awaitLongPressOrCancellation(down.id)
                                if (held == null) {
                                    // Released before the hold time: a tap (unless something else took it).
                                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                    val tapped = up != null && up.changedToUp()
                                    interaction.tryEmit(if (tapped) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                                    if (tapped) {
                                        up.consume()
                                        if (dragging == null) {
                                            if (currentSelected != tabKey) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            select(tabKey)
                                        }
                                    }
                                    return@awaitEachGesture
                                }
                                // Held: lift the tab and follow the finger along the dock. No navigation.
                                interaction.tryEmit(PressInteraction.Cancel(press))
                                if (dragging != null) return@awaitEachGesture
                                held.consume()
                                dragStart = shown.indexOf(tabKey)
                                dragPx = 0f
                                dragging = tabKey
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                var slotNow = dragStart
                                val completed = horizontalDrag(held.id) { change ->
                                    val dx = change.positionChange().x
                                    dragPx += if (rtl) -dx else dx
                                    change.consume()
                                    if (drop != slotNow) {
                                        slotNow = drop
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    }
                                }
                                // Drop where it is (or, if the gesture was cancelled, back where it came from).
                                val from = dragStart
                                val to = if (completed) drop else from
                                landings[tabKey] = liveX(tabKey) ?: 0f
                                dragging = null
                                dragPx = 0f
                                move(from, to)
                                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = isSelected
                            // Icons only, as in Spendly's design; every tab keeps its name for screen readers.
                            contentDescription = tab.label
                            onClick(label = "Open ${tab.label}") { select(tabKey); true }
                            customActions = listOfNotNull(
                                before?.let { CustomAccessibilityAction("Move before $it") { move(index, index - 1); true } },
                                after?.let { CustomAccessibilityAction("Move after $it") { move(index, index + 1); true } },
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (isSelected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
            }
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

private fun NavBackStackEntry.isTop() = destination.isTopLevel()

private const val FADE_IN = 220
private const val FADE_OUT = 150

/** Tabs: a quick cross-fade with a hint of depth. Deeper screens: a short shared-axis slide. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition = when {
    from.isTop() && to.isTop() -> fadeIn(tween(FADE_IN, delayMillis = 40)) + scaleIn(tween(FADE_IN, delayMillis = 40), initialScale = 0.985f)
    else -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 6 } + fadeIn(tween(FADE_IN))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition = when {
    from.isTop() && to.isTop() -> fadeOut(tween(FADE_OUT))
    else -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 12 } + fadeOut(tween(FADE_OUT + 80))
}

private fun popEnterFor(): EnterTransition = fadeIn(tween(FADE_IN)) + scaleIn(tween(FADE_IN), initialScale = 0.985f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, spring(dampingRatio = 0.95f, stiffness = 420f)) { it / 6 } + fadeOut(tween(FADE_OUT + 60))
