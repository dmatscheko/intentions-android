package at.matscheko.intentions.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A saved intent. [data] is the Base64 form produced by
 * [at.matscheko.intentions.core.IntentCodec]. Replaces the old `bookmarks`
 * SQLite table (`_id, name, data`) and its manual `CursorAdapter`.
 */
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val data: String,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Bookmark>>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Query("UPDATE bookmarks SET name = :name, data = :data WHERE id = :id")
    suspend fun update(id: Long, name: String, data: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Bookmark::class], version = 1, exportSchema = true)
abstract class BookmarkDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var instance: BookmarkDatabase? = null

        fun get(context: Context): BookmarkDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BookmarkDatabase::class.java,
                "bookmarks.db",
            ).build().also { instance = it }
        }
    }
}
