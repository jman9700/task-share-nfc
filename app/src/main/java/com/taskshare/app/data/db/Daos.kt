package com.taskshare.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.taskshare.app.data.db.entity.HouseholdUserEntity
import com.taskshare.app.data.db.entity.TaskEntity
import com.taskshare.app.data.db.entity.TaskInstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE archived = 0")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET archived = 1 WHERE id = :taskId")
    suspend fun archive(taskId: String)
}

@Dao
interface TaskInstanceDao {
    @Query("SELECT * FROM task_instances")
    fun observeAll(): Flow<List<TaskInstanceEntity>>

    @Query("SELECT * FROM task_instances")
    suspend fun getAll(): List<TaskInstanceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(instance: TaskInstanceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(instances: List<TaskInstanceEntity>)
}

@Dao
interface HouseholdUserDao {
    @Query("SELECT * FROM household_users")
    fun observeAll(): Flow<List<HouseholdUserEntity>>

    @Query("SELECT * FROM household_users")
    suspend fun getAll(): List<HouseholdUserEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(user: HouseholdUserEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(users: List<HouseholdUserEntity>)
}
