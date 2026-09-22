package com.taskshare.app.data.db

import androidx.room.TypeConverter
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.Priority
import java.time.Instant

class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun frequencyUnitToString(value: FrequencyUnit): String = value.name

    @TypeConverter
    fun stringToFrequencyUnit(value: String): FrequencyUnit = FrequencyUnit.valueOf(value)

    @TypeConverter
    fun priorityToString(value: Priority): String = value.name

    @TypeConverter
    fun stringToPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter
    fun stringListToString(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",")
}
