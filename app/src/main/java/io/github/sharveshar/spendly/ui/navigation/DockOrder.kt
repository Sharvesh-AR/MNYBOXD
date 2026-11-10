// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.navigation

import io.github.sharveshar.spendly.AppTab
import kotlin.math.roundToInt

/**
 * The bottom dock's tab order, as the user arranged it by dragging. Pure logic, kept apart from
 * the UI so it can be tested on its own.
 */
object DockOrder {
    val DEFAULT: List<AppTab> = AppTab.entries.toList()

    /**
     * Reads a saved order. Unknown or repeated names are dropped and any tab missing from the
     * saved list goes at the end, so the dock always shows every tab exactly once.
     */
    fun parse(stored: String?): List<AppTab> {
        if (stored.isNullOrBlank()) return DEFAULT
        val known = stored.split(',').mapNotNull { n -> AppTab.entries.firstOrNull { it.name == n.trim() } }.distinct()
        return known + DEFAULT.filter { it !in known }
    }

    fun serialize(order: List<AppTab>): String = order.joinToString(",") { it.name }

    /** [order] with the tab at [from] moved to [to]; the others keep their relative order. */
    fun move(order: List<AppTab>, from: Int, to: Int): List<AppTab> {
        if (from !in order.indices) return order
        val target = to.coerceIn(0, order.lastIndex)
        if (from == target) return order
        return order.toMutableList().apply { add(target, removeAt(from)) }
    }

    /**
     * The slot a tab dragged from [start] by [dragPx] would land in: the nearest whole slot,
     * never off either end of the dock.
     */
    fun dropIndex(start: Int, dragPx: Float, slotPx: Float, count: Int): Int {
        if (count <= 0) return 0
        if (slotPx <= 0f) return start.coerceIn(0, count - 1)
        return (start + (dragPx / slotPx).roundToInt()).coerceIn(0, count - 1)
    }
}
