package com.anotether.crypto

import com.google.crypto.tink.subtle.ChaCha20Poly1305
import com.google.crypto.tink.subtle.X25519
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Anotether cryptographic primitives.
 *
 * Design:
 * - X25519 ECDH for key exchange (Curve25519, constant-time, no patent issues)
 * - HKDF-SHA256 to derive a session key from the shared secret
 * - ChaCha20-Poly1305 AEAD for message encryption (via Google Tink)
 * - SecureRandom for nonce generation (12 bytes, fresh per message)
 *
 * Why Tink over raw JCE/Bouncy Castle:
 * - Tink prevents common misuse (nonce reuse detection, key commitment)
 * - Audited and maintained by Google security team
 * - Wraps Curve25519 and ChaCha20 with safe defaults
 * - No need to bundle a native .so library
 *
 * This class is stateless — all methods are pure functions on byte arrays.
 * Key material is never stored here; callers are responsible for lifecycle.
 */
object AnotetherCrypto {

    private val secureRandom = SecureRandom()

    // ── Key generation ────────────────────────────────────────────────────────

    /**
     * Generate an ephemeral X25519 key pair.
     * Call once per session. Private key never leaves the device.
     */
    fun generateKeyPair(): EphemeralKeyPair {
        val privateKey = X25519.generatePrivateKey()
        val publicKey = X25519.publicFromPrivate(privateKey)
        return EphemeralKeyPair(privateKey = privateKey, publicKey = publicKey)
    }

    // ── Key exchange ─────────────────────────────────────────────────────────

    /**
     * Derive a shared session key from our private key and the peer's public key.
     *
     * Process:
     * 1. X25519 ECDH → 32-byte shared secret
     * 2. HKDF-SHA256 with the session token as salt → 32-byte session key
     *
     * The session token as salt ensures that two parties using the same key pair
     * in different sessions derive different keys. (Ephemeral keys mean this
     * won't happen in practice, but defense in depth is cheap here.)
     *
     * @param ourPrivateKey  Our ephemeral X25519 private key (32 bytes)
     * @param theirPublicKey Peer's X25519 public key (32 bytes)
     * @param sessionToken   6-character session token (used as HKDF salt)
     */
    fun deriveSessionKey(
        ourPrivateKey: ByteArray,
        theirPublicKey: ByteArray,
        sessionToken: String,
    ): ByteArray {
        require(ourPrivateKey.size == 32) { "X25519 private key must be 32 bytes" }
        require(theirPublicKey.size == 32) { "X25519 public key must be 32 bytes" }

        val sharedSecret = X25519.computeSharedSecret(ourPrivateKey, theirPublicKey)
        return hkdfSha256(
            inputKeyMaterial = sharedSecret,
            salt = sessionToken.toByteArray(Charsets.UTF_8),
            info = "anotether-v1".toByteArray(Charsets.UTF_8),
            outputLength = 32,
        )
    }

    // ── Encryption ───────────────────────────────────────────────────────────

    /**
     * Encrypt a plaintext message with ChaCha20-Poly1305.
     *
     * Returns an [EncryptedMessage] containing the ciphertext (with 16-byte auth tag
     * appended by Tink) and a fresh 12-byte nonce.
     *
     * The nonce is randomly generated per call. Never reuse nonces with the same key.
     * We generate fresh nonces here rather than using counters to avoid any statefulness.
     * SecureRandom on modern Android is CSPRNG-backed.
     *
     * @param sessionKey 32-byte derived session key
     * @param plaintext  Plaintext bytes to encrypt
     */
    fun encrypt(sessionKey: ByteArray, plaintext: ByteArray): EncryptedMessage {
        require(sessionKey.size == 32) { "Session key must be 32 bytes" }

        val nonce = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = ChaCha20Poly1305(sessionKey)
        // Tink's ChaCha20Poly1305.seal(nonce, plaintext, associatedData)
        // Output = ciphertext + 16-byte Poly1305 tag
        val ciphertext = cipher.seal(nonce, plaintext, ByteArray(0))

        return EncryptedMessage(ciphertext = ciphertext, nonce = nonce)
    }

    /**
     * Decrypt a ciphertext with ChaCha20-Poly1305.
     *
     * Throws [javax.crypto.AEADBadTagException] if authentication fails —
     * i.e., the message was tampered with, or the wrong key is being used.
     * Callers must handle this exception and surface an appropriate error.
     *
     * @param sessionKey 32-byte derived session key
     * @param encrypted  The [EncryptedMessage] to decrypt
     */
    fun decrypt(sessionKey: ByteArray, encrypted: EncryptedMessage): ByteArray {
        require(sessionKey.size == 32) { "Session key must be 32 bytes" }
        require(encrypted.nonce.size == 12) { "Nonce must be 12 bytes" }

        val cipher = ChaCha20Poly1305(sessionKey)
        // open() throws GeneralSecurityException if tag verification fails
        return cipher.open(encrypted.nonce, encrypted.ciphertext, ByteArray(0))
    }

    // ── HKDF ─────────────────────────────────────────────────────────────────

    /**
     * HKDF-SHA256 key derivation function (RFC 5869).
     *
     * Two-step process:
     * 1. Extract: HMAC-SHA256(salt, inputKeyMaterial) → pseudorandom key
     * 2. Expand:  HMAC-SHA256(prk, info || counter) → outputLength bytes
     *
     * We implement this directly rather than pulling in a dependency.
     * The implementation is straightforward and testable.
     */
    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        // Extract
        val prk = hmacSha256(key = salt, data = inputKeyMaterial)

        // Expand (single block sufficient for 32 bytes output; SHA256 block = 32 bytes)
        require(outputLength <= 32 * 255) { "HKDF output length too large" }
        val output = ByteArray(outputLength)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < outputLength) {
            t = hmacSha256(key = prk, data = t + info + byteArrayOf(counter.toByte()))
            val toCopy = minOf(t.size, outputLength - offset)
            t.copyInto(output, offset, 0, toCopy)
            offset += toCopy
            counter++
        }

        return output
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

/**
 * An ephemeral X25519 key pair for one session.
 * Both keys are 32 bytes (Curve25519).
 *
 * Security: privateKey must never be serialized or logged.
 * It is held in memory only for the duration of the session.
 */
data class EphemeralKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EphemeralKeyPair) return false
        return privateKey.contentEquals(other.privateKey) &&
            publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = publicKey.contentHashCode()

    /** Zero out the private key when done. Call when session ends. */
    fun destroy() {
        privateKey.fill(0)
    }
}

/**
 * An encrypted message payload — ciphertext with its nonce.
 * Both fields are raw bytes; encoding to base64url for transport is in the network layer.
 */
data class EncryptedMessage(
    val ciphertext: ByteArray,  // ChaCha20-Poly1305 output: encrypted bytes + 16-byte tag
    val nonce: ByteArray,       // 12 random bytes, unique per message
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedMessage) return false
        return ciphertext.contentEquals(other.ciphertext) && nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + nonce.contentHashCode()
}
