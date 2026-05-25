package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileDirect(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)
}

@Dao
interface DailyProgressDao {
    @Query("SELECT * FROM daily_progress WHERE day = :day LIMIT 1")
    fun getDailyProgressFlow(day: Int): Flow<DailyProgressEntity?>

    @Query("SELECT * FROM daily_progress WHERE day = :day LIMIT 1")
    suspend fun getDailyProgressDirect(day: Int): DailyProgressEntity?

    @Query("SELECT * FROM daily_progress ORDER BY day ASC")
    fun getAllDailyProgressFlow(): Flow<List<DailyProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyProgress(progress: DailyProgressEntity)

    @Query("DELETE FROM daily_progress")
    suspend fun deleteAllProgress()
}

@Dao
interface WeightLogDao {
    @Query("SELECT * FROM weight_log ORDER BY id DESC")
    fun getAllWeightLogsFlow(): Flow<List<WeightLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLogEntity)

    @Delete
    suspend fun deleteWeightLog(log: WeightLogEntity)

    @Query("DELETE FROM weight_log")
    suspend fun deleteAllWeightLogs()
}

@Database(
    entities = [UserProfileEntity::class, DailyProgressEntity::class, WeightLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WeightLossDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun weightLogDao(): WeightLogDao

    companion object {
        @Volatile
        private var INSTANCE: WeightLossDatabase? = null

        fun getDatabase(context: Context): WeightLossDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeightLossDatabase::class.java,
                    "weight_loss_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class WeightLossRepository(private val db: WeightLossDatabase) {
    val userProfile: Flow<UserProfileEntity?> = db.userProfileDao().getUserProfileFlow()
    val allDailyProgress: Flow<List<DailyProgressEntity>> = db.dailyProgressDao().getAllDailyProgressFlow()
    val allWeightLogs: Flow<List<WeightLogEntity>> = db.weightLogDao().getAllWeightLogsFlow()

    suspend fun getUserProfileDirect(): UserProfileEntity? = db.userProfileDao().getUserProfileDirect()

    suspend fun insertUserProfile(profile: UserProfileEntity) {
        db.userProfileDao().insertUserProfile(profile)
    }

    fun getDailyProgressFlow(day: Int): Flow<DailyProgressEntity?> {
        return db.dailyProgressDao().getDailyProgressFlow(day)
    }

    suspend fun getDailyProgressDirect(day: Int): DailyProgressEntity? {
        return db.dailyProgressDao().getDailyProgressDirect(day)
    }

    suspend fun insertDailyProgress(progress: DailyProgressEntity) {
        db.dailyProgressDao().insertDailyProgress(progress)
    }

    suspend fun insertWeightLog(log: WeightLogEntity) {
        db.weightLogDao().insertWeightLog(log)
        
        // Also update user current weight in profile if profile exists
        val currentProfile = db.userProfileDao().getUserProfileDirect()
        if (currentProfile != null) {
            val updatedProfile = currentProfile.copy(currentWeight = log.weight)
            db.userProfileDao().insertUserProfile(updatedProfile)
        }
    }

    suspend fun deleteWeightLog(log: WeightLogEntity) {
        db.weightLogDao().deleteWeightLog(log)
    }

    // Prepare a clean 30 days challenge log
    suspend fun initializeDefault30Days() {
        // Only initialize if not already populated or if we want to reset
        for (day in 1..30) {
            val existing = db.dailyProgressDao().getDailyProgressDirect(day)
            if (existing == null) {
                db.dailyProgressDao().insertDailyProgress(DailyProgressEntity(day = day))
            }
        }
    }

    suspend fun resetAllData() {
        db.dailyProgressDao().deleteAllProgress()
        db.weightLogDao().deleteAllWeightLogs()
        // Delete profile or let user onboarding override it
    }
}
