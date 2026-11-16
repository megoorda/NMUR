package com.nmur.reelnotes.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** Reminder time in millis, 0 = none. */
    val remindAt: Long = 0,
) {
    val content: NoteContent?
        get() = contentJson.takeIf { it.isNotBlank() }?.let {
            runCatching { AppJson.decodeFromString(NoteContent.serializer(), it) }.getOrNull()
        }

    val checkedSet: Set<String> get() = checked.split(",").filter { it.isNotBlank() }.toSet()
}

/** One multiple-choice question for spaced-repetition review. */
@Entity(tableName = "quiz_cards", indices = [Index("noteId"), Index("dueAt")])
data class QuizCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val question: String,
    /** Options joined with "\u001F". */
    val options: String,
    val answerIndex: Int,
    val explanation: String = "",
    /** Days until next review after a correct answer. */
    val intervalDays: Int = 0,
    val dueAt: Long = System.currentTimeMillis(),
    val reviews: Int = 0,
    val lapses: Int = 0,
) {
    val optionList: List<String> get() = options.split(OPTION_SEP)

    companion object {
        const val OPTION_SEP = "\u001F"
    }
}

@Dao
interface QuizDao {
    @Query("SELECT * FROM quiz_cards WHERE dueAt <= :now ORDER BY dueAt LIMIT 50")
    suspend fun due(now: Long = System.currentTimeMillis()): List<QuizCard>

    @Query("SELECT COUNT(*) FROM quiz_cards WHERE dueAt <= :now")
    fun dueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM quiz_cards WHERE dueAt <= :now")
    suspend fun dueCountNow(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM quiz_cards WHERE noteId = :noteId")
    fun countForNote(noteId: Long): Flow<Int>

    @Query("SELECT * FROM quiz_cards")
    suspend fun all(): List<QuizCard>

    @Insert
    suspend fun insertAll(cards: List<QuizCard>)

    @Update
    suspend fun update(card: QuizCard)

    @Query("DELETE FROM quiz_cards WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun all(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun allNow(): List<Note>

    @Query("SELECT * FROM notes WHERE remindAt > :now ORDER BY remindAt LIMIT 1")
    suspend fun nextReminder(now: Long = System.currentTimeMillis()): Note?

    @Query("SELECT * FROM notes WHERE remindAt > 0")
    suspend fun withReminders(): List<Note>

    @Query("SELECT COUNT(*) FROM notes WHERE sourceUrl = :url")
    suspend fun countByUrl(url: String): Int

    @Query("SELECT id FROM notes WHERE status = 'pending'")
    suspend fun pendingIds(): List<Long>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Note::class, QuizCard::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun quiz(): QuizDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN remindAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS quiz_cards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "noteId INTEGER NOT NULL, question TEXT NOT NULL, options TEXT NOT NULL, " +
                        "answerIndex INTEGER NOT NULL, explanation TEXT NOT NULL, intervalDays INTEGER NOT NULL, " +
                        "dueAt INTEGER NOT NULL, reviews INTEGER NOT NULL, lapses INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_cards_noteId ON quiz_cards (noteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_cards_dueAt ON quiz_cards (dueAt)")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "reelnotes.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
