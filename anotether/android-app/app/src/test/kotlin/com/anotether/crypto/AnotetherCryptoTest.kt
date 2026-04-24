package com.anotether.crypto

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for AnotetherCrypto.
 *
 * These run on the JVM (not device) via Robolectric.
 * Crypto correctness tests — the most important tests in the app.
 */
class AnotetherCryptoTest {

    @Test
    fun keyPairGeneration_producesCorrectSizes() {
        val kp = AnotetherCrypto.generateKeyPair()
        assertEquals("Public key must be 32 bytes", 32, kp.publicKey.size)
        assertEquals("Private key must be 32 bytes", 32, kp.privateKey.size)
    }

    @Test
    fun keyPairGeneration_isDeterministicallyRandom() {
        val kp1 = AnotetherCrypto.generateKeyPair()
        val kp2 = AnotetherCrypto.generateKeyPair()
        assertFalse(
            "Two generated key pairs should not be identical",
            kp1.publicKey.contentEquals(kp2.publicKey),
        )
    }

    @Test
    fun ecdh_bothPartiesDeriveSameSharedKey() {
        val alice = AnotetherCrypto.generateKeyPair()
        val bob = AnotetherCrypto.generateKeyPair()
        val token = "A3B7KM"

        val aliceKey = AnotetherCrypto.deriveSessionKey(alice.privateKey, bob.publicKey, token)
        val bobKey = AnotetherCrypto.deriveSessionKey(bob.privateKey, alice.publicKey, token)

        assertArrayEquals(
            "ECDH must produce the same shared key on both sides",
            aliceKey,
            bobKey,
        )
    }

    @Test
    fun ecdh_differentTokensProduceDifferentKeys() {
        val alice = AnotetherCrypto.generateKeyPair()
        val bob = AnotetherCrypto.generateKeyPair()

        val key1 = AnotetherCrypto.deriveSessionKey(alice.privateKey, bob.publicKey, "AAAAAA")
        val key2 = AnotetherCrypto.deriveSessionKey(alice.privateKey, bob.publicKey, "BBBBBB")

        assertFalse(
            "Different tokens must produce different session keys",
            key1.contentEquals(key2),
        )
    }

    @Test
    fun encryptDecrypt_roundTrip() {
        val alice = AnotetherCrypto.generateKeyPair()
        val bob = AnotetherCrypto.generateKeyPair()
        val sessionKey = AnotetherCrypto.deriveSessionKey(alice.privateKey, bob.publicKey, "TESTOK")

        val plaintext = "Hello, Anotether!"
        val encrypted = AnotetherCrypto.encrypt(sessionKey, plaintext.toByteArray(Charsets.UTF_8))
        val decrypted = AnotetherCrypto.decrypt(sessionKey, encrypted)

        assertEquals(
            "Decrypted text must match original",
            plaintext,
            String(decrypted, Charsets.UTF_8),
        )
    }

    @Test
    fun encryptDecrypt_roundTripWithEmptyMessage() {
        val kp = AnotetherCrypto.generateKeyPair()
        val sessionKey = AnotetherCrypto.deriveSessionKey(kp.privateKey, kp.publicKey, "TESTOK")

        val plaintext = ""
        val encrypted = AnotetherCrypto.encrypt(sessionKey, plaintext.toByteArray())
        val decrypted = AnotetherCrypto.decrypt(sessionKey, encrypted)

        assertEquals("Empty message round-trip", plaintext, String(decrypted))
    }

    @Test
    fun encryptDecrypt_eachCallProducesUniqueNonce() {
        val kp = AnotetherCrypto.generateKeyPair()
        val sessionKey = AnotetherCrypto.deriveSessionKey(kp.privateKey, kp.publicKey, "TESTOK")
        val plaintext = "test".toByteArray()

        val enc1 = AnotetherCrypto.encrypt(sessionKey, plaintext)
        val enc2 = AnotetherCrypto.encrypt(sessionKey, plaintext)

        assertFalse(
            "Each encryption must use a fresh nonce",
            enc1.nonce.contentEquals(enc2.nonce),
        )
    }

    @Test(expected = Exception::class)
    fun decrypt_withWrongKey_throwsException() {
        val alice = AnotetherCrypto.generateKeyPair()
        val bob = AnotetherCrypto.generateKeyPair()
        val eve = AnotetherCrypto.generateKeyPair()

        val correctKey = AnotetherCrypto.deriveSessionKey(alice.privateKey, bob.publicKey, "TOKEN1")
        val wrongKey = AnotetherCrypto.deriveSessionKey(alice.privateKey, eve.publicKey, "TOKEN1")

        val encrypted = AnotetherCrypto.encrypt(correctKey, "secret".toByteArray())
        // This must throw — Poly1305 authentication tag will fail
        AnotetherCrypto.decrypt(wrongKey, encrypted)
    }

    @Test(expected = Exception::class)
    fun decrypt_withTamperedCiphertext_throwsException() {
        val kp = AnotetherCrypto.generateKeyPair()
        val key = AnotetherCrypto.deriveSessionKey(kp.privateKey, kp.publicKey, "TOKEN1")
        val encrypted = AnotetherCrypto.encrypt(key, "authentic message".toByteArray())

        // Flip a bit in the ciphertext
        val tampered = encrypted.ciphertext.copyOf()
        tampered[0] = tampered[0].xor(0xFF.toByte())

        AnotetherCrypto.decrypt(
            key,
            EncryptedMessage(ciphertext = tampered, nonce = encrypted.nonce),
        )
    }

    @Test
    fun keypairDestroy_zerosPrivateKey() {
        val kp = AnotetherCrypto.generateKeyPair()
        kp.destroy()
        assertTrue(
            "Destroyed private key must be all zeros",
            kp.privateKey.all { it == 0.toByte() },
        )
    }

    @Test
    fun base64Url_encodeDecodeRoundTrip() {
        val original = ByteArray(32) { it.toByte() }
        val encoded = Base64Url.encode(original)
        val decoded = Base64Url.decode(encoded)
        assertArrayEquals("Base64url encode/decode must be identity", original, decoded)
    }
}
