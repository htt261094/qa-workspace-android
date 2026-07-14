package vn.baokim.qa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MyWorkDao {

    /** Emits the cached snapshot in server order; re-emits on every [replaceAll]. */
    @Query("SELECT * FROM my_work_tasks ORDER BY bucketOrder, taskOrder")
    fun observeAll(): Flow<List<MyWorkTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<MyWorkTaskEntity>)

    @Query("DELETE FROM my_work_tasks")
    suspend fun clear()

    /** Full replace in one transaction so observers never see a half-empty list. */
    @Transaction
    suspend fun replaceAll(tasks: List<MyWorkTaskEntity>) {
        clear()
        insertAll(tasks)
    }
}
