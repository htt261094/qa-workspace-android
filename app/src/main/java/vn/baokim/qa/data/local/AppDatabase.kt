package vn.baokim.qa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Offline cache DB (E4.4, #7). Holds only non-sensitive cached view data — never the
 * PAT or session token, which stay in EncryptedSharedPreferences (OPSEC §7).
 */
@Database(
    entities = [MyWorkTaskEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun myWorkDao(): MyWorkDao
}
