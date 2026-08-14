package com.blurt.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.ai.SemanticSearchEngine
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.local.toDomain
import com.blurt.app.data.model.Capture
import com.blurt.app.notifications.ReminderScheduler
import com.blurt.app.util.escapeLikePattern
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Search with a meaning layer: semantic (Gemini vector) search first, plain
 * keyword search as the fallback whenever semantic is unavailable — no key
 * configured, offline, quota hit. The fallback keeps search alive in every
 * case, and both paths stay scoped to the signed-in user.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val repository: CaptureRepository,
    private val reminderScheduler: ReminderScheduler?,
    private val authState: StateFlow<AuthState>,
    private val semanticSearch: SemanticSearchEngine?,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** True when the latest search ran through semantic embeddings. */
    private val _semanticUsed = MutableStateFlow(false)
    val semanticUsed: StateFlow<Boolean> = _semanticUsed.asStateFlow()

    val results: StateFlow<List<Capture>> = combine(
        _query.debounce(250).distinctUntilChanged(),
        authState,
    ) { query, state -> query to (state as? AuthState.SignedIn)?.user?.uid }
        .flatMapLatest { (query, uid) ->
            when {
                uid == null -> flowOf(emptyList())
                query.isBlank() -> flowOf(emptyList())
                else -> flow {
                    val semantic = semanticSearch?.let {
                        withTimeoutOrNull(SEARCH_TIMEOUT_MS) { it.search(query, uid) }
                    }
                    _semanticUsed.value = semantic != null
                    emit(
                        if (semantic != null) semantic.map { it.toDomain() }
                        else repository.searchOnce(query.escapeLikePattern(), uid)
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Tombstones the capture; the sync engine removes it from the backend. */
    fun delete(id: Long) {
        viewModelScope.launch {
            val uid = (authState.value as? AuthState.SignedIn)?.user?.uid ?: return@launch
            reminderScheduler?.cancel(id)
            repository.delete(id, uid)
        }
    }

    /** Moves a blurt to Library → Archived, out of the main lists. */
    fun archive(id: Long) {
        viewModelScope.launch {
            val uid = (authState.value as? AuthState.SignedIn)?.user?.uid ?: return@launch
            repository.setArchived(id, uid, true)
        }
    }

    companion object {
        private const val SEARCH_TIMEOUT_MS = 8_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                SearchViewModel(
                    repository = app.container.captureRepository,
                    reminderScheduler = app.container.reminderScheduler,
                    authState = app.container.authRepository.authState,
                    semanticSearch = app.container.semanticSearch,
                )
            }
        }
    }
}
