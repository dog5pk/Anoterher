package com.anotether.session

import com.anotether.crypto.AnotetherCrypto
import com.anotether.crypto.Base64Url
import com.anotether.messaging.Message
import com.anotether.messaging.MessageDirection
import com.anotether.network.RelayClient
import com.anotether.network.RelayResult
import com.anotether.network.isSessionGone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * SessionManager orchestrates the full session lifecycle.
 *
 * Responsibilities:
 * - Create and join sessions (key generation, relay API calls)
 * - Poll for new messages and peer join events
 * - Manage the state machine via [SessionState] flow
 * - Emit incoming decrypted messages via [incomingMessages] flow
 *
 * This class is the single source of truth for session state.
 * The UI observes [sessionState] and [incomingMessages] flows.
 *
 * Threading: all public methods are safe to call from the main thread.
 * Suspend functions run on [Dispatchers.IO] internally.
 */
class SessionManager(
    private val relay: RelayClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Message> = _incomingMessages.asSharedFlow()

    private var pollJob: Job? = null

    // ── Create session ────────────────────────────────────────────────────────

    /**
     * Initiate a new session. Generates an ephemeral key pair and registers
     * with the relay. On success, transitions to [SessionState.WaitingForPeer].
     */
    suspend fun createSession(): SessionState {
        _sessionState.value = SessionState.Creating

        val keyPair = withContext(Dispatchers.Default) {
            AnotetherCrypto.generateKeyPair()
        }
        val publicKeyB64 = Base64Url.encode(keyPair.publicKey)

        return when (val result = relay.createSession(publicKeyB64)) {
            is RelayResult.Success -> {
                val state = SessionState.WaitingForPeer(
                    token = result.data.token,
                    keyPair = keyPair,
                    expiresAt = result.data.expiresAt,
                )
                _sessionState.value = state
                startPolling()
                state
            }
            is RelayResult.Failure -> {
                keyPair.destroy()
                val error = SessionState.Error(failureMessage(result))
                _sessionState.value = error
                error
            }
        }
    }

    // ── Join session ──────────────────────────────────────────────────────────

    /**
     * Join an existing session by token. Generates a key pair, sends it to
     * the relay, receives the initiator's public key, and derives the session key.
     * On success, transitions directly to [SessionState.Active].
     */
    suspend fun joinSession(token: String): SessionState {
        _sessionState.value = SessionState.Joining

        val keyPair = withContext(Dispatchers.Default) {
            AnotetherCrypto.generateKeyPair()
        }
        val publicKeyB64 = Base64Url.encode(keyPair.publicKey)
        val normalizedToken = token.trim().uppercase()

        return when (val result = relay.joinSession(normalizedToken, publicKeyB64)) {
            is RelayResult.Success -> {
                // try/finally guarantees keyPair.destroy() runs even if decode or
                // deriveSessionKey throws. Without this, a malformed peer key from
                // the relay would leak our private key in memory indefinitely.
                try {
                    val peerPublicKey = Base64Url.decode(result.data.peerPublicKey)
                    val sessionKey = withContext(Dispatchers.Default) {
                        AnotetherCrypto.deriveSessionKey(
                            ourPrivateKey = keyPair.privateKey,
                            theirPublicKey = peerPublicKey,
                            sessionToken = normalizedToken,
                        )
                    }

                    // The relay stores our public key from /session/join and returns it to
                    // the initiator via peer_public_key in the poll response. No sentinel
                    // message needed — key exchange is handled entirely at the relay layer.

                    val state = SessionState.Active(
                        token = normalizedToken,
                        sessionKey = sessionKey,
                        role = Role.Joiner,
                        expiresAt = result.data.expiresAt,
                    )
                    _sessionState.value = state
                    startPolling()
                    state
                } catch (e: Exception) {
                    val error = SessionState.Error("Key derivation failed: ${e.message}")
                    _sessionState.value = error
                    error
                } finally {
                    keyPair.destroy() // always runs — success, decode failure, or derive failure
                }
            }
            is RelayResult.Failure -> {
                keyPair.destroy()
                val error = SessionState.Error(failureMessage(result))
                _sessionState.value = error
                error
            }
        }
    }

    // ── Close session ─────────────────────────────────────────────────────────

    /**
     * Close the current session. Notifies the relay, stops polling,
     * and transitions to [SessionState.Closed].
     */
    suspend fun closeSession() {
        val current = _sessionState.value
        val token = when (current) {
            is SessionState.Active -> current.token
            is SessionState.WaitingForPeer -> current.token
            else -> null
        }

        stopPolling()

        if (token != null) {
            relay.closeSession(token) // best-effort; we transition regardless
        }

        // Zero key material BEFORE overwriting _sessionState — clearKeyMaterial()
        // reads the current state to find the key. Once state is Closed, it can't.
        clearKeyMaterial()
        _sessionState.value = SessionState.Closed
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun poll() {
        val state = _sessionState.value

        when (state) {
            is SessionState.WaitingForPeer -> pollForPeer(state)
            is SessionState.Active -> pollForMessages(state)
            else -> stopPolling()
        }
    }

    /**
     * Poll until the relay reports the peer has joined and returns their public key.
     * When both conditions are true, derives the shared session key and transitions
     * to [SessionState.Active].
     *
     * The joiner's public key comes directly from the poll response field
     * [PollResponse.peerPublicKey] — no sentinel messages, no out-of-band tricks.
     * The relay stores it during /session/join and surfaces it here.
     */
    private suspend fun pollForPeer(state: SessionState.WaitingForPeer) {
        val result = relay.pollMessages(state.token, after = 0)

        when (result) {
            is RelayResult.Success -> {
                val response = result.data

                if (response.sessionStatus == "expired") {
                    stopPolling()
                    state.keyPair.destroy()
                    _sessionState.value = SessionState.Expired
                    return
                }

                // Wait until peer_joined is true AND peer_public_key is present.
                // Both arrive together once the joiner hits /session/join.
                val peerPublicKeyB64 = response.peerPublicKey
                if (!response.peerJoined || peerPublicKeyB64 == null) return

                val peerPublicKey = try {
                    Base64Url.decode(peerPublicKeyB64)
                } catch (_: Exception) {
                    // Malformed key in relay response — shouldn't happen, but don't crash
                    return
                }

                if (peerPublicKey.size != 32) return

                val sessionKey = try {
                    withContext(Dispatchers.Default) {
                        AnotetherCrypto.deriveSessionKey(
                            ourPrivateKey = state.keyPair.privateKey,
                            theirPublicKey = peerPublicKey,
                            sessionToken = state.token,
                        )
                    }
                } catch (_: Exception) {
                    // A weird relay response (wrong key size, bad encoding) must not kill
                    // the polling coroutine. Destroy the keypair, surface an error state,
                    // and stop — the session is unrecoverable at this point anyway.
                    state.keyPair.destroy()
                    stopPolling()
                    _sessionState.value = SessionState.Error("Key derivation failed")
                    return
                }
                state.keyPair.destroy() // Private key no longer needed

                _sessionState.value = SessionState.Active(
                    token = state.token,
                    sessionKey = sessionKey,
                    role = Role.Initiator,
                    expiresAt = state.expiresAt,
                    lastSeq = 0,
                )
            }
            is RelayResult.Failure -> {
                if (result.isSessionGone()) {
                    stopPolling()
                    state.keyPair.destroy()
                    _sessionState.value = SessionState.Expired
                }
                // Network errors: silently retry next poll cycle
            }
        }
    }

    /**
     * Poll for new encrypted messages and emit them decrypted.
     */
    private suspend fun pollForMessages(state: SessionState.Active) {
        val result = relay.pollMessages(state.token, after = state.lastSeq)

        when (result) {
            is RelayResult.Success -> {
                val response = result.data

                if (response.sessionStatus == "closed" || response.sessionStatus == "expired") {
                    stopPolling()
                    // Zero key material before state transition — same ordering
                    // discipline as closeSession(). clearKeyMaterial() needs Active state
                    // to be present, so it must run before we overwrite _sessionState.
                    clearKeyMaterial()
                    _sessionState.value = if (response.sessionStatus == "expired") {
                        SessionState.Expired
                    } else {
                        SessionState.Closed
                    }
                    return
                }

                var newLastSeq = state.lastSeq
                for (wireMsg in response.messages) {
                    val decrypted = tryDecrypt(state.sessionKey, wireMsg)
                    if (decrypted != null) {
                        val direction = if (wireMsg.senderId == state.role.senderId) {
                            MessageDirection.Outgoing
                        } else {
                            MessageDirection.Incoming
                        }
                        _incomingMessages.emit(
                            Message(
                                seq = wireMsg.seq,
                                text = decrypted,
                                direction = direction,
                                sentAt = wireMsg.sentAt,
                            )
                        )
                    }
                    if (wireMsg.seq > newLastSeq) newLastSeq = wireMsg.seq
                }

                if (newLastSeq != state.lastSeq) {
                    _sessionState.value = state.copy(lastSeq = newLastSeq)
                }
            }
            is RelayResult.Failure -> {
                if (result.isSessionGone()) {
                    stopPolling()
                    clearKeyMaterial()
                    _sessionState.value = SessionState.Expired
                }
            }
        }
    }

    private fun tryDecrypt(
        sessionKey: ByteArray,
        wireMsg: com.anotether.network.models.WireMessage,
    ): String? {
        return try {
            val ciphertext = Base64Url.decode(wireMsg.ciphertext)
            val nonce = Base64Url.decode(wireMsg.nonce)
            val decrypted = AnotetherCrypto.decrypt(
                sessionKey,
                com.anotether.crypto.EncryptedMessage(ciphertext = ciphertext, nonce = nonce),
            )
            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            // Decryption failure: tampered message or wrong key.
            // We silently drop rather than crashing — in a real session this shouldn't happen.
            null
        }
    }

    // ── Send message ──────────────────────────────────────────────────────────

    /**
     * Encrypt and send a message in the current active session.
     * Returns false if not in an active session or if send fails.
     */
    suspend fun sendMessage(text: String): Boolean {
        val state = _sessionState.value as? SessionState.Active ?: return false

        val encrypted = withContext(Dispatchers.Default) {
            AnotetherCrypto.encrypt(state.sessionKey, text.toByteArray(Charsets.UTF_8))
        }

        val result = relay.sendMessage(
            token = state.token,
            senderId = state.role.senderId,
            ciphertext = Base64Url.encode(encrypted.ciphertext),
            nonce = Base64Url.encode(encrypted.nonce),
        )

        return result is RelayResult.Success
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun clearKeyMaterial() {
        // Session keys in Active state will be GC'd. Kotlin doesn't have explicit
        // memory zeroing for managed objects, but we do destroy EphemeralKeyPair
        // private keys explicitly where possible (they hold raw byte arrays).
        // The session key ByteArray is zeroed here if we can get to it.
        val state = _sessionState.value
        if (state is SessionState.Active) {
            state.sessionKey.fill(0)
        }
    }

    fun destroy() {
        clearKeyMaterial()
        stopPolling()
        scope.cancel()
        relay.close()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }

    private fun failureMessage(failure: RelayResult.Failure): String = when (failure) {
        is RelayResult.Failure.ApiError -> "${failure.code}: ${failure.message}"
        is RelayResult.Failure.NetworkError -> "network_error: ${failure.cause.message}"
        is RelayResult.Failure.ParseError -> "parse_error: ${failure.cause.message}"
    }
}
