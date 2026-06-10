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

/** One intercepted broadcast/intent recorded by the sniffer. */
@Entity(tableName = "sniffed")
data class SniffedBroadcast(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val timestamp: Long,
    val extras: String,
    /** Base64-encoded intent (see IntentCodec) so it can be reloaded into the editor. */
    val data: String,
)

@Dao
interface SniffedDao {
    @Query("SELECT * FROM sniffed ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SniffedBroadcast>>

    @Insert
    suspend fun insert(item: SniffedBroadcast)

    @Query("DELETE FROM sniffed")
    suspend fun clear()
}

@Database(entities = [SniffedBroadcast::class], version = 1, exportSchema = true)
abstract class SnifferDatabase : RoomDatabase() {
    abstract fun dao(): SniffedDao

    companion object {
        @Volatile
        private var instance: SnifferDatabase? = null

        fun get(context: Context): SnifferDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SnifferDatabase::class.java,
                "sniffer.db",
            ).build().also { instance = it }
        }
    }
}
