package io.github.ouyangmatters.rounds.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class Repository(context: Context) {

    private val db = AppDatabase.get(context)
    private val items = db.itemDao()
    private val events = db.eventDao()
    private val rules = db.ruleDao()

    val photos = PhotoStore(context)

    fun observeItems(): Flow<List<ChecklistItem>> = items.observeActive()

    fun observeItem(id: Long): Flow<ChecklistItem?> = items.observe(id)

    fun observeEvents(itemId: Long): Flow<List<CheckEvent>> = events.observeForItem(itemId)

    fun observePhotoEvents(itemId: Long): Flow<List<CheckEvent>> =
        events.observePhotosForItem(itemId)

    fun observeRules(itemId: Long): Flow<List<ReminderRule>> = rules.observeForItem(itemId)

    fun observeEventChanges(): Flow<Int> = events.observeEventCount()

    fun observeAllRules(): Flow<List<ReminderRule>> = rules.observeAll()

    suspend fun getItem(id: Long): ChecklistItem? = items.get(id)

    suspend fun getAllItems(): List<ChecklistItem> = items.getAll()

    suspend fun enabledRules(): List<ReminderRule> = rules.getEnabled()

    suspend fun createItem(item: ChecklistItem): Long = items.insert(item)

    /**
     * Updates item settings. Turning a way of logging off only limits what can be
     * added next, so the events table is deliberately left alone.
     */
    suspend fun updateItem(item: ChecklistItem) = items.update(item)

    suspend fun archiveItem(item: ChecklistItem) = items.update(item.copy(archived = true))

    suspend fun deleteItem(item: ChecklistItem) {
        items.delete(item)
        // Events go with it via CASCADE; then drop the now-unreferenced photo files.
        pruneOrphanPhotos()
    }

    /** Logs a check. */
    suspend fun addEvent(
        itemId: Long,
        kind: CheckKind,
        note: String?,
        photoPath: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): Long = events.insert(
        CheckEvent(
            itemId = itemId,
            kind = kind,
            timestamp = timestamp,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            photoPath = photoPath,
        ),
    )

    suspend fun updateEventNote(event: CheckEvent, note: String?) =
        events.update(event.copy(note = note?.trim()?.takeIf { it.isNotEmpty() }))

    /** Deletes entries together with their photo files. */
    suspend fun deleteEvents(ids: List<Long>) {
        if (ids.isEmpty()) return
        val targets = events.getByIds(ids)
        events.deleteByIds(ids)
        targets.mapNotNull { it.photoPath }.forEach { photos.delete(it) }
    }

    suspend fun lastEventAt(itemId: Long): Long? = events.lastTimestamp(itemId)

    suspend fun countEventsInWindow(itemId: Long, window: CheckWindow): Int =
        events.countInRange(itemId, window.startMillis, window.endMillis)

    suspend fun eventTimesInWindow(itemId: Long, window: CheckWindow): List<Long> =
        events.timestampsInRange(itemId, window.startMillis, window.endMillis)

    suspend fun rulesForItem(itemId: Long): List<ReminderRule> = rules.getForItem(itemId)

    suspend fun upsertRule(rule: ReminderRule): Long = rules.upsert(rule)

    suspend fun deleteRuleById(id: Long) = rules.deleteById(id)

    suspend fun deleteRule(rule: ReminderRule) = rules.delete(rule)

    suspend fun markRuleEvaluated(ruleId: Long, deadline: Long) =
        rules.markEvaluated(ruleId, deadline)

    /** Keeps files still referenced by an entry and deletes the rest. */
    private suspend fun pruneOrphanPhotos() {
        photos.pruneOrphans(events.allPhotoPaths().toSet())
    }

    companion object {
        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
