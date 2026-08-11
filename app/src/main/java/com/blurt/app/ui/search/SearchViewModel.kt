package com.blurt.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.model.Capture
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Phase 1 search is plain local keyword search: case-insensitive, across
 * capture content and links, updating live as the query changes.
 * (Semantic/vector search arrives with a backend in a later phase.)
 * Results are scoped to the signed-in user.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    repository: CaptureRepository,
    authState: StateFlow<AuthState>,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<Capture>> = combine(
        _query.debounce(250).distinctUntilChanged(),
        authState,
    ) { query, state -> query to (state as? AuthState.SignedIn)?.user?.uid }
        .flatMapLatest { (query, uid) ->
            when {
                uid == null -> flowOf(emptyList())
                query.isBlank() -> flowOf(emptyList())
                else -> repository.search(query, uid)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                SearchViewModel(
                    repository = app.container.captureRepository,
                    authState = app.container.authRepository.authState,
                )
            }
        }
    }
}
