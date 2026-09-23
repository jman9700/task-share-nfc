package com.taskshare.app.ui.calendar

import java.time.LocalDate
import java.time.YearMonth

/** One cell in a month calendar grid. [inCurrentMonth] is false for the leading/trailing days
 *  from adjacent months shown to fill out a full week row. */
data class MonthGridDay(val date: LocalDate, val inCurrentMonth: Boolean)

/**
 * Pure, unit-testable month-grid math, kept separate from CalendarViewModel so the "which 42
 * dates does a traditional month calendar show, and where does the real month start/end within
 * them" logic can be verified without Android. Weeks start on Sunday, matching the week-view
 * convention elsewhere in this screen.
 */
object MonthGrid {

    /** Always 42 dates (6 full Sunday-Saturday weeks) — a fixed size avoids the grid's height
     *  jumping between 5 and 6 rows as the user pages between months. */
    fun forMonth(anchor: LocalDate): List<MonthGridDay> {
        val month = YearMonth.from(anchor)
        val monthStart = month.atDay(1)
        val gridStart = monthStart.minusDays((monthStart.dayOfWeek.value % 7).toLong())
        return (0 until 42).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            MonthGridDay(date, YearMonth.from(date) == month)
        }
    }
}
