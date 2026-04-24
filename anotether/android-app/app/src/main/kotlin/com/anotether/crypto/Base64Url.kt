package com.anotether.crypto

import android.util.Base64

/**
 * Base64url encoding/decoding utilities.
 *
 * We use base64url (RFC 4648 §5) — URL-safe variant with - and _ instead of + and /.
 * No padding by default (standard practice for base64url in modern protocols).
 *
 * All crypto wire values (public keys, ciphertext, nonces) use this encoding.
 */
object Base64Url {

    /** Encode bytes to base64url string without padding. */
    fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** Decode a base64url string (with or without padding) to bytes. */
    fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP)
}
