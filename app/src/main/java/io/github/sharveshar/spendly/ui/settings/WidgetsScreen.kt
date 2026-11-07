package io.github.sharveshar.spendly.ui.settings

import kotlin.math.roundToInt
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sharveshar.spendly.appContainer
import io.github.sharveshar.spendly.ui.components.SelectChip
import io.github.sharveshar.spendly.ui.components.SubScreen
import io.github.sharveshar.spendly.ui.components.layeredSurface
import io.github.sharveshar.spendly.ui.navigation.LocalAppContainer
import io.github.sharveshar.spendly.ui.theme.AppTheme
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import io.github.sharveshar.spendly.widget.WidgetKind
import io.github.sharveshar.spendly.widget.WidgetPrefs
import io.github.sharveshar.spendly.widget.WidgetRenderer
import io.github.sharveshar.spendly.widget.WidgetSnapshot
import io.github.sharveshar.spendly.widget.WidgetUpdater

private data class WidgetInfo(val kind: WidgetKind, val title: String, val body: String, val compact: Pair<Dp, Dp>, val expanded: Pair<Dp, Dp>)

private val widgetInfos = listOf(
    WidgetInfo(WidgetKind.QUICK_ADD, "Quick Add", "Add an expense, or tap the microphone to speak it. Nothing is saved until you check it and tap Save.", 160.dp to 76.dp, 330.dp to 160.dp),
    WidgetInfo(WidgetKind.MONTHLY, "Monthly overview", "This month's spending, your budget, what's left, and the last 7 days. Tap to open Budgets.", 160.dp to 160.dp, 330.dp to 160.dp),
    WidgetInfo(WidgetKind.ACCOUNTS, "Account balance", "Money available in banks, cash and wallets, with card dues kept separate. Shows all accounts or one you choose.", 160.dp to 160.dp, 330.dp to 230.dp),
    WidgetInfo(WidgetKind.TREND, "Spending trend", "Day-by-day spending for the last week, the total and your highest day. Tap to open Insights.", 160.dp to 160.dp, 330.dp to 160.dp),
    WidgetInfo(WidgetKind.STATS, "Quick stats", "Today, this month, number of entries and budget left, at a glance.", 160.dp to 160.dp, 330.dp to 76.dp),
)

/**
 * Settings → Widgets. The previews are the real widget layouts, rendered with your data and the
 * widget theme, but they are previews only: widgets live on the home screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current ?: context.appContainer
    var style by remember { mutableStateOf(WidgetPrefs.style(context)) }
    var version by remember { mutableIntStateOf(0) }
    val content by produceState<WidgetRenderer.Content?>(null) { value = WidgetUpdater.loadContent(container) }
    val manager = remember { AppWidgetManager.getInstance(context) }
    val canPin = remember { manager?.isRequestPinAppWidgetSupported == true }

    SubScreen("Widgets", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "Spendly's home-screen widgets show your real figures and update whenever your data changes. They respect your phone's widget sizes: touch and hold one to resize it.",
                style = MaterialTheme.typography.bodyMedium,
                color = Spendly.colors.muted,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
            )

            Group("Widget theme")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SelectChip(selected = style == null, label = "Match app", onClick = { style = null; apply(context, null); version++ })
                AppTheme.entries.forEach { t ->
                    SelectChip(selected = style == t, label = t.label, onClick = { style = t; apply(context, t); version++ })
                }
            }

            widgetInfos.forEach { info ->
                Spacer(Modifier.height(Space.xl))
                Column(Modifier.padding(horizontal = Space.gutter).fillMaxWidth().layeredSurface(RoundedCornerShape(24.dp)).padding(Space.lg)) {
                    Text(info.title, style = MaterialTheme.typography.titleMedium)
                    Text(info.body, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted, modifier = Modifier.padding(top = Space.xs))
                    Spacer(Modifier.height(Space.md))
                    Text("Preview", style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted)
                    Spacer(Modifier.height(Space.xs))
                    // Both sizes, each scaled down if needed so the whole widget is always visible.
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Spendly.colors.accent.copy(alpha = 0.30f), Spendly.colors.secondaryAccent.copy(alpha = 0.12f), Spendly.colors.background)))
                            .padding(Space.md),
                    ) {
                        val room = maxWidth
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                            content?.let { c ->
                                WidgetPreview(info.kind, c, info.compact, version, "${info.title}, compact", room)
                                WidgetPreview(info.kind, c, info.expanded, version, "${info.title}, expanded", room)
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    if (canPin) {
                        OutlinedButton(onClick = { manager?.requestPinAppWidget(WidgetUpdater.component(context, info.kind), null, null) }) {
                            Text("Add to home screen")
                        }
                    }
                }
            }

            Group("Adding a widget yourself")
            Text(
                "1. Touch and hold an empty part of your home screen.\n2. Tap Widgets.\n3. Find Spendly, then drag a widget into place.\n4. Touch and hold a widget to resize it. The Account balance widget can be set to one account from its settings.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
            )
            Spacer(Modifier.height(Space.xxxl))
        }
    }
}

private fun apply(context: Context, theme: AppTheme?) {
    WidgetPrefs.setStyle(context, theme)
    WidgetUpdater.requestUpdate(context)
}

/**
 * The actual widget RemoteViews, inflated in place at a typical launcher size and, when that's
 * wider than [room], scaled down as a whole (never cropped).
 */
@Composable
private fun WidgetPreview(kind: WidgetKind, content: WidgetRenderer.Content, size: Pair<Dp, Dp>, version: Int, description: String, room: Dp) {
    val (w, h) = size
    val scale = (room / w).coerceAtMost(1f)
    AndroidView(
        factory = { ctx -> FrameLayout(ctx) },
        update = { frame ->
            @Suppress("UNUSED_EXPRESSION") version
            frame.removeAllViews()
            val views = WidgetRenderer.forSize(frame.context, kind, PREVIEW_WIDGET_ID, content, w.value.toInt(), h.value.toInt())
            val view: View = runCatching { views.apply(frame.context, frame) }.getOrNull() ?: return@AndroidView
            // Previews aren't buttons: the real widget on the home screen is.
            disableClicks(view)
            frame.addView(view)
        },
        modifier = Modifier
            .layout { measurable, _ ->
                val full = measurable.measure(Constraints.fixed(w.roundToPx(), h.roundToPx()))
                layout((full.width * scale).roundToInt(), (full.height * scale).roundToInt()) {
                    full.placeWithLayer(0, 0) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
            .clip(RoundedCornerShape(24.dp))
            .semantics { contentDescription = "Preview of the $description widget" },
    )
}

private fun disableClicks(v: View) {
    v.isClickable = false
    v.setOnClickListener(null)
    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) disableClicks(v.getChildAt(i))
}

/** Not a real widget id: previews never touch per-widget settings. */
private const val PREVIEW_WIDGET_ID = Int.MAX_VALUE / 64
