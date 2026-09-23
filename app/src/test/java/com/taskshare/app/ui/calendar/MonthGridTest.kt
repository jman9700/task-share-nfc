package com.taskshare.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {

    @Test
    fun `always returns exactly 42 dates`() {
        assertEquals(42, MonthGrid.forMonth(LocalDate.of(2026, 2, 15)).size)
    }

    @Test
    fun `first date is the Sunday on or before the 1st of the month`() {
        // 2026-02-01 is a Sunday, so the grid should start exactly there with no padding.
        val grid = MonthGrid.forMonth(LocalDate.of(2026, 2, 15))
        assertEquals(LocalDate.of(2026, 2, 1), grid.first().date)
        assertEquals(DayOfWeek.SUNDAY, grid.first().date.dayOfWeek)
    }

    @Test
    fun `pads leading days from the previous month when the 1st isn't a Sunday`() {
        // 2026-03-01 is a Sunday too (Feb 2026 has 28 days), so use a month whose 1st isn't
        // a Sunday: 2026-04-01 is a Wednesday.
        val grid = MonthGrid.forMonth(LocalDate.of(2026, 4, 10))
        assertEquals(DayOfWeek.SUNDAY, grid.first().date.dayOfWeek)
        assertTrue(grid.first().date.isBefore(LocalDate.of(2026, 4, 1)))
        assertTrue(grid.none { it.date == LocalDate.of(2026, 4, 1) && !it.inCurrentMonth })
    }

    @Test
    fun `every date in the target month is marked inCurrentMonth, adjacent-month padding is not`() {
        val target = YearMonth.of(2026, 4)
        val grid = MonthGrid.forMonth(LocalDate.of(2026, 4, 1))

        grid.forEach { cell ->
            assertEquals(YearMonth.from(cell.date) == target, cell.inCurrentMonth)
        }
        assertTrue(grid.any { it.inCurrentMonth })
        assertEquals(target.lengthOfMonth(), grid.count { it.inCurrentMonth })
    }

    @Test
    fun `dates are consecutive with no gaps`() {
        val grid = MonthGrid.forMonth(LocalDate.of(2026, 4, 1))
        for (i in 1 until grid.size) {
            assertEquals(grid[i - 1].date.plusDays(1), grid[i].date)
        }
    }
}
