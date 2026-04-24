package com.anotether.messaging

/**
 * A decrypted, local message ready for display.
 *
 * These are never persisted to disk. They live in memory for the duration
 * of the active session. When the session ends, messages are gone.
 * This is intentional — ephemeral by design.
 */
data class Message(
    val seq: Int,
    val text: String,
    val direction: MessageDirection,
    val sentAt: Long, // unix timestamp from relay
)

enum class MessageDirection {
    /** Sent by this user */
    Outgoing,
    /** Received from the peer */
    Incoming,
}
