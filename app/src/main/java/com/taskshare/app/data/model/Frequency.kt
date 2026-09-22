package com.taskshare.app.data.model

/** How often a task is meant to be done: "once every [quantity] [unit]". */
enum class FrequencyUnit(val averageDays: Double) {
    DAY(1.0),
    WEEK(7.0),
    MONTH(30.44),
}

data class Frequency(val quantity: Int, val unit: FrequencyUnit) {
    init {
        require(quantity > 0) { "Frequency quantity must be positive" }
    }

    /** Average interval between occurrences, in days. */
    val intervalDays: Double get() = quantity * unit.averageDays

    fun label(): String {
        val unitWord = unit.name.lowercase().let { if (quantity == 1) it else "${it}s" }
        return "Every $quantity $unitWord"
    }
}

enum class Priority { LOW, MEDIUM, HIGH }
