package io.github.sharveshar.spendly.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the exported Room schema (app/schemas) against the entities, and every migration
 * path from version 1 to [SpendlyDatabase.VERSION]. When a new version is added, its schema is
 * exported automatically and this test must be extended with the new migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SpendlyDatabase::class.java)

    @Test fun `version 1 schema matches entities`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO accounts (name, institution, type, opening_balance_paise, color_index, is_archived, sort_order, created_at) VALUES ('Cash', '', 'CASH', 0, 0, 0, 0, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, SpendlyDatabase.VERSION, true, *SpendlyDatabase.MIGRATIONS)
        db.query("SELECT COUNT(*) FROM accounts").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.close()
    }

    @Test fun `migration to 2 adds month budgets and keeps everything else`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO categories (id, `key`, name, kind, icon_key, color_index, is_archived, sort_order) VALUES (1, 'food', 'Food', 'EXPENSE', 'restaurant', 0, 0, 0)")
            execSQL("INSERT INTO budgets (category_id, amount_paise, updated_at) VALUES (NULL, 2000000, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, *SpendlyDatabase.MIGRATIONS)
        db.execSQL("INSERT INTO budget_overrides (month, category_id, amount_paise, updated_at) VALUES ('2026-12', 1, 900000, 0)")
        db.query("SELECT COUNT(*) FROM budgets").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT amount_paise FROM budget_overrides WHERE month = '2026-12'").use { it.moveToFirst(); assertEquals(900000, it.getInt(0)) }
        db.close()
    }

    private companion object { const val TEST_DB = "migration-test" }
}
