# Anotether Protocol v1

## Session Lifecycle

```
INITIATOR                    RELAY                    JOINER
    │                          │                         │
    │  1. Generate keypair      │                         │
    │  2. POST /session/create  │                         │
    │     {pubkey_initiator}    │                         │
    │──────────────────────────►│                         │
    │  ◄── {token, expires_at} ─│                         │
    │                          │                         │
    │  3. Display token to user │                         │
    │                          │                         │
    │                          │  4. User enters token   │
    │                          │  5. Generate keypair    │
    │                          │  6. POST /session/join  │
    │                          │◄────────────────────────│
    │                          │     {token, pubkey_j}   │
    │                          │  ──{pubkey_initiator}──►│
    │                          │                         │
    │  7. Poll /message/poll   │  8. Poll /message/poll  │
    │◄─── {joiner_pubkey} ─────│                         │
    │                          │                         │
    │  9. Both derive shared    │  10. Both derive shared │
    │     secret via X25519     │      secret via X25519  │
    │     ECDH                  │      ECDH               │
    │                          │                         │
    │  ═══════════ ENCRYPTED MESSAGING BEGINS ══════════ │
    │                          │                         │
    │  11. POST /message/send  │  12. POST /message/send │
    │     {token, ciphertext,  │◄──── {token, ciphertext,│
    │      nonce, seq}         │       nonce, seq}       │
    │──────────────────────────►│                         │
    │                          │  ────────────────────── │
    │  13. GET /message/poll   │  14. GET /message/poll  │
    │      ?token=T&after=seq  │◄──── ?token=T&after=seq │
    │◄──── {messages[]}────────│                         │
```

## Key Exchange Detail

### Initiator (Alice)

```
1. Generate ephemeral X25519 keypair: (alice_sk, alice_pk)
2. POST /session/create { public_key: base64(alice_pk) }
3. Receive: { token: "A3B7KM", expires_at: 1234567890 }
4. Display token. Poll for joiner's public key.
5. On receiving bob_pk:
   shared_secret = X25519(alice_sk, bob_pk)
   session_key = HKDF-SHA256(shared_secret, salt=token, info="anotether-v1")
```

### Joiner (Bob)

```
1. Enter token "A3B7KM"
2. Generate ephemeral X25519 keypair: (bob_sk, bob_pk)
3. POST /session/join { token: "A3B7KM", public_key: base64(bob_pk) }
4. Receive: { alice_pk: base64(alice_pk), expires_at: 1234567890 }
5. Compute:
   shared_secret = X25519(bob_sk, alice_pk)
   session_key = HKDF-SHA256(shared_secret, salt=token, info="anotether-v1")
```

Both Alice and Bob now hold the same `session_key`. The relay never sees either private key
or the shared secret. ECDH is performed locally on each device.

## Message Encryption

Each message is encrypted with ChaCha20-Poly1305 (AEAD):

```
nonce       = random 12 bytes (crypto_random)
ciphertext  = ChaCha20-Poly1305.encrypt(session_key, nonce, plaintext)
mac         = included in ChaCha20-Poly1305 output (16-byte tag)

wire_payload = {
  ciphertext: base64(ciphertext + mac),
  nonce: base64(nonce)
}
```

**Important**: A fresh random nonce is generated per message. This is critical — nonce reuse
with ChaCha20-Poly1305 breaks confidentiality. We use `SecureRandom` on Android.

## Wire Format

### POST /session/create

Request:
```json
{
  "public_key": "base64url(alice_pk_32bytes)"
}
```

Response:
```json
{
  "token": "A3B7KM",
  "expires_at": 1714000000,
  "role": "initiator"
}
```

### POST /session/join

Request:
```json
{
  "token": "A3B7KM",
  "public_key": "base64url(bob_pk_32bytes)"
}
```

Response:
```json
{
  "peer_public_key": "base64url(alice_pk_32bytes)",
  "expires_at": 1714000000,
  "role": "joiner"
}
```

### POST /message/send

Request:
```json
{
  "token": "A3B7KM",
  "sender_id": "a",
  "ciphertext": "base64url(encrypted_bytes)",
  "nonce": "base64url(12_random_bytes)"
}
```

`sender_id` is either `"a"` (initiator) or `"b"` (joiner). This is not identity — it's just
a directional marker so the client can show sent vs received. The relay assigns this on join.

Response:
```json
{
  "seq": 42
}
```

### GET /message/poll

Request: `GET /message/poll?token=A3B7KM&after=41`

Response:
```json
{
  "messages": [
    {
      "seq": 42,
      "sender_id": "a",
      "ciphertext": "base64url(...)",
      "nonce": "base64url(...)",
      "sent_at": 1714000100
    }
  ],
  "session_expires_at": 1714000000,
  "peer_joined": true
}
```

### POST /session/close

Request:
```json
{
  "token": "A3B7KM"
}
```

Response:
```json
{
  "closed": true
}
```

## Session State Machine

```
CREATING ──► WAITING_FOR_PEER ──► ACTIVE ──► CLOSED
                                              ▲
                                    EXPIRED ──┘
```

States:
- `CREATING`: Local keypair generated, creating session on relay
- `WAITING_FOR_PEER`: Token issued, waiting for other party to join
- `ACTIVE`: Both peers present, messages flowing
- `EXPIRED`: TTL elapsed, session auto-destroyed
- `CLOSED`: Manually ended by either party

## Security Properties

| Property | v1 Status | Notes |
|----------|-----------|-------|
| End-to-end encryption | ✅ | ChaCha20-Poly1305 AEAD |
| Forward secrecy | ⚠️ Partial | Ephemeral session keys. No per-message ratchet in v1 |
| Key authentication | ⚠️ TOFU | No out-of-band verification. Relay could MITM during join |
| Anonymous transport | ✅ | No accounts, no identifiers |
| Metadata minimization | ✅ | Relay sees tokens + ciphertext only |
| Session ephemerality | ✅ | 24h TTL, auto-delete |
| Replay protection | ✅ | Sequence numbers + nonce-per-message |

### MITM Risk Acknowledgment

In v1, during `/session/join`, the relay delivers Alice's public key to Bob. A malicious relay
could substitute its own key (classic MITM). This is a known limitation.

Mitigations available but not in v1 scope:
- Out-of-band public key verification (compare fingerprints via voice/QR)
- Key commitment before join
- Transparency logs

v1 threat model explicitly excludes malicious relay operator. This is documented honestly.

## Data Retention Policy (Backend)

| Data | Retention | Justification |
|------|-----------|---------------|
| Session tokens | 24h TTL | Required for operation |
| Public keys | 24h TTL | Required for key exchange |
| Ciphertext blobs | 24h TTL | Required for delivery |
| IP addresses | Never logged | No operational need |
| Message content | Never stored plaintext | Cannot |
| User identity | Never | Cannot exist |
