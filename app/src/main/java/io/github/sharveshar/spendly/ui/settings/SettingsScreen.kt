package io.github.sharveshar.spendly.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.data.backup.BackupCodec
import io.github.sharveshar.spendly.data.prefs.ThemeMode
import io.github.sharveshar.spendly.ui.common.Formats
import io.github.sharveshar.spendly.ui.components.OriginAnchor
import io.github.sharveshar.spendly.ui.components.OriginPopup
import io.github.sharveshar.spendly.ui.components.SegmentedSelector
import io.github.sharveshar.spendly.ui.components.originAnchor
import io.github.sharveshar.spendly.ui.components.rememberOriginAnchor
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class SettingsPopup { NONE, EXPORT_PASSWORD, IMPORT_PASSWORD, CSV_WARNING }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onCategories: () -> Unit, onAbout: () -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val flow by viewModel.flow.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var popup by rememberSaveable { mutableStateOf(SettingsPopup.NONE) }
    val exportAnchor = rememberOriginAnchor()
    val importAnchor = rememberOriginAnchor()
    val csvAnchor = rememberOriginAnchor()
    val restoreAnchor = importAnchor

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupCodec.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportTo(uri) else viewModel.clearPassword()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readBackup(uri) else viewModel.clearPassword()
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Group("Appearance")
                SettingRow(Icons.Rounded.Palette, "Theme", "Dark is Spendly's signature look")
                SegmentedSelector(
                    options = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM),
                    selected = prefs.themeMode,
                    label = { when (it) { ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light"; ThemeMode.SYSTEM -> "System" } },
                    onSelect = { viewModel.setTheme(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
                )
                Spacer(Modifier.height(Space.md))

                ShortcutsSection(prefs.buttonShortcut, onShortcutChange = viewModel::setButtonShortcut)

                Group("Privacy")
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Switch) { viewModel.setSecureScreen(!prefs.secureScreen) }.heightIn(min = 64.dp).padding(horizontal = Space.gutter, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = Spendly.colors.muted)
                    Spacer(Modifier.width(Space.lg))
                    Column(Modifier.weight(1f)) {
                        Text("Hide app contents", style = MaterialTheme.typography.bodyLarge)
                        Text("Blocks screenshots and blurs Spendly in recent apps", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
                    }
                    Switch(checked = prefs.secureScreen, onCheckedChange = null)
                }
                SettingRow(
                    Icons.Rounded.Lock,
                    "Your data stays on this phone",
                    "Encrypted on-device database. No account, no internet permission, no ads or trackers. Android cloud backup is turned off for Spendly; use encrypted backups below.",
                )

                Group("Backup & export")
                SettingRow(Icons.Rounded.Download, "Create encrypted backup", "Password-protected file you save where you choose", Modifier.originAnchor(exportAnchor)) { popup = SettingsPopup.EXPORT_PASSWORD }
                SettingRow(Icons.Rounded.Restore, "Restore from backup", "Replaces everything here after you confirm", Modifier.originAnchor(importAnchor)) { popup = SettingsPopup.IMPORT_PASSWORD }
                SettingRow(Icons.Rounded.TableChart, "Export as CSV", "Unencrypted spreadsheet of all transactions", Modifier.originAnchor(csvAnchor)) { popup = SettingsPopup.CSV_WARNING }

                Group("Organise")
                SettingRow(Icons.Rounded.Category, "Categories", "Add, rename or archive categories", chevron = true, onClick = onCategories)

                if (viewModel.debugToolsAvailable) {
                    Group("Debug build only")
                    SettingRow(Icons.Rounded.Science, "Load sample data", "Fictional demo records for testing. Not present in release builds.") { viewModel.loadSampleData() }
                }

                Group("About")
                SettingRow(Icons.Rounded.Info, "About Spendly", "Version, privacy, open-source licences", chevron = true, onClick = onAbout)
                Spacer(Modifier.height(Space.xxxl))
                BrandFooter()
                Spacer(Modifier.height(Space.xl))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).systemBarsPadding())

        val working = flow as? BackupFlow.Working
        if (working != null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(Space.md))
                    Text(working.label)
                }
            }
        }
    }

    PasswordPopup(
        visible = popup == SettingsPopup.EXPORT_PASSWORD,
        anchor = exportAnchor,
        title = "Encrypt your backup",
        body = "Choose a password of at least ${BackupCodec.MIN_PASSWORD_LENGTH} characters. It isn't stored anywhere. If you forget it, the backup can't be recovered.",
        confirm = true,
        action = "Choose location",
        onDismiss = { popup = SettingsPopup.NONE },
    ) { pwd ->
        viewModel.holdPassword(pwd)
        popup = SettingsPopup.NONE
        exportLauncher.launch("spendly-backup-${LocalDate.now()}.${BackupCodec.EXTENSION}")
    }
    PasswordPopup(
        visible = popup == SettingsPopup.IMPORT_PASSWORD,
        anchor = importAnchor,
        title = "Restore a backup",
        body = "Enter the backup's password, then pick the file. You'll see what's inside and confirm before anything is replaced.",
        confirm = false,
        action = "Choose file",
        onDismiss = { popup = SettingsPopup.NONE },
    ) { pwd ->
        viewModel.holdPassword(pwd)
        popup = SettingsPopup.NONE
        importLauncher.launch(arrayOf("*/*"))
    }
    OriginPopup(visible = popup == SettingsPopup.CSV_WARNING, anchor = csvAnchor, onDismissRequest = { popup = SettingsPopup.NONE }) {
        Column(Modifier.padding(Space.xxl)) {
            Text("Export unencrypted CSV?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))
            Text(
                "The CSV file contains all your transactions in plain text. Anyone or any app that can open the file can read it. Prefer the encrypted backup for safekeeping.",
                color = Spendly.colors.muted,
            )
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { popup = SettingsPopup.NONE }) { Text("Cancel") }
                Spacer(Modifier.width(Space.sm))
                Button(onClick = {
                    popup = SettingsPopup.NONE
                    csvLauncher.launch("spendly-transactions-${LocalDate.now()}.csv")
                }) { Text("Export") }
            }
        }
    }
    val confirm = flow as? BackupFlow.ConfirmRestore
    var lastConfirm by remember { mutableStateOf<BackupFlow.ConfirmRestore?>(null) }
    if (confirm != null) lastConfirm = confirm
    OriginPopup(visible = confirm != null, anchor = restoreAnchor, onDismissRequest = viewModel::cancelRestore) {
        val s = lastConfirm?.validation?.summary
        Column(Modifier.padding(Space.xxl)) {
            Text("Replace all data?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))
            if (s != null) {
                val created = Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.systemDefault())
                Text(
                    "Backup from ${created.format(Formats.dateTime)}\n${s.accounts} accounts · ${s.transactions} transactions · ${s.categories} categories · ${s.budgets} budgets",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Space.md))
            }
            Text(
                "Everything currently in Spendly on this phone will be replaced by this backup. This can't be undone. Consider creating a backup of the current data first.",
                color = Spendly.colors.warning,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") }
                Spacer(Modifier.width(Space.sm))
                Button(onClick = viewModel::confirmRestore) { Text("Replace my data") }
            }
        }
    }
}

@Composable
internal fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.xl, bottom = Space.xs).semantics { heading() },
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Spendly.colors.muted)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
        }
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Spendly.colors.muted)
    }
}

@Composable
private fun PasswordPopup(
    visible: Boolean,
    anchor: OriginAnchor,
    title: String,
    body: String,
    confirm: Boolean,
    action: String,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var password by remember(visible) { mutableStateOf("") }
    var again by remember(visible) { mutableStateOf("") }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    OriginPopup(visible = visible, anchor = anchor, onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.xxl)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))
            Text(body, color = Spendly.colors.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Space.lg))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128); error = null },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            if (confirm) {
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = again,
                    onValueChange = { again = it.take(128); error = null },
                    label = { Text("Repeat password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it, color = Spendly.colors.negative, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(Space.sm))
                Button(onClick = {
                    when {
                        password.length < BackupCodec.MIN_PASSWORD_LENGTH -> error = "Use at least ${BackupCodec.MIN_PASSWORD_LENGTH} characters"
                        confirm && password != again -> error = "Passwords don't match"
                        else -> {
                            val chars = password.toCharArray()
                            password = ""; again = ""
                            onSubmit(chars)
                        }
                    }
                }) { Text(action) }
            }
        }
    }
}

private const val BRAND_URL = "https://ar-sharvesh.vercel.app"

/** Small, understated credit. The link opens in the user's browser; Spendly itself has no network access. */
@Composable
fun BrandFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Spendly.colors.muted)) { append("Built with ") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("♥") }
                withStyle(SpanStyle(color = Spendly.colors.muted)) { append(" by ") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("ARS") }
            },
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            "ar-sharvesh.vercel.app",
            style = MaterialTheme.typography.labelSmall,
            color = Spendly.colors.muted,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = "Open website in browser") {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, BRAND_URL.toUri())
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                        // No browser installed; nothing else to do.
                    }
                }
                .heightIn(min = 48.dp)
                .padding(horizontal = Space.md, vertical = Space.md),
        )
    }
}
