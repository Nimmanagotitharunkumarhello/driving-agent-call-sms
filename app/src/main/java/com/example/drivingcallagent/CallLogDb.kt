package com.example.drivingcallagent

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * =============================================================================
 * 1. THE ENTITY (Table structure)
 * =============================================================================
 * Each CallRecord represents a single phone call row saved in our database.
 */
@Entity(tableName = "call_records")
data class CallRecord(
    // autoGenerate = true lets SQLite assign a unique 1, 2, 3... ID to each row
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // The incoming phone number (e.g. "+1234567890" or "Private Number")
    val callerNumber: String,

    // The exact timestamp when the call arrived, stored as milliseconds since Jan 1, 1970
    val timeEpochMs: Long = System.currentTimeMillis(),

    // Status: "ANSWERED_BY_AGENT" (agent answered and spoke) or "MISSED" (hung up early)
    val status: String
)

/**
 * =============================================================================
 * 2. THE DAO (Data Access Object - SQL operations)
 * =============================================================================
 * Defines the operations we can perform on the "call_records" table.
 */
@Dao
interface CallDao {

    /**
     * Inserts a new call record into the database.
     * Marked as 'suspend' so it runs on a background thread and does not freeze the UI.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(callRecord: CallRecord): Long

    /**
     * Returns all call records sorted with the newest calls at the very top.
     * Returning a Flow means whenever a new row is inserted or deleted,
     * the Flow automatically emits the fresh updated list to our UI.
     */
    @Query("SELECT * FROM call_records ORDER BY timeEpochMs DESC")
    fun getAll(): Flow<List<CallRecord>>

    /**
     * Deletes all call records from the table (used by the "Clear" button).
     */
    @Query("DELETE FROM call_records")
    suspend fun clearAll()
}

/**
 * =============================================================================
 * 3. THE DATABASE (Room Database Singleton)
 * =============================================================================
 * AppDatabase is the central connection to our SQLite database.
 * We use a Singleton pattern so only ONE database instance exists throughout the app.
 */
@Database(entities = [CallRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Provides access to our CallDao methods
    abstract fun callDao(): CallDao

    companion object {
        // @Volatile ensures changes to INSTANCE are immediately visible to all threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the existing database instance, or builds it if it doesn't exist yet.
         */
        fun getDatabase(context: Context): AppDatabase {
            // If already created, return it directly
            return INSTANCE ?: synchronized(this) {
                // Double-checked locking to avoid creating two instances from multiple threads
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "driving_call_agent_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
