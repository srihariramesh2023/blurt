package com.blurt.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.ai.AiKeyStore
import com.blurt.app.ai.GeminiKeyStatus
import com.blurt.app.ai.GeminiKeyValidator
import com.blurt.app.ai.GroqKeyStatus
import com.blurt.app.ai.GroqKeyValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the BYOK dialog is showing about the last Save & Check attempt. */
sealed interface AiKeyStatus {
    data object Idle : AiKeyStatus
    data object Checking : AiKeyStatus

    /** Groq accepted the key and it was persisted. */
    data object Valid : AiKeyStatus

    /** Groq rejected the key — not saved. */
    data object Invalid : AiKeyStatus

    /** Couldn't reach Groq, but the key was saved anyway (may be fine). */
    data object Unreachable : AiKeyStatus
}

/**
 * Backs the BYOK dialog in the avatar menu. The draft key and the validation
 * status live here so they survive the menu closing; the actual key material
 * is persisted only through [AiKeyStore] (Android Keystore-encrypted).
 */
class AiKeyViewModel(
    private val keyStore: AiKeyStore,
    private val groqValidator: GroqKeyValidator = GroqKeyValidator(),
    private val geminiValidator: GeminiKeyValidator = GeminiKeyValidator(),
) : ViewModel() {

    // --- Groq (capture classification) ---------------------------------------

    private val _draftKey = MutableStateFlow("")
    val draftKey: StateFlow<String> = _draftKey.asStateFlow()

    private val _status = MutableStateFlow<AiKeyStatus>(AiKeyStatus.Idle)
    val status: StateFlow<AiKeyStatus> = _status.asStateFlow()

    /** The tail of the currently saved user key (never the full key). */
    fun savedKeyTail(): String? = keyStore.groqKey()?.takeLast(4)

    fun onDraftChange(value: String) {
        _draftKey.value = value
        _status.value = AiKeyStatus.Idle
    }

    /** Probes the key with Groq; saves it only when it's accepted (or unreachable). */
    fun saveAndCheck() {
        val key = _draftKey.value.trim()
        if (key.isBlank()) return
        viewModelScope.launch {
            _status.value = AiKeyStatus.Checking
            when (groqValidator.validate(key)) {
                GroqKeyStatus.VALID -> {
                    keyStore.saveGroqKey(key)
                    _draftKey.value = ""
                    _status.value = AiKeyStatus.Valid
                }
                GroqKeyStatus.INVALID -> _status.value = AiKeyStatus.Invalid
                GroqKeyStatus.UNREACHABLE -> {
                    keyStore.saveGroqKey(key)
                    _draftKey.value = ""
                    _status.value = AiKeyStatus.Unreachable
                }
            }
        }
    }

    fun removeKey() {
        keyStore.clearGroqKey()
        _draftKey.value = ""
        _status.value = AiKeyStatus.Idle
    }

    // --- Gemini (classification fallback + semantic search) ------------------

    private val _geminiDraftKey = MutableStateFlow("")
    val geminiDraftKey: StateFlow<String> = _geminiDraftKey.asStateFlow()

    private val _geminiStatus = MutableStateFlow<AiKeyStatus>(AiKeyStatus.Idle)
    val geminiStatus: StateFlow<AiKeyStatus> = _geminiStatus.asStateFlow()

    /** The tail of the currently saved user Gemini key (never the full key). */
    fun savedGeminiKeyTail(): String? = keyStore.geminiKey()?.takeLast(4)

    fun onGeminiDraftChange(value: String) {
        _geminiDraftKey.value = value
        _geminiStatus.value = AiKeyStatus.Idle
    }

    /** Probes the key with Gemini; saves it only when accepted (or unreachable). */
    fun saveAndCheckGemini() {
        val key = _geminiDraftKey.value.trim()
        if (key.isBlank()) return
        viewModelScope.launch {
            _geminiStatus.value = AiKeyStatus.Checking
            when (geminiValidator.validate(key)) {
                GeminiKeyStatus.VALID -> {
                    keyStore.saveGeminiKey(key)
                    _geminiDraftKey.value = ""
                    _geminiStatus.value = AiKeyStatus.Valid
                }
                GeminiKeyStatus.INVALID -> _geminiStatus.value = AiKeyStatus.Invalid
                GeminiKeyStatus.UNREACHABLE -> {
                    keyStore.saveGeminiKey(key)
                    _geminiDraftKey.value = ""
                    _geminiStatus.value = AiKeyStatus.Unreachable
                }
            }
        }
    }

    fun removeGeminiKey() {
        keyStore.clearGeminiKey()
        _geminiDraftKey.value = ""
        _geminiStatus.value = AiKeyStatus.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                AiKeyViewModel(
                    keyStore = app.container.aiKeyStore,
                    groqValidator = GroqKeyValidator(),
                    geminiValidator = GeminiKeyValidator(
                        packageName = app.packageName,
                        certSha1 = app.container.signingCertSha1(app),
                    ),
                )
            }
        }
    }
}
