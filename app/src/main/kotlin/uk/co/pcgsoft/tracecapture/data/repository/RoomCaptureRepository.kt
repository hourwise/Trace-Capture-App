package uk.co.pcgsoft.tracecapture.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemDao
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemFactory
import uk.co.pcgsoft.tracecapture.data.local.CaptureValidator
import uk.co.pcgsoft.tracecapture.data.local.toDomain
import uk.co.pcgsoft.tracecapture.data.local.toEntity
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCaptureRepository @Inject constructor(
    private val dao: CaptureItemDao,
    private val validator: CaptureValidator,
    private val factory: CaptureItemFactory
) : CaptureRepository {

    override fun observeInbox(): Flow<List<CaptureItem>> {
        return dao.observeInbox().map { entities -> entities.toDomain() }
    }

    override fun observeByStatus(status: CaptureStatus): Flow<List<CaptureItem>> {
        return dao.observeByStatus(status).map { entities -> entities.toDomain() }
    }

    override fun search(query: String): Flow<List<CaptureItem>> {
        return dao.search(query).map { entities -> entities.toDomain() }
    }

    override suspend fun getById(id: String): CaptureItem? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun save(item: CaptureItem) {
        val sanitized = validator.sanitize(item)
        validator.validate(sanitized)
        dao.insert(sanitized.toEntity())
    }

    override suspend fun saveAll(items: List<CaptureItem>) {
        val sanitized = items.map { validator.sanitize(it) }
        sanitized.forEach { validator.validate(it) }
        dao.insertAll(sanitized.map { it.toEntity() })
    }

    override suspend fun updateNote(id: String, note: String?) {
        if ((note?.length ?: 0) > CaptureValidator.MAX_NOTE_LENGTH) {
            throw uk.co.pcgsoft.tracecapture.data.local.CaptureValidationException(
                "Note exceeds maximum length of ${CaptureValidator.MAX_NOTE_LENGTH} characters"
            )
        }
        dao.updateNote(id, note, factory.currentEpochMillis())
    }

    override suspend fun markReviewed(id: String) {
        dao.markReviewed(id, factory.currentEpochMillis())
    }

    override suspend fun archive(id: String) {
        dao.archive(id, factory.currentEpochMillis())
    }

    override suspend fun restoreToPending(id: String) {
        dao.restoreToPending(id, factory.currentEpochMillis())
    }

    override suspend fun softDelete(id: String) {
        dao.softDelete(id, factory.currentEpochMillis())
    }

    override suspend fun findExactUrlDuplicates(
        primaryUrl: String,
        excludingId: String?
    ): List<CaptureItem> {
        return dao.findExactUrlDuplicates(primaryUrl, excludingId).toDomain()
    }
}
