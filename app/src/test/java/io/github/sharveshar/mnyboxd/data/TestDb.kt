// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.mnyboxd.data.db.MnyboxdDatabase
import io.github.sharveshar.mnyboxd.data.db.MnyboxdDatabaseCallback

/** In-memory database with the production callback (seed data, foreign keys, integrity triggers). */
fun testDatabase(): MnyboxdDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, MnyboxdDatabase::class.java)
        .addCallback(MnyboxdDatabaseCallback { 1_760_000_000_000L })
        .allowMainThreadQueries()
        .build()
}
