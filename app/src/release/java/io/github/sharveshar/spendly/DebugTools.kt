package io.github.sharveshar.spendly

/** Release builds contain no debug tooling. */
object DebugTools {
    const val available = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun loadSampleData(repos: Repositories): String = "Not available"
}
