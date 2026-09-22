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
)

class CalendarViewModel(private val repository: TaskRepository) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val mode = MutableStateFlow(CalendarMode.WEEK)
    private val anchor = MutableStateFlow(LocalDate.now(zone))

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.observeActiveTasks(),
        repository.observeInstances(),
        mode,
        anchor,
    ) { tasks, instances, mode, anchor ->
        val (start, end) = rangeFor(mode, anchor)
        val entries = CalendarProjection.buildEntries(
            tasks, instances,
            start.atStartOfDay(zone).toInstant(),
            end.atStartOfDay(zone).toInstant(),
        )
        val byDay = entries.groupBy { it.at.atZone(zone).toLocalDate() }
        CalendarUiState(mode = mode, rangeLabel = "$start – ${end.minusDays(1)}", entriesByDay = byDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun setMode(newMode: CalendarMode) {
        mode.value = newMode
    }

    fun goToday() {
        anchor.value = LocalDate.now(zone)
    }

    fun step(forward: Boolean) {
        val delta = if (forward) 1 else -1
        anchor.value = when (mode.value) {
            CalendarMode.DAY -> anchor.value.plusDays(delta.toLong())
            CalendarMode.WEEK -> anchor.value.plusWeeks(delta.toLong())
            CalendarMode.MONTH -> anchor.value.plusMonths(delta.toLong())
        }
    }

    private fun rangeFor(mode: CalendarMode, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (mode) {
        CalendarMode.DAY -> anchor to anchor.plusDays(1)
        CalendarMode.WEEK -> {
            val start = anchor.minusDays((anchor.dayOfWeek.value % 7).toLong()) // week starts Sunday
            start to start.plusDays(7)
        }
        CalendarMode.MONTH -> {
            val start = anchor.withDayOfMonth(1)
            start to start.plusMonths(1)
        }
    }

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory {
            initializer { CalendarViewModel(repository) }
        }
    }
}
