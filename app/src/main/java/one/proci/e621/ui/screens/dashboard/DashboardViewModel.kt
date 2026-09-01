package one.proci.e621.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.proci.e621.data.settings.UsageStats
import one.proci.e621.data.settings.UsageStatsStore

data class DashboardUiState(
    val stats: UsageStats = UsageStats(),
    val enabled: Boolean = true,
)

class DashboardViewModel(private val store: UsageStatsStore) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(store.statsFlow, store.enabledFlow) { stats, enabled ->
        DashboardUiState(stats, enabled)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { store.setEnabled(enabled) }

    fun clear() = viewModelScope.launch { store.clear() }
}
