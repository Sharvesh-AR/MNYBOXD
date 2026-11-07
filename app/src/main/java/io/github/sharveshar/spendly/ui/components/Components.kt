package io.github.sharveshar.spendly.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.db.TransactionRow
import io.github.sharveshar.spendly.domain.insights.BudgetLevel
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.theme.AmountStyle
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A layered surface: the theme's surface colour with a faint light edge along the top (dark
 * themes) or a soft shadow (light theme). This gives cards depth without heavy borders.
 */
fun Modifier.layeredSurface(shape: Shape, color: Color? = null, raised: Boolean = false): Modifier = composed {
    val c = Spendly.colors
    val base = if (!c.isDark) {
        shadow(if (raised) 8.dp else 2.dp, shape, clip = false, ambientColor = c.shadow.copy(alpha = 0.18f), spotColor = c.shadow.copy(alpha = 0.22f))
    } else if (raised) {
        shadow(10.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
    } else {
        Modifier
    }
    this
        .then(base)
        .clip(shape)
        .background(color ?: c.card)
        .border(1.dp, Brush.verticalGradient(0f to c.edgeHighlight, 0.5f to Color.Transparent), shape)
}

/** A card-like section. Hierarchy comes from surface tone and a soft edge, not heavy borders. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Dp = Space.lg,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.pressScale(interaction, 0.98f) else it }
            .layeredSurface(RoundedCornerShape(22.dp))
            .let { if (onClick != null) it.clickable(interactionSource = interaction, indication = ripple(), onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/**
 * The main call to action (Save, Continue). A gently lit gradient face and a soft press-in, with
 * loading and disabled states.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
) {
    val c = Spendly.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    val top = if (destructive) c.negative else c.accentHighlight
    val bottom = if (destructive) c.negative else c.accent
    val content = if (destructive) c.background else c.onAccent
    val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, label = "btnAlpha")
    Box(
        modifier
            .heightIn(min = 56.dp)
            .pressScale(interaction, 0.97f)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.Transparent), shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.xl),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(loading, transitionSpec = { fadeIn(tween(150)).togetherWith(fadeOut(tween(100))) }, label = "btnLoading") { busy ->
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), color = content, strokeWidth = 2.dp)
            } else {
                Text(text, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (Spendly.colors.isDark) 0.14f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Text that rolls each changed character vertically, like an odometer. Purely visual: the
 * string shown is always the exact formatted value.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, contentDescription: String = text) {
    Row(modifier.clearAndSetSemantics { this.contentDescription = contentDescription }) {
        text.forEachIndexed { index, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { h -> if (up) h else -h } + fadeIn(tween(120)))
                        .togetherWith(slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { h -> if (up) -h else h } + fadeOut(tween(90)))
                },
                label = "digit$index",
            ) { c -> Text(c.toString(), style = style, color = color, maxLines = 1) }
        }
    }
}

/** Large animated currency amount. */
@Composable
fun MoneyText(paise: Long, style: TextStyle, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val text = Money.format(paise)
    RollingText(text, style, color, modifier, contentDescription = spokenAmount(paise))
}

/** Screen-reader friendly amount: "minus 1,250 rupees 50 paise". */
fun spokenAmount(paise: Long): String {
    val abs = kotlin.math.abs(paise)
    val rupees = Money.format(abs - abs % 100, symbol = false)
    val p = abs % 100
    return buildString {
        if (paise < 0) append("minus ")
        append(rupees).append(" rupees")
        if (p != 0L) append(" ").append(p).append(" paise")
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.secondary, size = 56.dp)
        Spacer(Modifier.height(Space.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Spendly.colors.muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

@Composable
fun budgetColor(level: BudgetLevel): Color = when (level) {
    BudgetLevel.OK -> MaterialTheme.colorScheme.primary
    BudgetLevel.WARNING -> Spendly.colors.warning
    BudgetLevel.EXCEEDED -> Spendly.colors.negative
}

/** Thin animated progress bar with an 80% tick. */
@Composable
fun BudgetBar(fraction: Float, level: BudgetLevel, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "budget")
    val color = budgetColor(level)
    val track = Spendly.colors.chartMuted
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(RoundedCornerShape(height)).background(color))
        Box(Modifier.fillMaxWidth(0.8f).height(height)) {
            Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(height).background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)))
        }
    }
}

@Composable
fun MonthSwitcher(label: String, onPrevious: () -> Unit, onNext: () -> Unit, canGoNext: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Space.xs))
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

/** One transaction in a list. Amount sign and colour carry meaning; nothing else is coloured. */
@Composable
fun TransactionListItem(row: TransactionRow, onClick: () -> Unit, modifier: Modifier = Modifier, showTime: Boolean = true) {
    val palette = Spendly.colors.categoryPalette
    val (icon, tint) = when (row.type) {
        TransactionType.TRANSFER -> CategoryIcons.transfer to MaterialTheme.colorScheme.secondary
        else -> CategoryIcons.forKey(row.categoryIcon) to palette[(row.categoryColor ?: 9).coerceIn(0, palette.lastIndex)]
    }
    val title = row.description.ifBlank {
        when (row.type) {
            TransactionType.TRANSFER -> "Transfer"
            else -> row.categoryName ?: row.type.label
        }
    }
    val subtitle = buildList {
        when (row.type) {
            TransactionType.TRANSFER -> add("${row.accountName} → ${row.toAccountName.orEmpty()}")
            else -> {
                if (row.description.isNotBlank()) row.categoryName?.let { add(it) }
                add(row.instrumentName ?: row.accountName)
                row.provider?.let { add(it.label) }
            }
        }
        if (row.type == TransactionType.REFUND) add("Refund")
        if (showTime) add(Instant.ofEpochMilli(row.occurredAt).atZone(ZoneId.systemDefault()).format(timeFormat))
    }.joinToString(" · ")
    val (amountText, amountColor) = when (row.type) {
        TransactionType.EXPENSE -> "−" + Money.format(row.amountPaise) to MaterialTheme.colorScheme.onSurface
        TransactionType.INCOME, TransactionType.REFUND -> "+" + Money.format(row.amountPaise) to Spendly.colors.positive
        TransactionType.TRANSFER -> Money.format(row.amountPaise) to Spendly.colors.muted
    }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open transaction", onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.gutter, vertical = Space.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, ${row.type.label}, ${spokenAmount(row.amountPaise)}, $subtitle"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.sm))
        Text(amountText, style = AmountStyle.row, color = amountColor, maxLines = 1)
    }
}

/** Rupee amount text field for forms (account opening balance, budgets). */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 14 && new.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(new) },
        label = { Text(label) },
        prefix = { Text("₹") },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
    )
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
        Spacer(Modifier.height(Space.xxs))
        Text(value, style = AmountStyle.summary, color = valueColor, maxLines = 1)
    }
}

@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Spendly.colors.muted)
        Spacer(Modifier.width(Space.lg))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
    }
}
