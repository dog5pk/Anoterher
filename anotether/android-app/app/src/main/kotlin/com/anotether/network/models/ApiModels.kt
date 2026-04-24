package com.anotether.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request bodies ────────────────────────────────────────────────────────────

@Serializable
data class CreateSessionRequest(
    @SerialName("public_key") val publicKey: String,
)

@Serializable
data class JoinSessionRequest(
    @SerialName("token") val token: String,
    @SerialName("public_key") val publicKey: String,
)

@Serializable
data class SendMessageRequest(
    @SerialName("token") val token: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("ciphertext") val ciphertext: String,
    @SerialName("nonce") val nonce: String,
)

@Serializable
data class CloseSessionRequest(
    @SerialName("token") val token: String,
)

// ── Response bodies ───────────────────────────────────────────────────────────

@Serializable
data class CreateSessionResponse(
    @SerialName("token") val token: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("role") val role: String,
)

@Serializable
data class JoinSessionResponse(
    @SerialName("peer_public_key") val peerPublicKey: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("role") val role: String,
)

@Serializable
data class SendMessageResponse(
    @SerialName("seq") val seq: Int,
)

@Serializable
data class WireMessage(
    @SerialName("seq") val seq: Int,
    @SerialName("sender_id") val senderId: String,
    @SerialName("ciphertext") val ciphertext: String,
    @SerialName("nonce") val nonce: String,
    @SerialName("sent_at") val sentAt: Long,
)

@Serializable
data class PollResponse(
    @SerialName("messages") val messages: List<WireMessage>,
    @SerialName("session_expires_at") val sessionExpiresAt: Long,
    @SerialName("peer_joined") val peerJoined: Boolean,
    @SerialName("peer_public_key") val peerPublicKey: String? = null,
    @SerialName("session_status") val sessionStatus: String,
)

@Serializable
data class CloseSessionResponse(
    @SerialName("closed") val closed: Boolean,
)

@Serializable
data class ApiErrorDetail(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
)

@Serializable
data class ApiErrorResponse(
    @SerialName("error") val error: ApiErrorDetail,
)
