package com.anotether.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anotether.messaging.Message
import com.anotether.network.RelayClient
import com.anotether.session.SessionManager
import com.anotether.session.SessionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * MainViewModel bridges [SessionManager] to the Compose UI.
 *
 * The UI observes [uiState] and calls action methods.
 * No business logic lives here — this is a thin adapter.
 */
class MainViewModel : ViewModel() {

    private val sessionManager = SessionManager(RelayClient())

    // Session state forwarded from SessionManager
    val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    // All messages for the current session (accumulated in memory)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // Error for the join screen
    private val _joinError = MutableStateFlow<String?>(null)
    val joinError: StateFlow<String?> = _joinError.asStateFlow()

    // Loading flag for join action
    private val _isJoining = MutableStateFlow(false)
    val isJoining: StateFlow<Boolean> = _isJoining.asStateFlow()

    init {
        // Collect incoming messages from the session manager
        viewModelScope.launch {
            sessionManager.incomingMessages.collect { message ->
                _messages.update { current ->
                    // Deduplicate by seq — we might receive the same message twice during polling
                    if (current.any { it.seq == message.seq }) current
                    else (current + message).sortedBy { it.seq }
                }
            }
        }

        // Clear messages when session resets
        viewModelScope.launch {
            sessionManager.sessionState.collect { state ->
                if (state is SessionState.Idle || state is SessionState.Creating) {
                    _messages.value = emptyList()
                    _joinError.value = null
                }
            }
        }
    }

    fun createSession() {
        viewModelScope.launch {
            sessionManager.createSession()
        }
    }

    fun joinSession(token: String) {
        _joinError.value = null
        _isJoining.value = true
        viewModelScope.launch {
            try {
                val result = sessionManager.joinSession(token)
                if (result is SessionState.Error) {
                    _joinError.value = friendlyError(result.message)
                }
            } finally {
                _isJoining.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            sessionManager.sendMessage(text)
        }
    }

    fun endSession() {
        viewModelScope.launch {
            sessionManager.closeSession()
        }
    }

    fun resetToIdle() {
        viewModelScope.launch {
            if (sessionState.value !is SessionState.Active &&
                sessionState.value !is SessionState.WaitingForPeer
            ) {
                sessionManager.closeSession()
            }
        }
    }

    private fun friendlyError(raw: String): String = when {
        raw.contains("session_not_found") || raw.contains("session_expired") ->
            "Session not found or expired. Check the code and try again."
        raw.contains("session_full") ->
            "This session already has two participants."
        raw.contains("invalid_token") ->
            "Invalid session code. Codes are 6 characters."
        raw.contains("rate_limited") ->
            "Too many attempts. Please wait a moment."
        raw.contains("network") || raw.contains("connect") ->
            "Connection failed. Check your network."
        else -> "Could not join session. Please try again."
    }

    override fun onCleared() {
        super.onCleared()
        sessionManager.destroy()
    }
}
