package com.taskshare.app

import android.app.Application
import com.taskshare.app.data.db.AppDatabase
import com.taskshare.app.data.repository.TaskRepository
import com.taskshare.app.nfc.LocalDeviceIdentity

class TaskShareApp : Application() {
    lateinit var repository: TaskRepository
        private set

    val localDeviceId: String by lazy { LocalDeviceIdentity.deviceId(this) }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repository = TaskRepository(
            taskDao = db.taskDao(),
            instanceDao = db.taskInstanceDao(),
            userDao = db.householdUserDao(),
            localDeviceId = localDeviceId,
        )
    }
}
