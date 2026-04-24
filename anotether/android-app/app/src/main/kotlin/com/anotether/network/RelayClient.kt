package com.anotether.network

import com.anotether.network.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * HTTP client for the Anotether relay backend.
 *
 * All methods are suspend functions returning [RelayResult].
 * No exceptions propagate to callers — all errors are wrapped.
 *
 * The client is lightweight: no auth headers, no session cookies,
 * no device identifiers. Just plain HTTPS POST/GET with JSON.
 *
 * Change BASE_URL before deploying to production.
 */
class RelayClient(
    private val baseUrl: String = BASE_URL,
) {
    companion object {
        // Replace with your deployed Cloudflare Worker URL
        const val BASE_URL = "https://anotether-relay.YOUR_SUBDOMAIN.workers.dev"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        // No logging plugin — we never want message content in logs
        engine {
            connectTimeout = 10_000
            socketTimeout = 15_000
        }
    }

    // ── Session endpoints ─────────────────────────────────────────────────────

    suspend fun createSession(publicKey: String): RelayResult<CreateSessionResponse> =
        safeCall {
            httpClient.post("$baseUrl/session/create") {
                contentType(ContentType.Application.Json)
                setBody(CreateSessionRequest(publicKey = publicKey))
            }.body()
        }

    suspend fun joinSession(
        token: String,
        publicKey: String,
    ): RelayResult<JoinSessionResponse> =
        safeCall {
            httpClient.post("$baseUrl/session/join") {
                contentType(ContentType.Application.Json)
                setBody(JoinSessionRequest(token = token, publicKey = publicKey))
            }.body()
        }

    suspend fun closeSession(token: String): RelayResult<CloseSessionResponse> =
        safeCall {
            httpClient.post("$baseUrl/session/close") {
                contentType(ContentType.Application.Json)
                setBody(CloseSessionRequest(token = token))
            }.body()
        }

    // ── Message endpoints ─────────────────────────────────────────────────────

    suspend fun sendMessage(
        token: String,
        senderId: String,
        ciphertext: String,
        nonce: String,
    ): RelayResult<SendMessageResponse> =
        safeCall {
            httpClient.post("$baseUrl/message/send") {
                contentType(ContentType.Application.Json)
                setBody(
                    SendMessageRequest(
                        token = token,
                        senderId = senderId,
                        ciphertext = ciphertext,
                        nonce = nonce,
                    )
                )
            }.body()
        }

    suspend fun pollMessages(
        token: String,
        after: Int,
    ): RelayResult<PollResponse> =
        safeCall {
            httpClient.get("$baseUrl/message/poll") {
                parameter("token", token)
                parameter("after", after)
            }.body()
        }

    // ── Error handling ────────────────────────────────────────────────────────

    /**
     * Wrap any API call in uniform error handling.
     * Maps HTTP errors and network failures to [RelayResult.Failure] subtypes.
     */
    private suspend fun <T> safeCall(block: suspend () -> T): RelayResult<T> {
        return try {
            RelayResult.Success(block())
        } catch (e: ResponseException) {
            // HTTP error response from the relay
            try {
                val errorBody: ApiErrorResponse = e.response.body()
                RelayResult.Failure.ApiError(
                    httpStatus = e.response.status.value,
                    code = errorBody.error.code,
                    message = errorBody.error.message,
                )
            } catch (_: Exception) {
                // Could not parse error body — use HTTP status
                RelayResult.Failure.ApiError(
                    httpStatus = e.response.status.value,
                    code = "http_error",
                    message = "HTTP ${e.response.status.value}",
                )
            }
        } catch (e: IOException) {
            RelayResult.Failure.NetworkError(e)
        } catch (e: Exception) {
            RelayResult.Failure.ParseError(e)
        }
    }

    fun close() {
        httpClient.close()
    }
}
