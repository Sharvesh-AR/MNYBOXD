// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.navigation

import io.github.sharveshar.spendly.AppTab
import io.github.sharveshar.spendly.AppTab.ACCOUNTS
import io.github.sharveshar.spendly.AppTab.ACTIVITY
import io.github.sharveshar.spendly.AppTab.BUDGETS
import io.github.sharveshar.spendly.AppTab.HOME
import io.github.sharveshar.spendly.AppTab.INSIGHTS
import org.junit.Assert.assertEquals
import org.junit.Test

class DockOrderTest {
    @Test fun `nothing saved gives the default order`() {
        assertEquals(listOf(HOME, ACTIVITY, ACCOUNTS, BUDGETS, INSIGHTS), DockOrder.parse(null))
        assertEquals(DockOrder.DEFAULT, DockOrder.parse(""))
        assertEquals(DockOrder.DEFAULT, DockOrder.parse("   "))
    }

    @Test fun `a saved order round-trips`() {
        val order = listOf(INSIGHTS, HOME, BUDGETS, ACTIVITY, ACCOUNTS)
        assertEquals(order, DockOrder.parse(DockOrder.serialize(order)))
    }

    @Test fun `every tab appears exactly once whatever was stored`() {
        // Unknown names dropped, duplicates removed, missing tabs added at the end in default order.
        assertEquals(listOf(BUDGETS, HOME, ACTIVITY, ACCOUNTS, INSIGHTS), DockOrder.parse("BUDGETS,NOPE,BUDGETS,HOME"))
        assertEquals(DockOrder.DEFAULT, DockOrder.parse("garbage"))
        assertEquals(listOf(INSIGHTS, HOME, ACTIVITY, ACCOUNTS, BUDGETS), DockOrder.parse(" INSIGHTS ,"))
        AppTab.entries.forEach { tab -> assertEquals(1, DockOrder.parse("$tab,$tab").count { it == tab }) }
    }

    @Test fun `moving a tab shifts the ones in between`() {
        val d = DockOrder.DEFAULT
        assertEquals(listOf(INSIGHTS, HOME, ACTIVITY, ACCOUNTS, BUDGETS), DockOrder.move(d, 4, 0))
        assertEquals(listOf(ACTIVITY, ACCOUNTS, HOME, BUDGETS, INSIGHTS), DockOrder.move(d, 0, 2))
        assertEquals(listOf(HOME, ACCOUNTS, ACTIVITY, BUDGETS, INSIGHTS), DockOrder.move(d, 1, 2))
    }

    @Test fun `moves that go nowhere leave the order alone`() {
        val d = DockOrder.DEFAULT
        assertEquals(d, DockOrder.move(d, 2, 2))
        assertEquals(d, DockOrder.move(d, -1, 3))
        assertEquals(d, DockOrder.move(d, 9, 0))
        // Targets past either end clamp to the end.
        assertEquals(listOf(ACTIVITY, ACCOUNTS, BUDGETS, INSIGHTS, HOME), DockOrder.move(d, 0, 99))
        assertEquals(listOf(INSIGHTS, HOME, ACTIVITY, ACCOUNTS, BUDGETS), DockOrder.move(d, 4, -5))
    }

    @Test fun `a dragged tab lands in the nearest slot and never off the dock`() {
        val slot = 100f
        assertEquals(2, DockOrder.dropIndex(2, 0f, slot, 5))
        assertEquals(2, DockOrder.dropIndex(2, 49f, slot, 5)) // less than half a slot: stays
        assertEquals(3, DockOrder.dropIndex(2, 51f, slot, 5)) // more than half: next slot
        assertEquals(0, DockOrder.dropIndex(2, -180f, slot, 5))
        assertEquals(0, DockOrder.dropIndex(2, -900f, slot, 5)) // far past the start
        assertEquals(4, DockOrder.dropIndex(2, 900f, slot, 5)) // far past the end
        assertEquals(1, DockOrder.dropIndex(1, 30f, 0f, 5)) // not measured yet: stays put
        assertEquals(0, DockOrder.dropIndex(3, 0f, slot, 0))
    }
}
