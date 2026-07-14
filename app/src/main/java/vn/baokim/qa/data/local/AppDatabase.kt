package vn.baokim.qa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Offline cache DB (E4.4, #7). Holds only non-sensitive cached view data — never the
 * PAT or session token, which stay in EncryptedSharedPreferences (OPSEC §7).
 */
@Database(
    entities = [MyWorkTaskEntity::class],
    version = 3, // v3: + customs (custom-status slugs) on my_work_tasks (E5.5)
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun myWorkDao(): MyWorkDao
}
