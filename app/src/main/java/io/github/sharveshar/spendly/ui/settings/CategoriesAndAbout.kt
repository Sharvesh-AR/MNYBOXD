// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontFamily
import io.github.sharveshar.spendly.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.data.db.CategoryEntity
import io.github.sharveshar.spendly.data.repo.WriteResult
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.ui.components.CategoryIcons
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.OriginAnchor
import io.github.sharveshar.spendly.ui.components.OriginPopup
import io.github.sharveshar.spendly.ui.components.SectionHeader
import io.github.sharveshar.spendly.ui.components.SegmentedSelector
import io.github.sharveshar.spendly.ui.components.originAnchor
import io.github.sharveshar.spendly.ui.components.rememberOriginAnchor
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repos: Repositories) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = repos.categories.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun save(existing: CategoryEntity?, kind: CategoryKind, name: String, icon: String, color: Int): String? {
        val r = if (existing == null) repos.categories.create(name, kind, icon, color) else repos.categories.update(existing.id, name, icon, color)
        return (r as? WriteResult.Rejected)?.reason
    }

    suspend fun setArchived(id: Long, archived: Boolean) = repos.categories.setArchived(id, archived)

    suspend fun delete(id: Long): String? = (repos.categories.deleteIfUnused(id) as? WriteResult.Rejected)?.reason
}

private data class CategoryEdit(val existing: CategoryEntity?, val kind: CategoryKind, val anchor: OriginAnchor)

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var edit by remember { mutableStateOf<CategoryEdit?>(null) }
    var shown by remember { mutableStateOf(false) }
    val addAnchor = rememberOriginAnchor()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Categories", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = { edit = CategoryEdit(null, kind, addAnchor); shown = true }, modifier = Modifier.originAnchor(addAnchor)) {
                Icon(Icons.Rounded.Add, contentDescription = "Add category")
            }
        }
        SegmentedSelector(
            listOf(CategoryKind.EXPENSE, CategoryKind.INCOME), kind,
            { if (it == CategoryKind.EXPENSE) "Expense" else "Income" }, { kind = it },
            Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.sm),
        )
        val list = categories.filter { it.kind == kind }
        LazyColumn(Modifier.fillMaxSize()) {
            val active = list.filter { !it.archived }
            val archived = list.filter { it.archived }
            items(active, key = { it.id }) { c -> CategoryRow(c, Modifier.animateItem()) { a -> edit = CategoryEdit(c, kind, a); shown = true } }
            if (archived.isNotEmpty()) {
                item(key = "arch") { SectionHeader("Archived", Modifier.padding(horizontal = Space.gutter)) }
                items(archived, key = { it.id }) { c -> CategoryRow(c, Modifier.animateItem()) { a -> edit = CategoryEdit(c, kind, a); shown = true } }
            }
            item { Spacer(Modifier.height(Space.xxxl)) }
        }
    }
    edit?.let { e -> CategoryEditor(viewModel, e, shown) { shown = false } }
}

@Composable
private fun CategoryRow(c: CategoryEntity, modifier: Modifier, onClick: (OriginAnchor) -> Unit) {
    val anchor = rememberOriginAnchor()
    val color = Spendly.colors.categoryPalette[c.colorIndex.coerceIn(0, 9)]
    Row(
        modifier.originAnchor(anchor).fillMaxWidth().clickable { onClick(anchor) }.heightIn(min = 56.dp).padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(CategoryIcons.forKey(c.iconKey), if (c.archived) Spendly.colors.muted else color, size = 36.dp)
        Spacer(Modifier.width(Space.md))
        Text(c.name, style = MaterialTheme.typography.bodyLarge, color = if (c.archived) Spendly.colors.muted else MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditor(viewModel: CategoriesViewModel, e: CategoryEdit, visible: Boolean, onDismiss: () -> Unit) {
    var name by remember(e) { mutableStateOf(e.existing?.name.orEmpty()) }
    var icon by remember(e) { mutableStateOf(e.existing?.iconKey ?: "other") }
    var color by remember(e) { mutableIntStateOf(e.existing?.colorIndex ?: 0) }
    var error by remember(e) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    OriginPopup(visible = visible, anchor = e.anchor, onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.xxl).verticalScroll(rememberScrollState())) {
            Text(if (e.existing == null) "New category" else "Edit category", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.lg))
            OutlinedTextField(
                name, { name = it.take(30); error = null }, label = { Text("Name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.lg))
            Text("Colour", style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
            Spacer(Modifier.height(Space.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Spendly.colors.categoryPalette.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).border(2.dp, if (i == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .selectable(i == color, role = Role.RadioButton) { color = i }.semantics { contentDescription = "Colour ${i + 1}" },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(26.dp).clip(CircleShape).background(c)) }
                }
            }
            Spacer(Modifier.height(Space.lg))
            val tint = Spendly.colors.categoryPalette[color]
            // Every icon, grouped the way people think about spending (habits included).
            CategoryIcons.groups.forEach { group ->
                Text(group.title, style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
                Spacer(Modifier.height(Space.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    group.icons.forEach { item ->
                        val picked = item.key == icon
                        val ring by animateColorAsState(if (picked) MaterialTheme.colorScheme.onSurface else Color.Transparent, label = "iconRing")
                        val scale by animateFloatAsState(if (picked) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "iconPick")
                        Box(
                            Modifier
                                .size(48.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape)
                                .border(2.dp, ring, CircleShape)
                                .selectable(picked, role = Role.RadioButton) {
                                    if (!picked) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    icon = item.key
                                    if (name.isBlank()) name = item.label
                                }
                                .semantics { contentDescription = item.label },
                            contentAlignment = Alignment.Center,
                        ) { IconBadge(item.vector, tint, size = 38.dp) }
                    }
                }
                Spacer(Modifier.height(Space.md))
            }
            Spacer(Modifier.height(Space.lg))
            error?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it, color = Spendly.colors.negative, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                e.existing?.let { existing ->
                    TextButton(onClick = { scope.launch { viewModel.setArchived(existing.id, !existing.archived); onDismiss() } }) {
                        Text(if (existing.archived) "Restore" else "Archive")
                    }
                    TextButton(onClick = { scope.launch { val err = viewModel.delete(existing.id); if (err == null) onDismiss() else error = err } }) {
                        Text("Delete", color = Spendly.colors.negative)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { scope.launch { val err = viewModel.save(e.existing, e.kind, name, icon, color); if (err == null) onDismiss() else error = err } }) { Text("Save") }
            }
        }
    }
}

private data class License(val name: String, val license: String)

private val licenses = listOf(
    License("SQLCipher for Android (Community Edition), Zetetic LLC", "BSD-style licence · includes SQLite and LibTomCrypt (public domain)"),
    License("AndroidX, Jetpack Compose, Material 3 & Material Icons", "Apache License 2.0"),
    License("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
    License("Okio, Guava ListenableFuture, JetBrains & JSpecify annotations", "Apache License 2.0"),
    License("Protocol Buffers (via AndroidX DataStore)", "BSD-3-Clause"),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull() ?: ""
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("About", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter)) {
            Spacer(Modifier.height(Space.lg))
            Text("MNYBOXD", style = MaterialTheme.typography.displaySmall)
            Text("Version $version", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
            Spacer(Modifier.height(Space.xl))
            AboutBlock(
                "Privacy",
                "MNYBOXD works entirely offline and has no internet permission. There is no account, no analytics, no ads and no tracking. " +
                    "Your records are stored in an encrypted database on this phone, protected by a key held in Android Keystore.",
            )
            AboutBlock(
                "Backups",
                "Android cloud and device-to-device backup are disabled for MNYBOXD, so your data is never copied to the cloud without you. " +
                    "Create an encrypted backup file from Settings and keep it somewhere safe. If you uninstall the app or reset your phone without a backup, your records can't be recovered.",
            )
            AboutBlock(
                "Voice entry",
                "Voice uses your phone's speech recognition service. Depending on the device, that service may process audio online. MNYBOXD never stores audio, and you always confirm before anything is saved.",
            )
            AboutBlock("Not a bank", "MNYBOXD never connects to banks or payment apps and never moves money. Balances are calculated from what you record.")
            Spacer(Modifier.height(Space.md))
            Text("Open-source licences", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Space.sm))
            licenses.forEach {
                Column(Modifier.padding(vertical = Space.sm)) {
                    Text(it.name, style = MaterialTheme.typography.bodyMedium)
                    Text(it.license, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
                }
            }
            var showFull by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { showFull = !showFull }) { Text(if (showFull) "Hide full licence texts" else "Show full licence texts") }
            AnimatedVisibility(showFull) {
                val resources = LocalResources.current
                val notices = remember(resources) {
                    runCatching { resources.openRawResource(R.raw.open_source_notices).bufferedReader().use { it.readText() } }.getOrDefault("")
                }
                SelectionContainer {
                    Text(
                        notices,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Spendly.colors.muted,
                        modifier = Modifier.padding(vertical = Space.sm),
                    )
                }
            }
            Spacer(Modifier.height(Space.xxxl))
            BrandFooter()
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun AboutBlock(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Space.xs))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = Spendly.colors.muted)
    Spacer(Modifier.height(Space.xl))
}
