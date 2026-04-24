package com.anotether.network

/**
 * Typed result wrapper for all relay API calls.
 *
 * We avoid throwing exceptions across the network boundary.
 * Every API call returns a RelayResult — callers handle errors explicitly.
 *
 * This makes error handling at the UI layer clear and exhaustive.
 */
sealed class RelayResult<out T> {
    data class Success<T>(val data: T) : RelayResult<T>()

    sealed class Failure : RelayResult<Nothing>() {
        /** HTTP error from the relay with a machine-readable code */
        data class ApiError(val httpStatus: Int, val code: String, val message: String) : Failure()
        /** Network connectivity problem */
        data class NetworkError(val cause: Throwable) : Failure()
        /** Unexpected response parsing failure */
        data class ParseError(val cause: Throwable) : Failure()
    }
}

/** Convenience: true if success */
val <T> RelayResult<T>.isSuccess get() = this is RelayResult.Success

/** Convenience: extract data or null */
fun <T> RelayResult<T>.dataOrNull(): T? = (this as? RelayResult.Success)?.data

/** Whether this is a "session not found / expired" error */
fun RelayResult<*>.isSessionGone(): Boolean {
    val failure = this as? RelayResult.Failure.ApiError ?: return false
    return failure.httpStatus == 404 || failure.code == "session_expired"
}
