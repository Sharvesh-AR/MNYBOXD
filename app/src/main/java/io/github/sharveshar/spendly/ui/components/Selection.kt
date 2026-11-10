// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly

/**
 * Selectable pill. Selection animates background, outline and content colour; the press
 * scales down slightly. A light tick haptic confirms the choice.
 */
@Composable
fun SelectChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.16f) else Spendly.colors.card, spring(stiffness = 700f), label = "chipBg")
    val border by animateColorAsState(if (selected) accent.copy(alpha = 0.7f) else Spendly.colors.hairline, spring(stiffness = 700f), label = "chipBorder")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else Spendly.colors.muted, label = "chipFg")
    Row(
        modifier
            .pressScale(interaction)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(50))
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
            ) {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(horizontal = Space.md + Space.xxs, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) accent else fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs + Space.xxs))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Segmented control whose indicator slides between options with a spring. */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Spendly.colors.card)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, Motion.spatial(), label = "segment")
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Spendly.colors.selectedFill),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                val color by animateColorAsState(if (i == index) Spendly.colors.onSelectedFill else Spendly.colors.muted, label = "segText")
                Box(
                    Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .selectable(selected = i == index, enabled = enabled, role = Role.Tab) {
                            if (i != index) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(o)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(o), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Simple clickable row for settings-style lists. */
@Composable
fun ClickRow(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = Space.gutter, vertical = Space.md), contentAlignment = Alignment.CenterStart) {
        content()
    }
}
