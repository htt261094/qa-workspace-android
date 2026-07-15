package vn.baokim.qa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BugDao {

    /** Emits the cached bug list in backend order; re-emits on every [replaceAll]. */
    @Query("SELECT * FROM bug_log ORDER BY rowOrder")
    fun observeAll(): Flow<List<BugEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bugs: List<BugEntity>)

    @Query("DELETE FROM bug_log")
    suspend fun clear()

    /** Full replace in one transaction so observers never see a half-empty list. */
    @Transaction
    suspend fun replaceAll(bugs: List<BugEntity>) {
        clear()
        insertAll(bugs)
    }
}
