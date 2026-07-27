package uk.co.pcgsoft.tracecapture.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

@Dao
interface CaptureItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CaptureItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CaptureItemEntity>)

    @Update
    suspend fun update(item: CaptureItemEntity)

    @Query("SELECT * FROM capture_items WHERE id = :id")
    suspend fun getById(id: String): CaptureItemEntity?

    @Query("SELECT * FROM capture_items WHERE id = :id")
    fun observeById(id: String): Flow<CaptureItemEntity?>

    @Query("SELECT * FROM capture_items WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeInbox(): Flow<List<CaptureItemEntity>>

    @Query("SELECT * FROM capture_items WHERE status = :status AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByStatus(status: CaptureStatus): Flow<List<CaptureItemEntity>>

    @Query("SELECT * FROM capture_items WHERE sync_status = :syncStatus AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeBySyncStatus(syncStatus: SyncStatus): Flow<List<CaptureItemEntity>>

    @Query(
        """
        SELECT * FROM capture_items 
        WHERE deleted_at IS NULL 
        AND (original_content LIKE '%' || :query || '%' 
             OR primary_url LIKE '%' || :query || '%'
             OR note LIKE '%' || :query || '%'
             OR source_label LIKE '%' || :query || '%')
        ORDER BY created_at DESC
        """
    )
    fun search(query: String): Flow<List<CaptureItemEntity>>

    @Query("SELECT * FROM capture_items WHERE primary_url = :primaryUrl AND id != COALESCE(:excludingId, '') AND deleted_at IS NULL")
    suspend fun findExactUrlDuplicates(primaryUrl: String, excludingId: String? = null): List<CaptureItemEntity>

    @Query(
        """
        SELECT * FROM capture_items 
        WHERE primary_url = :primaryUrl 
        AND created_at > :sinceEpochMillis 
        AND id != COALESCE(:excludingId, '') 
        AND deleted_at IS NULL
        ORDER BY created_at DESC
        """
    )
    suspend fun findRecentUrlDuplicates(
        primaryUrl: String,
        sinceEpochMillis: Long,
        excludingId: String? = null
    ): List<CaptureItemEntity>

    @Query("UPDATE capture_items SET status = 'REVIEWED', updated_at = :now WHERE id = :id")
    suspend fun markReviewed(id: String, now: Long)

    @Query("UPDATE capture_items SET status = 'ARCHIVED', archived_at = :now, updated_at = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    @Query("UPDATE capture_items SET status = 'PENDING', archived_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restoreToPending(id: String, now: Long)

    @Query("UPDATE capture_items SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE capture_items SET note = :note, updated_at = :now WHERE id = :id")
    suspend fun updateNote(id: String, note: String?, now: Long)

    @Query("SELECT COUNT(*) FROM capture_items")
    suspend fun count(): Int

    @Query("DELETE FROM capture_items")
    suspend fun deleteAll()
}
