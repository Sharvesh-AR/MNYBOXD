// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.ui.components.SubScreen
import io.github.sharveshar.spendly.ui.components.pressScale
import io.github.sharveshar.spendly.ui.theme.AppTheme
import io.github.sharveshar.spendly.ui.theme.AmountStyle
import io.github.sharveshar.spendly.ui.theme.Palettes
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import io.github.sharveshar.spendly.ui.theme.SpendlyPalette
import io.github.sharveshar.spendly.ui.theme.ThemeChoice

/** Settings → Appearance → Themes: live previews of every theme, applied instantly on tap. */
@Composable
fun ThemesScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val spend by viewModel.monthSpend.collectAsStateWithLifecycle()
    val choice = prefs.theme
    SubScreen("Themes", onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xxxl),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "system") {
                FollowSystemRow(
                    on = choice == ThemeChoice.SYSTEM,
                    onChange = { on -> viewModel.setTheme(if (on) ThemeChoice.SYSTEM else ThemeChoice.DEFAULT) },
                )
            }
            items(Palettes.all, key = { it.theme.name }) { palette ->
                val selected = choice.theme == palette.theme
                ThemeCard(
                    palette = palette,
                    selected = selected,
                    spendPaise = spend,
                    onSelect = { viewModel.setTheme(ThemeChoice.of(palette.theme)) },
                )
            }
        }
    }
}

@Composable
private fun FollowSystemRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Spendly.colors.card)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 72.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Contrast, contentDescription = null, tint = Spendly.colors.muted)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text("Match system", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Mono Dark when your phone is in dark mode, Mono Light when it's light",
                style = MaterialTheme.typography.bodySmall,
                color = Spendly.colors.muted,
            )
        }
        Spacer(Modifier.width(Space.md))
        Switch(checked = on, onCheckedChange = null)
    }
}

/**
 * A miniature of the real UI drawn in [palette]'s colours: amount, a small chart, chips and the
 * primary button, so the difference between themes is visible at a glance.
 */
@Composable
private fun ThemeCard(palette: SpendlyPalette, selected: Boolean, spendPaise: Long?, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(if (selected) Spendly.colors.accent else Spendly.colors.hairline, spring(stiffness = 600f), label = "ring")
    val ringWidth by animateDpAsState(if (selected) 2.dp else 1.dp, spring(stiffness = 600f), label = "ringW")
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .pressScale(interaction, 0.97f)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) { contentDescription = "${palette.theme.label} theme" + if (selected) ", selected" else "" },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(BorderStroke(ringWidth, ring), shape)
                .padding(4.dp),
        ) {
            ThemeMiniature(palette, spendPaise, Modifier.clip(RoundedCornerShape(18.dp)))
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(palette.theme.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Space.xs))
        Text(
            palette.theme.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = Spendly.colors.muted,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Space.xs),
        )
    }
}

@Composable
private fun ThemeMiniature(p: SpendlyPalette, spendPaise: Long?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(p.background, p.background)))
            .padding(Space.md),
    ) {
        Text("Spent this month", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
        Text(
            spendPaise?.let { Money.format(it) } ?: "₹0",
            style = AmountStyle.summary.copy(fontWeight = FontWeight.SemiBold),
            color = p.text,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.sm))
        // Mini chart card.
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.surface)
                .padding(horizontal = Space.sm, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(0.45f, 0.8f, 0.3f, 0.6f, 0.95f).forEachIndexed { i, h ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(32.dp * h)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i == 4) p.accent else p.track),
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniChip("Food", p.accentContainer, p.onAccentContainer)
            MiniChip("UPI", p.surfaceElevated, p.textMuted)
        }
        Spacer(Modifier.height(Space.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(p.accentHighlight, p.accent))),
            contentAlignment = Alignment.Center,
        ) {
            Text("Save", style = MaterialTheme.typography.labelMedium, color = p.onAccent)
        }
    }
}

@Composable
private fun MiniChip(label: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/** "Mono" or "Match system · Light" for the Settings row. */
fun ThemeChoice.describe(resolved: AppTheme): String =
    if (this == ThemeChoice.SYSTEM) "Match system · ${resolved.label}" else resolved.label
