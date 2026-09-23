package com.taskshare.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taskshare.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

enum class CalendarMode { DAY, WEEK, MONTH }

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.WEEK,
    val rangeLabel: String = "",
    val entriesByDay: Map<LocalDate, List<CalendarEntry>> = emptyMap(),
    /** The 7 dates (Sunday–Saturday) of the current week — only meaningful in WEEK mode. */
    val weekDates: List<LocalDate> = emptyList(),
    /** The 42-cell grid for the current month — only meaningful in MONTH mode. See MonthGrid. */
    val monthGrid: List<MonthGridDay> = emptyList(),
    /** The date whose agenda shows below the month grid; also the date shown in DAY mode. */
    val selectedDate: LocalDate = LocalDate.now(),
)

class CalendarViewModel(private val repository: TaskRepository) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val mode = MutableStateFlow(CalendarMode.WEEK)
    private val anchor = MutableStateFlow(LocalDate.now(zone))
    private val selectedDate = MutableStateFlow(LocalDate.now(zone))

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.observeActiveTasks(),
        repository.observeInstances(),
        mode,
        anchor,
        selectedDate,
    ) { tasks, instances, mode, anchor, selectedDate ->
        val (start, end) = rangeFor(mode, anchor)
        val entries = CalendarProjection.buildEntries(
            tasks, instances,
            start.atStartOfDay(zone).toInstant(),
            end.atStartOfDay(zone).toInstant(),
        )
        val byDay = entries.groupBy { it.at.atZone(zone).toLocalDate() }
        CalendarUiState(
            mode = mode,
            rangeLabel = "$start – ${end.minusDays(1)}",
            entriesByDay = byDay,
            weekDates = if (mode == CalendarMode.WEEK) (0..6).map { start.plusDays(it.toLong()) } else emptyList(),
            monthGrid = if (mode == CalendarMode.MONTH) MonthGrid.forMonth(anchor) else emptyList(),
            selectedDate = if (mode == CalendarMode.DAY) anchor else selectedDate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun setMode(newMode: CalendarMode) {
        mode.value = newMode
    }

    fun goToday() {
        val today = LocalDate.now(zone)
        anchor.value = today
        selectedDate.value = today
    }

    fun step(forward: Boolean) {
        val delta = if (forward) 1 else -1
        anchor.value = when (mode.value) {
            CalendarMode.DAY -> anchor.value.plusDays(delta.toLong())
            CalendarMode.WEEK -> anchor.value.plusWeeks(delta.toLong())
            CalendarMode.MONTH -> anchor.value.plusMonths(delta.toLong())
        }
    }

    /** Picks which day's agenda shows below the month grid. */
    fun selectDay(date: LocalDate) {
        selectedDate.value = date
    }

    private fun rangeFor(mode: CalendarMode, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (mode) {
        CalendarMode.DAY -> anchor to anchor.plusDays(1)
        CalendarMode.WEEK -> {
            val start = anchor.minusDays((anchor.dayOfWeek.value % 7).toLong()) // week starts Sunday
            start to start.plusDays(7)
        }
        CalendarMode.MONTH -> {
            // The grid can show days from adjacent months (see MonthGrid), so entries are
            // fetched for the full 42-day grid range, not just the calendar month, otherwise
            // padding days would always show as empty even when they have real entries.
            val grid = MonthGrid.forMonth(anchor)
            grid.first().date to grid.last().date.plusDays(1)
        }
    }

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory {
            initializer { CalendarViewModel(repository) }
        }
    }
}
