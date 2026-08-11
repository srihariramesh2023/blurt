package com.blurt.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.model.CaptureType
import com.blurt.app.util.isHttpUrl
import com.blurt.app.util.normalizedHttpUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CaptureViewModel(
    private val repository: CaptureRepository,
    private val authState: StateFlow<AuthState>,
    initialType: CaptureType,
) : ViewModel() {

    private val _type = MutableStateFlow(initialType)
    val type: StateFlow<CaptureType> = _type.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set to the new capture id once saved; the screen observes it to pop back. */
    private val _saved = MutableStateFlow<Long?>(null)
    val saved: StateFlow<Long?> = _saved.asStateFlow()

    fun onTypeSelected(type: CaptureType) {
        _type.value = type
        _error.value = null
    }

    fun onContentChange(text: String) {
        _content.value = text
        _error.value = null
    }

    fun save() {
        val type = _type.value
        val content = if (type == CaptureType.LINK) _content.value.normalizedHttpUrl() else _content.value.trim()

        when (type) {
            CaptureType.TEXT, CaptureType.IDEA ->
                if (content.isBlank()) {
                    _error.value = "Write something first."
                    return
                }

            CaptureType.LINK ->
                if (!content.isHttpUrl()) {
                    _error.value = "That doesn't look like a valid link."
                    return
                }
        }

        viewModelScope.launch {
            val uid = (authState.value as? AuthState.SignedIn)?.user?.uid
            if (uid == null) {
                _error.value = "You need to be signed in to save."
                return@launch
            }
            try {
                val id = repository.create(uid, type, content)
                _saved.value = id
            } catch (e: Exception) {
                _error.value = "Couldn't save. Try again."
            }
        }
    }

    fun onSavedHandled() {
        _saved.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                val type = CaptureType.fromRoute(createSavedStateHandle()["type"])
                CaptureViewModel(
                    repository = app.container.captureRepository,
                    authState = app.container.authRepository.authState,
                    initialType = type,
                )
            }
        }
    }
}
