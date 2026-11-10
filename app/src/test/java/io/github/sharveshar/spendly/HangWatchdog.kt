// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.schedule
import kotlin.time.Duration

/**
 * Fails a test that runs longer than [limit] instead of letting it hang the build.
 *
 * On expiry it prints every thread's stack (so the log shows what the test was waiting on) and
 * interrupts the test thread, which ends blocking waits such as runBlocking, Thread.sleep and
 * Compose's waitUntil. Unlike JUnit's Timeout rule it leaves the test on its own thread:
 * Robolectric and Compose tests must run on the thread that owns the main looper.
 *
 * Gradle's Test task timeout (app/build.gradle.kts) backs this up for hangs an interrupt can't end.
 */
class HangWatchdog(private val limit: Duration) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val testThread = Thread.currentThread()
            val expired = AtomicBoolean(false)
            val timer = Timer("hang-watchdog", true)
            timer.schedule(limit.inWholeMilliseconds) {
                expired.set(true)
                System.err.println(threadDump("${description.displayName} still running after $limit"))
                testThread.interrupt()
            }
            val failure = try {
                base.evaluate()
                null
            } catch (t: Throwable) {
                t
            } finally {
                timer.cancel()
            }
            if (expired.get()) {
                Thread.interrupted() // Don't leak the interrupt into the next test.
                throw AssertionError("${description.displayName} timed out after $limit (thread dump on stderr)", failure)
            }
            if (failure != null) throw failure
        }
    }

    private fun threadDump(title: String) = buildString {
        appendLine("=== $title; thread dump ===")
        for ((thread, stack) in Thread.getAllStackTraces()) {
            appendLine("\"${thread.name}\" ${thread.state}")
            stack.forEach { appendLine("    at $it") }
        }
    }
}

private fun Timer.schedule(delayMs: Long, action: () -> Unit) = schedule(object : TimerTask() {
    override fun run() = action()
}, delayMs)
