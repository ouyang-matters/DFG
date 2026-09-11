package io.github.ouyangmatters.rounds.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun intSetToString(value: Set<Int>): String = value.sorted().joinToString(",")

    @TypeConverter
    fun stringToIntSet(value: String): Set<Int> =
        value.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    @TypeConverter fun kindToString(value: CheckKind): String = value.name

    @TypeConverter fun stringToKind(value: String): CheckKind = CheckKind.valueOf(value)

    @TypeConverter fun recurrenceToString(value: RecurrenceType): String = value.name

    @TypeConverter
    fun stringToRecurrence(value: String): RecurrenceType = RecurrenceType.valueOf(value)

    @TypeConverter fun unitToString(value: IntervalUnit): String = value.name

    @TypeConverter fun stringToUnit(value: String): IntervalUnit = IntervalUnit.valueOf(value)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    suspend fun getAll(): List<ChecklistItem>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<ChecklistItem?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ChecklistItem?

    @Insert suspend fun insert(item: ChecklistItem): Long

    @Update suspend fun update(item: ChecklistItem)

    @Delete suspend fun delete(item: ChecklistItem)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun observeForItem(itemId: Long): Flow<List<CheckEvent>>

    @Query("SELECT * FROM events WHERE itemId = :itemId AND photoPath IS NOT NULL ORDER BY timestamp DESC")
    fun observePhotosForItem(itemId: Long): Flow<List<CheckEvent>>

    @Query("SELECT COUNT(*) FROM events WHERE itemId = :itemId AND timestamp >= :from AND timestamp < :to")
    suspend fun countInRange(itemId: Long, from: Long, to: Long): Int

    @Query("SELECT timestamp FROM events WHERE itemId = :itemId AND timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun timestampsInRange(itemId: Long, from: Long, to: Long): List<Long>

    @Query("SELECT * FROM events WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<CheckEvent>

    @Query("SELECT photoPath FROM events WHERE photoPath IS NOT NULL")
    suspend fun allPhotoPaths(): List<String>

    @Query("SELECT MAX(timestamp) FROM events WHERE itemId = :itemId")
    suspend fun lastTimestamp(itemId: Long): Long?

    /** Re-emits on any insert or delete, which makes the list screen recompute. */
    @Query("SELECT COUNT(*) FROM events")
    fun observeEventCount(): Flow<Int>

    @Query("SELECT MAX(timestamp) FROM events WHERE itemId = :itemId")
    fun observeLastTimestamp(itemId: Long): Flow<Long?>

    @Insert suspend fun insert(event: CheckEvent): Long

    @Update suspend fun update(event: CheckEvent)

    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE itemId = :itemId ORDER BY id")
    fun observeForItem(itemId: Long): Flow<List<ReminderRule>>

    @Query("SELECT * FROM rules WHERE itemId = :itemId ORDER BY id")
    suspend fun getForItem(itemId: Long): List<ReminderRule>

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM rules WHERE enabled = 1")
    suspend fun getEnabled(): List<ReminderRule>

    @Query("SELECT * FROM rules")
    fun observeAll(): Flow<List<ReminderRule>>

    @Upsert suspend fun upsert(rule: ReminderRule): Long

    @Query("UPDATE rules SET lastEvaluatedDeadline = :deadline WHERE id = :id")
    suspend fun markEvaluated(id: Long, deadline: Long)

    @Delete suspend fun delete(rule: ReminderRule)
}
