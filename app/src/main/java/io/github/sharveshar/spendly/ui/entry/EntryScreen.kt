package io.github.sharveshar.spendly.ui.entry

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import io.github.sharveshar.spendly.core.time.SelectableRange
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.components.AnimatedMoney
import io.github.sharveshar.spendly.ui.components.CalendarPicker
import io.github.sharveshar.spendly.ui.components.CoinFlightOverlay
import io.github.sharveshar.spendly.ui.components.CoinFlightSpec
import io.github.sharveshar.spendly.ui.components.PrimaryButton
import io.github.sharveshar.spendly.ui.components.trackBounds
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.insights.BudgetLevel
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.common.Formats
import io.github.sharveshar.spendly.ui.components.CategoryIcons
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.LocalReducedMotion
import io.github.sharveshar.spendly.ui.components.OriginAnchor
import io.github.sharveshar.spendly.ui.components.OriginPopup
import io.github.sharveshar.spendly.ui.components.RollingText
import io.github.sharveshar.spendly.ui.components.SegmentedSelector
import io.github.sharveshar.spendly.ui.components.SelectChip
import io.github.sharveshar.spendly.ui.components.icon
import io.github.sharveshar.spendly.ui.components.originAnchor
import io.github.sharveshar.spendly.ui.components.pressScale
import io.github.sharveshar.spendly.ui.components.rememberOriginAnchor
import io.github.sharveshar.spendly.ui.components.spokenAmount
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import io.github.sharveshar.spendly.ui.voice.VoiceRecognizer
import io.github.sharveshar.spendly.ui.voice.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private enum class Popup { NONE, CATEGORIES, DATE, VOICE_DISCLOSURE, VOICE, PERMISSION }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    startVoice: Boolean,
    onClose: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val disclosureAccepted by viewModel.voiceDisclosureAccepted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val f = state.form

    var popup by rememberSaveable { mutableStateOf(Popup.NONE) }
    var saved by remember { mutableStateOf<EntryEvent.Saved?>(null) }
    var flight by remember { mutableStateOf<CoinFlightSpec?>(null) }
    var landed by remember { mutableStateOf(false) }
    val micAnchor = rememberOriginAnchor()
    val categoryAnchor = rememberOriginAnchor()
    val dateAnchor = rememberOriginAnchor()
    // Where things are on screen, for the coin that flies after a save.
    var amountBounds by remember { mutableStateOf<Rect?>(null) }
    var saveBounds by remember { mutableStateOf<Rect?>(null) }
    val fromBounds = remember { mutableStateMapOf<Long, Rect>() }
    val toBounds = remember { mutableStateMapOf<Long, Rect>() }

    val recognizer = remember { VoiceRecognizer(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { recognizer.destroy() } }
    val voiceState by recognizer.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            popup = Popup.VOICE
            recognizer.start()
        } else {
            popup = Popup.PERMISSION
        }
    }

    fun startVoiceFlow() {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        when {
            !disclosureAccepted -> popup = Popup.VOICE_DISCLOSURE
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                popup = Popup.VOICE
                recognizer.start()
            }
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Voice results only pre-fill the form; the user still reviews and taps Save.
    LaunchedEffect(voiceState) {
        val s = voiceState
        if (s is VoiceState.Heard) {
            viewModel.applyVoice(s.text)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            recognizer.reset()
            popup = Popup.NONE
        }
    }

    var autoVoiceDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(f.initialized) {
        if (startVoice && f.initialized && !autoVoiceDone) {
            autoVoiceDone = true
            delay(250)
            startVoiceFlow()
        }
    }

    LaunchedEffect(state.errors) {
        if (state.errors.isNotEmpty()) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    QuickAddSheet(onDismissed = onClose) { sheet ->
        LaunchedEffect(Unit) {
            viewModel.events.collect { e ->
                if (e is EntryEvent.Saved) {
                    // The transaction is committed. Everything below is presentation only.
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    saved = e
                    val start = (if (e.type == TransactionType.TRANSFER) fromBounds[e.accountId] else null) ?: amountBounds
                    val target = (if (e.type == TransactionType.TRANSFER) toBounds[e.toAccountId] else fromBounds[e.accountId]) ?: saveBounds
                    if (start != null && target != null) {
                        val incoming = e.type == TransactionType.INCOME || e.type == TransactionType.REFUND
                        val sign = when (e.type) { TransactionType.EXPENSE -> "−"; TransactionType.TRANSFER -> ""; else -> "+" }
                        flight = CoinFlightSpec(start, target, sign + Money.format(e.amountPaise), incoming)
                    } else {
                        landed = true
                    }
                }
            }
        }
        LaunchedEffect(landed) {
            if (landed) {
                delay(if (saved?.alert != null) 1_600 else 650)
                sheet.close()
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxHeight < 640.dp
            Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                // --- Header ----------------------------------------------------------------------
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = sheet::close) { Icon(Icons.Rounded.Close, contentDescription = "Close without saving") }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when {
                            state.isRefund -> Text("Record refund", style = MaterialTheme.typography.titleMedium)
                            state.isEdit -> Text("Edit transaction", style = MaterialTheme.typography.titleMedium)
                            else -> SegmentedSelector(
                                options = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER),
                                selected = f.type,
                                label = { it.label },
                                onSelect = viewModel::setType,
                                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                            )
                        }
                    }
                    IconButton(onClick = ::startVoiceFlow, modifier = Modifier.originAnchor(micAnchor)) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Enter by voice", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (state.loadFailed) {
                    Text(
                        "This transaction is no longer available.",
                        modifier = Modifier.fillMaxWidth().padding(Space.gutter),
                        color = Spendly.colors.negative,
                    )
                }

                // --- Amount + when ---------------------------------------------------------------
                AmountDisplay(
                    f.amount.display(),
                    f.amount.text.isEmpty(),
                    f.type,
                    compact = compact,
                    modifier = Modifier.trackBounds { amountBounds = it },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    val zone = ZoneId.systemDefault()
                    val dt = Instant.ofEpochMilli(f.occurredAt).atZone(zone)
                    val label = Formats.relativeDay(dt.toLocalDate(), LocalDate.now(zone)) + " · " + dt.format(Formats.time)
                    SelectChip(
                        selected = false,
                        label = label,
                        icon = Icons.Rounded.Event,
                        onClick = { popup = Popup.DATE },
                        modifier = Modifier.originAnchor(dateAnchor),
                    )
                }
                val errorText = state.errors.firstOrNull()?.message
                AnimatedVisibility(errorText != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Text(
                        errorText.orEmpty(),
                        color = Spendly.colors.negative,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                state.refundLimitPaise?.let { limit ->
                    Text(
                        "Up to ${Money.format(limit)} can be refunded" + (state.refundOfLabel?.let { " · $it" } ?: ""),
                        color = Spendly.colors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs),
                    )
                }

                // --- Details (scrolls; the keypad and Save stay put) ---------------------------------
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .fadingEdges()
                        .verticalScroll(rememberScrollState())
                        .padding(top = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm + Space.xxs),
                ) {
                    AnimatedVisibility(f.voiceTranscript != null) {
                        VoiceBanner(f.voiceTranscript.orEmpty(), f.voiceIssues.map { it.message }, onDismiss = viewModel::dismissVoiceBanner)
                    }

                    if (f.type != TransactionType.TRANSFER) {
                        FieldLabel("Category")
                        LazyRow(contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            item(key = "all") {
                                SelectChip(
                                    selected = false,
                                    label = "All",
                                    icon = Icons.Rounded.Apps,
                                    onClick = { popup = Popup.CATEGORIES },
                                    modifier = Modifier.originAnchor(categoryAnchor),
                                )
                            }
                            items(state.categories, key = { it.id }) { c ->
                                val color = Spendly.colors.categoryPalette[c.colorIndex.coerceIn(0, 9)]
                                SelectChip(
                                    selected = c.id == f.categoryId,
                                    label = c.name,
                                    icon = CategoryIcons.forKey(c.iconKey),
                                    accent = color,
                                    onClick = { viewModel.setCategory(c.id) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }

                    FieldLabel(
                        when (f.type) {
                            TransactionType.EXPENSE -> "Paid with"
                            TransactionType.INCOME -> "Received in"
                            TransactionType.TRANSFER -> "From"
                            TransactionType.REFUND -> "Refunded to"
                        },
                    )
                    AccountChips(
                        state, selectedId = f.accountId, exclude = null, onSelect = viewModel::setAccount, onAddAccount = onAddAccount,
                        holdBalances = flight != null && !landed, onBounds = { id, r -> fromBounds[id] = r },
                    )

                    if (f.type == TransactionType.TRANSFER) {
                        FieldLabel("To")
                        AccountChips(
                            state, selectedId = f.toAccountId, exclude = f.accountId, onSelect = viewModel::setToAccount, onAddAccount = onAddAccount,
                            allowDebitCards = false, holdBalances = flight != null && !landed, onBounds = { id, r -> toBounds[id] = r },
                        )
                        Text(
                            "Transfers move money between your accounts. Paying a credit card bill is a transfer to the card.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Spendly.colors.muted,
                            modifier = Modifier.padding(horizontal = Space.gutter),
                        )
                    }

                    if (state.channels.size > 1) {
                        FieldLabel("Method")
                        LazyRow(contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            items(state.channels, key = { it.name }) { ch ->
                                SelectChip(selected = f.channel == ch, label = ch.label, onClick = { viewModel.setChannel(ch) })
                            }
                        }
                    }
                    AnimatedVisibility(state.showProviders, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm + Space.xxs)) {
                            FieldLabel("App (optional)")
                            LazyRow(contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                items(PaymentProvider.entries, key = { it.name }) { p ->
                                    SelectChip(selected = f.provider == p, label = p.label, onClick = { viewModel.setProvider(p) })
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = f.note,
                        onValueChange = viewModel::setNote,
                        placeholder = { Text("Add a note") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
                        shape = RoundedCornerShape(16.dp),
                    )
                    Spacer(Modifier.height(Space.xs))
                }

                // --- Keypad + Save -------------------------------------------------------------------
                AnimatedVisibility(!WindowInsets.isImeVisible, enter = fadeIn(tween(160)), exit = fadeOut(tween(90))) {
                    Keypad(
                        keyHeight = if (compact) 46.dp else 54.dp,
                        onDigit = viewModel::digit,
                        onDecimal = viewModel::decimal,
                        onBackspace = viewModel::backspace,
                        onClear = viewModel::clearAmount,
                    )
                }
                PrimaryButton(
                    text = when {
                        state.isEdit -> "Save changes"
                        f.type == TransactionType.TRANSFER -> "Save transfer"
                        state.isRefund -> "Save refund"
                        f.type == TransactionType.INCOME -> "Save income"
                        else -> "Save expense"
                    },
                    onClick = viewModel::save,
                    enabled = saved == null && !state.loadFailed,
                    loading = state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.md)
                        .trackBounds { saveBounds = it },
                )
            }

            CoinFlightOverlay(flight, onLanded = { landed = true })
            androidx.compose.animation.AnimatedVisibility(visible = landed, enter = fadeIn(tween(180)), exit = fadeOut()) {
                SuccessOverlay(saved)
            }
        }
    }

    // --- Popups (each grows out of the control that opened it) --------------------------------
    CategoryPickerPopup(
        visible = popup == Popup.CATEGORIES,
        anchor = categoryAnchor,
        state = state,
        onPick = { viewModel.setCategory(it); popup = Popup.NONE },
        onDismiss = { popup = Popup.NONE },
    )
    DateTimePopup(
        visible = popup == Popup.DATE,
        anchor = dateAnchor,
        initial = f.occurredAt,
        onConfirm = { viewModel.setOccurredAt(it); popup = Popup.NONE },
        onDismiss = { popup = Popup.NONE },
    )
    OriginPopup(visible = popup == Popup.VOICE_DISCLOSURE, anchor = micAnchor, onDismissRequest = { popup = Popup.NONE }) {
        Column(Modifier.padding(Space.xxl)) {
            Text("About voice entry", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))
            Text(
                "Speech is turned into text by your phone's speech recognition service (for example, Google's). " +
                    "Spendly prefers on-device recognition when your phone supports it, but depending on your device and settings, " +
                    "that service may process audio online.\n\n" +
                    "Spendly never records or stores audio and never sends your finances anywhere. The text is read on your device, " +
                    "and nothing is saved until you check the details and tap Save.",
                style = MaterialTheme.typography.bodyMedium,
                color = Spendly.colors.muted,
            )
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { popup = Popup.NONE }) { Text("Not now") }
                Spacer(Modifier.width(Space.sm))
                Button(onClick = {
                    viewModel.acceptVoiceDisclosure()
                    popup = Popup.NONE
                    scope.launch {
                        delay(220)
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            popup = Popup.VOICE
                            recognizer.start()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }) { Text("Continue") }
            }
        }
    }
    OriginPopup(visible = popup == Popup.PERMISSION, anchor = micAnchor, onDismissRequest = { popup = Popup.NONE }) {
        Column(Modifier.padding(Space.xxl)) {
            Text("Microphone is off", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))
            Text(
                "Voice entry needs microphone permission. You can allow it in system settings, or keep using the keypad. Everything else works without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = Spendly.colors.muted,
            )
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { popup = Popup.NONE }) { Text("Use keypad") }
                Spacer(Modifier.width(Space.sm))
                OutlinedButton(onClick = {
                    popup = Popup.NONE
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Open settings") }
            }
        }
    }
    VoicePopup(
        visible = popup == Popup.VOICE,
        anchor = micAnchor,
        state = voiceState,
        onStop = recognizer::stop,
        onRetry = recognizer::start,
        onCancel = { recognizer.cancel(); popup = Popup.NONE },
    )
}

/** Softly fades scrolled content out at the top and bottom edges instead of cutting it off. */
private fun Modifier.fadingEdges(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = 18.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = edge), blendMode = BlendMode.DstIn, size = Size(size.width, edge))
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - edge, endY = size.height),
            topLeft = Offset(0f, size.height - edge),
            size = Size(size.width, edge),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted, modifier = Modifier.padding(horizontal = Space.gutter))
}

@Composable
private fun AmountDisplay(text: String, empty: Boolean, type: TransactionType, compact: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        empty -> Spendly.colors.muted.copy(alpha = 0.6f)
        type == TransactionType.INCOME || type == TransactionType.REFUND -> Spendly.colors.positive
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = if (compact) Space.xs else Space.sm), contentAlignment = Alignment.Center) {
        // Shrink the type for long amounts so nothing is ever clipped.
        val base = if (compact) 44 else 52
        val size = when {
            text.length <= 7 -> base.sp
            text.length <= 10 -> (base - 8).sp
            else -> (base - 16).sp
        }
        val style = MaterialTheme.typography.displayLarge.copy(fontSize = size, lineHeight = size * 1.15f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.semantics(mergeDescendants = true) {
                contentDescription = if (empty) "Amount, empty" else "Amount ${spokenAmount(Money.parseRupees(text) ?: 0)}"
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Text("₹", style = style.copy(fontSize = size * 0.6f), color = Spendly.colors.muted)
            Spacer(Modifier.width(Space.xs))
            RollingText(text, style, color)
        }
    }
}

@Composable
private fun AccountChips(
    state: EntryUiState,
    selectedId: Long?,
    exclude: Long?,
    onSelect: (Long) -> Unit,
    onAddAccount: () -> Unit,
    allowDebitCards: Boolean = true,
    holdBalances: Boolean = false,
    onBounds: (Long, Rect) -> Unit = { _, _ -> },
) {
    val accounts = state.accounts.filter { it.id != exclude && (allowDebitCards || it.type != AccountType.DEBIT_CARD) && (!it.archived || it.id == selectedId) }
    // Wait for accounts to load so the list isn't anchored to the trailing "New account" chip.
    if (accounts.isEmpty() && state.accounts.isEmpty()) {
        Spacer(Modifier.height(40.dp))
        return
    }
    val listState = rememberLazyListState()
    val selectedIndex = accounts.indexOfFirst { it.id == selectedId }
    LaunchedEffect(selectedIndex) {
        // Bring the selected account into view (e.g. after voice entry picked it).
        if (selectedIndex >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex && it.offset >= 0 }) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        items(accounts, key = { it.id }) { a ->
            AccountChip(a, selected = a.id == selectedId, hold = holdBalances, onClick = { onSelect(a.id) }, modifier = Modifier.trackBounds { onBounds(a.id, it) })
        }
        item(key = "add") { SelectChip(selected = false, label = "New account", onClick = onAddAccount) }
    }
}

/**
 * An account to pay from or into, with its live balance. The balance counts to its new value
 * after a save (held while the coin is still in the air).
 */
@Composable
private fun AccountChip(a: AccountUi, selected: Boolean, hold: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Spendly.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) c.accent.copy(alpha = 0.14f) else c.card, spring(stiffness = 700f), label = "accBg")
    val border by animateColorAsState(if (selected) c.accent.copy(alpha = 0.75f) else c.hairline, spring(stiffness = 700f), label = "accBorder")
    val isCard = a.type == AccountType.CREDIT_CARD
    Row(
        modifier
            .pressScale(interaction)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), RoundedCornerShape(16.dp))
            .selectable(selected = selected, interactionSource = interaction, indication = ripple(), role = Role.RadioButton) {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(start = Space.md, end = Space.lg, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(a.type.icon(), contentDescription = null, tint = if (selected) c.accent else c.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Column {
            Text(a.name, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onSurface else c.muted, maxLines = 1)
            AnimatedMoney(
                paise = if (isCard) a.outstandingPaise else a.balancePaise,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isCard && a.outstandingPaise > 0 -> c.negative
                    !isCard && a.balancePaise < 0 -> c.negative
                    else -> c.muted
                },
                hold = hold,
                format = { if (isCard) Money.format(it) + " owed" else Money.format(it) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(keyHeight: androidx.compose.ui.unit.Dp, onDigit: (Char) -> Unit, onDecimal: () -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('.', '0', '<')).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    row.forEach { k ->
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(keyHeight)
                                .pressScale(interaction, 0.92f)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    interactionSource = interaction,
                                    indication = ripple(),
                                    role = Role.Button,
                                    onClickLabel = null,
                                    onLongClick = if (k == '<') ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); onClear() }) else null,
                                    onLongClickLabel = if (k == '<') "Clear amount" else null,
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    when (k) {
                                        '.' -> onDecimal()
                                        '<' -> onBackspace()
                                        else -> onDigit(k)
                                    }
                                }
                                .semantics { contentDescription = when (k) { '.' -> "Decimal point"; '<' -> "Delete"; else -> k.toString() } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (k == '<') {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null, tint = Spendly.colors.muted)
                            } else {
                                Text(k.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceBanner(transcript: String, issues: List<String>, onDismiss: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Spendly.colors.card)
            .padding(Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
            Text("Heard “$transcript”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp), tint = Spendly.colors.muted)
            }
        }
        Text("Check the details, then save.", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
        issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.warning) }
    }
}

@Composable
private fun SuccessOverlay(saved: EntryEvent.Saved?) {
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(if (saved != null) 1f else 0.6f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "ok")
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Space.xxl).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp).scale(if (reduced) 1f else scale),
            )
            Spacer(Modifier.height(Space.md))
            Text(if (saved?.wasEdit == true) "Updated" else "Saved", style = MaterialTheme.typography.titleLarge)
            saved?.alert?.let { a ->
                Spacer(Modifier.height(Space.sm))
                val who = a.categoryName?.let { "$it budget" } ?: "Monthly budget"
                Text(
                    if (a.level == BudgetLevel.EXCEEDED) "$who is used up (${a.percentUsed}%)" else "$who is ${a.percentUsed}% used",
                    color = if (a.level == BudgetLevel.EXCEEDED) Spendly.colors.negative else Spendly.colors.warning,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CategoryPickerPopup(visible: Boolean, anchor: OriginAnchor, state: EntryUiState, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    OriginPopup(visible = visible, anchor = anchor, onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.xl)) {
            Text("Choose a category", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.lg))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                state.categories.forEach { c ->
                    val color = Spendly.colors.categoryPalette[c.colorIndex.coerceIn(0, 9)]
                    Column(
                        Modifier
                            .width(88.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (c.id == state.form.categoryId) color.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(role = Role.Button, onClickLabel = "Choose ${c.name}") { onPick(c.id) }
                            .padding(vertical = Space.md, horizontal = Space.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBadge(CategoryIcons.forKey(c.iconKey), color, size = 40.dp)
                        Spacer(Modifier.height(Space.xs))
                        Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePopup(visible: Boolean, anchor: OriginAnchor, initial: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val initialLocal = Instant.ofEpochMilli(initial).atZone(zone)
    OriginPopup(visible = visible, anchor = anchor, onDismissRequest = onDismiss) {
        val today = LocalDate.now(zone)
        var date by remember(visible) { mutableStateOf(initialLocal.toLocalDate()) }
        val timeState = rememberTimePickerState(initialHour = initialLocal.hour, initialMinute = initialLocal.minute)
        var showTime by remember(visible) { mutableStateOf(false) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
            Text(
                if (showTime) "Time" else date.format(Formats.dayHeaderYear),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Space.xl),
            )
            Spacer(Modifier.height(Space.sm))
            AnimatedContent(showTime, transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.97f)).togetherWith(fadeOut(tween(120))) }, label = "dt") { time ->
                if (time) {
                    Box(Modifier.fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) { TimeInput(timeState) }
                } else {
                    CalendarPicker(
                        selected = date,
                        today = today,
                        range = SelectableRange.forTransactions(today),
                        onSelect = { date = it },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showTime = !showTime }) {
                    Text(if (showTime) "Pick date" else "Time · " + LocalDateTime.of(2000, 1, 1, timeState.hour, timeState.minute).format(Formats.time))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = {
                    val millis = date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli()
                    onConfirm(millis.coerceAtMost(System.currentTimeMillis()))
                }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun VoicePopup(visible: Boolean, anchor: OriginAnchor, state: VoiceState, onStop: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit) {
    OriginPopup(visible = visible, anchor = anchor, onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().padding(Space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
            val listening = state as? VoiceState.Listening
            // Three rings follow the voice level, each a little more lazily than the one inside it,
            // so speech ripples outwards. They move only with sound: silence means stillness.
            val raw = listening?.level ?: 0f
            val inner by animateFloatAsState(raw, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "rms1")
            val middle by animateFloatAsState(raw, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "rms2")
            val outer by animateFloatAsState(raw, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow), label = "rms3")
            val ring = MaterialTheme.colorScheme.primary
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                listOf(Triple(150.dp, outer, 0.07f), Triple(120.dp, middle, 0.11f), Triple(96.dp, inner, 0.16f)).forEach { (size, level, alpha) ->
                    Box(
                        Modifier
                            .size(size)
                            .graphicsLayer { val s = 0.6f + 0.4f * level; scaleX = s; scaleY = s }
                            .clip(CircleShape)
                            .background(ring.copy(alpha = alpha)),
                    )
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = if (listening != null) 1f else 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state is VoiceState.Processing) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                    } else {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.height(Space.lg))
            val title = when (state) {
                is VoiceState.Listening -> "Listening…"
                VoiceState.Processing -> "Understanding…"
                is VoiceState.Failed -> "Voice entry"
                else -> "Starting…"
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(Space.sm))
            Text(
                when (state) {
                    is VoiceState.Listening -> listening?.partial?.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "Try “Spent 500 on petrol using PhonePe from SBI”"
                    is VoiceState.Failed -> state.message
                    else -> " "
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is VoiceState.Failed) Spendly.colors.negative else Spendly.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 40.dp),
            )
            if (listening != null) {
                Text(
                    if (listening.onDevice) "On-device recognition" else "Using your phone's speech service",
                    style = MaterialTheme.typography.labelSmall,
                    color = Spendly.colors.muted,
                )
            }
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextButton(onClick = onCancel) { Text(if (state is VoiceState.Failed) "Use keypad" else "Cancel") }
                when (state) {
                    is VoiceState.Listening -> Button(onClick = onStop, colors = ButtonDefaults.buttonColors()) { Text("Done") }
                    is VoiceState.Failed -> if (state.retryable) Button(onClick = onRetry) { Text("Try again") }
                    else -> Unit
                }
            }
        }
    }
}
