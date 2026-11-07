package io.github.sharveshar.spendly.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.data.db.SpendlyDatabaseCallback

/** In-memory database with the production callback (seed data, foreign keys, integrity triggers). */
fun testDatabase(): SpendlyDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, SpendlyDatabase::class.java)
        .addCallback(SpendlyDatabaseCallback { 1_760_000_000_000L })
        .allowMainThreadQueries()
        .build()
}
