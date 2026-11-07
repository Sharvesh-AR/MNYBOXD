package io.github.sharveshar.spendly.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.core.time.CalendarMonthGrid
import io.github.sharveshar.spendly.core.time.SelectableRange
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val spokenDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

/**
 * Month calendar for picking a transaction date.
 *
 * Selected day: a filled accent circle. Today: an accent ring (and a filled circle when it's also
 * selected). Days outside [range] are dimmed and can't be picked. Every month shows six rows, so
 * changing month slides the grid without any layout jump; cells shrink on narrow screens but
 * the highlight is always a perfect circle with the number centred.
 */
@Composable
fun CalendarPicker(
    selected: LocalDate,
    today: LocalDate,
    range: SelectableRange,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    val haptics = LocalHapticFeedback.current
    var shownMonth by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month = YearMonth.parse(shownMonth)
    val firstDay = WeekFields.of(locale).firstDayOfWeek

    // Own text colour, so the header reads well wherever the picker is hosted.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shownMonth = month.minusMonths(1).toString() }, enabled = range.canShow(month.minusMonths(1))) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            AnimatedContent(
                month,
                transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) },
                label = "monthTitle",
                modifier = Modifier.weight(1f),
            ) { m ->
                Text(
                    m.format(monthTitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
            }
            IconButton(onClick = { shownMonth = month.plusMonths(1).toString() }, enabled = range.canShow(month.plusMonths(1))) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Space.sm)) {
            val cell = (maxWidth / CalendarMonthGrid.COLUMNS).coerceAtMost(52.dp)
            val circle = (cell - 6.dp).coerceIn(28.dp, 40.dp)
            Column(Modifier.align(Alignment.Center)) {
                Row {
                    CalendarMonthGrid.of(month, firstDay).weekdays.forEach { d ->
                        Text(
                            d.getDisplayName(JavaTextStyle.NARROW, locale),
                            style = MaterialTheme.typography.labelMedium,
                            color = Spendly.colors.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(cell).padding(vertical = Space.xs),
                        )
                    }
                }
                AnimatedContent(
                    month,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(160)))
                            .togetherWith(slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(110)))
                    },
                    label = "monthGrid",
                ) { m ->
                    val grid = CalendarMonthGrid.of(m, firstDay)
                    Column {
                        grid.cells.chunked(CalendarMonthGrid.COLUMNS).forEach { week ->
                            Row {
                                week.forEach { date ->
                                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                        if (date != null) {
                                            DayCell(
                                                date = date,
                                                size = circle,
                                                selected = date == selected,
                                                today = date == today,
                                                enabled = date in range,
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                    onSelect(date)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DayCell(date: LocalDate, size: androidx.compose.ui.unit.Dp, selected: Boolean, today: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Spendly.colors
    val fill by animateColorAsState(if (selected) c.accent else Color.Transparent, spring(stiffness = 700f), label = "dayFill")
    val text by animateColorAsState(
        when {
            selected -> c.onAccent
            !enabled -> c.muted.copy(alpha = 0.38f)
            today -> c.accent
            else -> MaterialTheme.colorScheme.onSurface
        },
        spring(stiffness = 700f),
        label = "dayText",
    )
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "dayScale")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(fill)
            .then(if (today && !selected) Modifier.border(BorderStroke(1.5.dp, c.accent), CircleShape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = date.format(spokenDate)
                stateDescription = buildString {
                    if (today) append("Today")
                    if (!enabled) { if (isNotEmpty()) append(", "); append("unavailable") }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal),
            color = text,
            textAlign = TextAlign.Center,
        )
    }
}
