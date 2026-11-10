// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly

/** Release builds contain no debug tooling. */
object DebugTools {
    const val available = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun loadSampleData(repos: Repositories): String = "Not available"
}
