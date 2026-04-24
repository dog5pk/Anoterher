package com.anotether.session

import com.anotether.crypto.EphemeralKeyPair

/**
 * The complete state machine for a session.
 *
 * Transitions:
 *   Idle
 *     → Creating (user taps "Create Session")
 *     → Joining  (user taps "Join Session")
 *   Creating → WaitingForPeer (relay returns token)
 *   WaitingForPeer → Active (peer joined, keys exchanged)
 *   Joining → Active (joined successfully, keys derived)
 *   Active → Closed (either party ends session)
 *   Active → Expired (TTL elapsed)
 *   Any → Error (unrecoverable failure)
 *
 * Invalid transitions are not represented — the state machine enforces them
 * by only allowing specific transitions in SessionManager.
 */
sealed class SessionState {

    /** No session in progress */
    object Idle : SessionState()

    /** Creating a new session on the relay */
    object Creating : SessionState()

    /** Session created; waiting for the other person to join */
    data class WaitingForPeer(
        val token: String,
        val keyPair: EphemeralKeyPair,
        val expiresAt: Long, // unix timestamp
    ) : SessionState()

    /** In the process of joining a session */
    object Joining : SessionState()

    /** Both parties present; messaging is live */
    data class Active(
        val token: String,
        val sessionKey: ByteArray,
        val role: Role,        // "a" (initiator) or "b" (joiner)
        val expiresAt: Long,
        val lastSeq: Int = 0,
    ) : SessionState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Active) return false
            return token == other.token &&
                sessionKey.contentEquals(other.sessionKey) &&
                role == other.role &&
                expiresAt == other.expiresAt &&
                lastSeq == other.lastSeq
        }

        override fun hashCode(): Int {
            var result = token.hashCode()
            result = 31 * result + sessionKey.contentHashCode()
            result = 31 * result + role.hashCode()
            result = 31 * result + expiresAt.hashCode()
            result = 31 * result + lastSeq
            return result
        }
    }

    /** Session has been closed (by either party) */
    object Closed : SessionState()

    /** Session TTL elapsed */
    object Expired : SessionState()

    /** Unrecoverable error */
    data class Error(val message: String) : SessionState()
}

enum class Role(val senderId: String) {
    Initiator("a"),
    Joiner("b"),
}
