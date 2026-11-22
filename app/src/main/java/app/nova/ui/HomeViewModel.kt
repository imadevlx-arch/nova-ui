package app.nova.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nova.ui.data.AppRepository
import app.nova.ui.data.TvApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val favorites: List<TvApp> = emptyList(),
    val others: List<TvApp> = emptyList(),
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val repository = AppRepository(app)

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var job: Job? = null

    init { refresh() }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val apps = runCatching { repository.loadApps() }.getOrDefault(emptyList())
            // V0 : les 6 premières apps en favoris. L'épinglage manuel arrive ensuite.
            _state.value = HomeState(
                loading = false,
                favorites = apps.take(FAVORITES),
                others = apps.drop(FAVORITES),
            )
        }
    }

    companion object { const val FAVORITES = 6 }
}
