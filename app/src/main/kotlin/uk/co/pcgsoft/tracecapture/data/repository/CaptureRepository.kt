package uk.co.pcgsoft.tracecapture.data.repository

import kotlinx.coroutines.flow.Flow
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus

interface CaptureRepository {
    fun observeInbox(): Flow<List<CaptureItem>>
    fun observeByStatus(status: CaptureStatus): Flow<List<CaptureItem>>
    fun search(query: String): Flow<List<CaptureItem>>

    suspend fun getById(id: String): CaptureItem?
    suspend fun save(item: CaptureItem)
    suspend fun saveAll(items: List<CaptureItem>)
    suspend fun updateNote(id: String, note: String?)
    suspend fun markReviewed(id: String)
    suspend fun archive(id: String)
    suspend fun restoreToPending(id: String)
    suspend fun softDelete(id: String)

    suspend fun findExactUrlDuplicates(
        primaryUrl: String,
        excludingId: String? = null
    ): List<CaptureItem>
}
