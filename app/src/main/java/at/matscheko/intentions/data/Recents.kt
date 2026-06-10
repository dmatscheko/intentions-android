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

/** An intent that was executed, kept as an automatic history. */
@Entity(tableName = "recents")
data class RecentIntent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val timestamp: Long,
    /** Base64-encoded intent (see IntentCodec). */
    val data: String,
)

@Dao
interface RecentDao {
    @Query("SELECT * FROM recents ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<RecentIntent>>

    @Insert
    suspend fun insert(item: RecentIntent)

    /** Remove any existing row with identical intent data (so re-runs move to the top). */
    @Query("DELETE FROM recents WHERE data = :data")
    suspend fun deleteByData(data: String)

    @Query("DELETE FROM recents WHERE id = :id")
    suspend fun delete(id: Long)

    /** Trim history to the most recent [keep] rows. */
    @Query("DELETE FROM recents WHERE id NOT IN (SELECT id FROM recents ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM recents")
    suspend fun clear()
}

@Database(entities = [RecentIntent::class], version = 1, exportSchema = true)
abstract class RecentsDatabase : RoomDatabase() {
    abstract fun dao(): RecentDao

    companion object {
        @Volatile
        private var instance: RecentsDatabase? = null

        fun get(context: Context): RecentsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RecentsDatabase::class.java,
                "recents.db",
            ).build().also { instance = it }
        }
    }
}
