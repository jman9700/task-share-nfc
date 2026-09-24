package com.taskshare.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.taskshare.app.data.db.entity.HouseholdUserEntity
import com.taskshare.app.data.db.entity.TaskEntity
import com.taskshare.app.data.db.entity.TaskInstanceEntity
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [TaskEntity::class, TaskInstanceEntity::class, HouseholdUserEntity::class],
    version = 2, // v2: added Task.startDate, made frequency nullable (one-time tasks)
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskInstanceDao(): TaskInstanceDao
    abstract fun householdUserDao(): HouseholdUserDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): AppDatabase {
            val passphrase = DbKeyProvider.getOrCreatePassphrase(context)
            val factory = SupportFactory(net.sqlcipher.database.SQLiteDatabase.getBytes(passphrase))
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "task-share.db")
                .openHelperFactory(factory)
                // Pre-release app, no installed base to preserve data for yet — destructive
                // migration is the standard, simplest choice until there's a real migration to
                // write. Revisit before this app ever ships with real user data at stake.
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
