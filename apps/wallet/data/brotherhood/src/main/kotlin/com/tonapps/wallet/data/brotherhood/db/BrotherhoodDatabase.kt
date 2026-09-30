package com.tonapps.wallet.data.brotherhood.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        ContractCacheEntity::class,
        TokenMetadataCacheEntity::class,
        AddressBookCacheEntity::class,
        WatchedLocationEntity::class,
        TrackedPersonalTokenEntity::class,
        KnownPollEntity::class,
        WatchedDnsDomainEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BrotherhoodDatabase : RoomDatabase() {

    abstract fun brotherhoodDao(): BrotherhoodDao

    companion object {
        private const val DATABASE_NAME = "brotherhood_cache.db"

        @Volatile
        private var instance: BrotherhoodDatabase? = null

        fun getInstance(context: Context): BrotherhoodDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrotherhoodDatabase::class.java,
                    DATABASE_NAME,
                )
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
