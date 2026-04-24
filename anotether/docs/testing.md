# Anotether Testing Guide

## Backend Tests

### Unit Tests (Vitest)

```bash
cd backend-worker
npm test
```

Tests cover:
- Token generation (format, uniqueness, character set)
- Session create/join/close lifecycle
- Message send and poll ordering
- Rate limiting logic
- TTL and expiry behavior
- Error response format

### Integration Tests

Use `wrangler dev` for local Worker and run:

```bash
npm run test:integration
```

#### Manual API Smoke Test

```bash
# 1. Create session
RESP=$(curl -s -X POST http://localhost:8787/session/create \
  -H "Content-Type: application/json" \
  -d '{"public_key":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}')
TOKEN=$(echo $RESP | jq -r '.token')
echo "Token: $TOKEN"

# 2. Join session
curl -s -X POST http://localhost:8787/session/join \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"$TOKEN\",\"public_key\":\"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=\"}"

# 3. Send message
curl -s -X POST http://localhost:8787/message/send \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"$TOKEN\",\"sender_id\":\"a\",\"ciphertext\":\"AAAA\",\"nonce\":\"AAAAAAAAAAAAAAAAAA==\"}"

# 4. Poll messages
curl -s "http://localhost:8787/message/poll?token=$TOKEN&after=0"

# 5. Close session
curl -s -X POST http://localhost:8787/session/close \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"$TOKEN\"}"
```

---

## Android Tests

### Unit Tests

```bash
cd android-app
./gradlew test
```

Covers:
- `AnotetherCrypto`: key generation, ECDH, encrypt/decrypt round-trip
- `SessionToken`: token validation
- `MessageEngine`: encrypt then decrypt produces original plaintext
- `SessionManager`: state machine transitions

### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

Requires a connected device or emulator.

Covers:
- Full session flow end-to-end (mock relay)
- UI state transitions
- Secure storage read/write

### Crypto Sanity Test

Run this to verify crypto layer before connecting to real backend:

```kotlin
// In AnotetherCryptoTest.kt
@Test
fun testFullHandshake() {
    val alice = AnotetherCrypto.generateKeyPair()
    val bob = AnotetherCrypto.generateKeyPair()
    
    val aliceShared = AnotetherCrypto.deriveSharedKey(alice.privateKey, bob.publicKey, "TESTOK")
    val bobShared = AnotetherCrypto.deriveSharedKey(bob.privateKey, alice.publicKey, "TESTOK")
    
    assert(aliceShared.contentEquals(bobShared)) { "Shared secrets must match" }
    
    val plaintext = "hello anotether"
    val encrypted = AnotetherCrypto.encrypt(aliceShared, plaintext.toByteArray())
    val decrypted = AnotetherCrypto.decrypt(bobShared, encrypted)
    
    assert(String(decrypted) == plaintext) { "Decryption must produce original plaintext" }
}
```

---

## Two-Device End-to-End Test

To verify the full system:

1. Install debug APK on two Android devices (or one device + one emulator)
2. On Device A: Create Session → note the 6-character token
3. On Device B: Join Session → enter token
4. Verify chat screen appears on both
5. Send messages both ways, verify delivery
6. Verify messages are readable on both sides (decryption works)
7. Close session on one device, verify expired screen on both
8. Restart app on both, verify session does NOT persist

---

## Security Test Cases

| Test | Expected |
|------|----------|
| Poll non-existent token | 404 |
| Join already-full session | 409 |
| Send to expired session | 404 |
| Send message >64KB | 413 |
| Create 11 sessions in 1 hour from same IP | 429 on 11th |
| Invalid base64 public key | 400 |
| Decrypt message with wrong key | AuthenticationException |
| Reuse nonce (simulated) | Should be caught — but nonce reuse is prevented by always using SecureRandom |

---

## Performance Expectations

| Metric | Target | Notes |
|--------|--------|-------|
| Session create | <200ms | Worker cold start first time |
| Message send | <150ms | KV write |
| Message poll (empty) | <100ms | KV read |
| Message poll (10 msgs) | <200ms | 10 KV reads |
| Key exchange (Android) | <10ms | X25519 is fast |
| Encrypt 1KB message | <5ms | ChaCha20 is fast |
