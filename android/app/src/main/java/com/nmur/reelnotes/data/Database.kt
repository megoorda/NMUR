package com.nmur.reelnotes.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Text that was shared (usually the reel link). */
    val sharedText: String = "",
    val sourceUrl: String = "",
    /** Local copies of shared videos/screenshots, newline separated. */
    val localFiles: String = "",
    val status: String = Status.PENDING,
    val error: String = "",
    val provider: String = "",
    val title: String = "",
    val category: String = "",
    val summary: String = "",
    val creator: String = "",
    val caption: String = "",
    val transcript: String = "",
    /** Full NoteContent as JSON. */
    val contentJson: String = "",
    /** Lowercased text used for search. */
    val searchText: String = "",
    /** Indexes of completed steps / roadmap items, e.g. "s0,s3,r1.2". */
    val checked: String = "",
    val favorite: Boolean = false,
) {
    val content: NoteContent?
        get() = contentJson.takeIf { it.isNotBlank() }?.let {
            runCatching { AppJson.decodeFromString(NoteContent.serializer(), it) }.getOrNull()
        }

    val checkedSet: Set<String> get() = checked.split(",").filter { it.isNotBlank() }.toSet()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun all(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Query("SELECT id FROM notes WHERE status = 'pending'")
    suspend fun pendingIds(): List<Long>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "reelnotes.db")
                .build().also { instance = it }
        }
    }
}
