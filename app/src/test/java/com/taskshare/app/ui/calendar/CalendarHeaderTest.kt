package com.taskshare.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarHeaderTest {

    @Test
    fun `month mode shows month name and year`() {
        val (title, subtitle) = headerFor(
            CalendarMode.MONTH, LocalDate.of(2026, 4, 15),
            LocalDate.of(2026, 3, 29), LocalDate.of(2026, 5, 10),
        )
        assertEquals("April", title)
        assertEquals("2026", subtitle)
    }

    @Test
    fun `week mode within a single month shows a compact day range`() {
        val (title, subtitle) = headerFor(
            CalendarMode.WEEK, LocalDate.of(2026, 9, 23),
            LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 27),
        )
        assertEquals("Sep 20 – 26", title)
        assertEquals("2026", subtitle)
    }

    @Test
    fun `week mode spanning two months shows both month abbreviations`() {
        val (title, subtitle) = headerFor(
            CalendarMode.WEEK, LocalDate.of(2026, 9, 29),
            LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 4),
        )
        assertEquals("Sep 27 – Oct 3", title)
        assertEquals("2026", subtitle)
    }

    @Test
    fun `day mode shows weekday name and full date`() {
        val (title, subtitle) = headerFor(
            CalendarMode.DAY, LocalDate.of(2026, 9, 24),
            LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25),
        )
        assertEquals("Thursday", title)
        assertEquals("September 24, 2026", subtitle)
    }
}
