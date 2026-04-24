# Anotether — Architecture v1

## Overview

Anotether is an accountless, anonymous, end-to-end encrypted messenger. No user ever registers
an account. No identity is persisted. Sessions are temporary and ephemeral by design.

## System Components

```
┌─────────────────────────────────────────────────────────────────┐
│                        ANDROID CLIENT                           │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐   │
│  │ Compose  │  │ Session  │  │  Crypto  │  │   Network    │   │
│  │   UI     │──│ Manager  │──│  Layer   │──│   Layer      │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘   │
│                                                    │            │
└────────────────────────────────────────────────────│────────────┘
                                                     │ HTTPS
                                                     │
┌────────────────────────────────────────────────────│────────────┐
│                    CLOUDFLARE WORKER                │           │
│                                                     ▼           │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   Relay Endpoints                        │   │
│  │  POST /session/create    POST /message/send              │   │
│  │  POST /session/join      GET  /message/poll              │   │
│  │  POST /session/close                                     │   │
│  └──────────────────────────────────────────────────────────┘   │
│                            │                                    │
│  ┌─────────────────────────▼────────────────────────────────┐   │
│  │                  Cloudflare KV                           │   │
│  │  sessions:{token}   → session metadata (TTL 24h)        │   │
│  │  messages:{token}   → ciphertext queue (TTL 24h)        │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Trust Model

The backend is an **untrusted relay**. It:
- Stores only opaque ciphertext blobs
- Knows session tokens (not identity)
- Never sees plaintext content
- Never knows who is talking to whom
- Has no user table, ever

The backend is treated as **potentially compromised** in our security model.
A subpoena to Cloudflare yields: random-looking tokens, ciphertext, and timestamps.
That is all.

## Android Module Structure

```
com.anotether/
├── crypto/
│   ├── AnotetherCrypto.kt        — X25519 + ChaCha20-Poly1305 wrapper
│   ├── KeyPair.kt                — Ephemeral keypair holder
│   └── SessionKey.kt             — Derived shared key
├── network/
│   ├── RelayClient.kt            — HTTP client (Ktor)
│   ├── RelayApi.kt               — API interface definitions
│   └── models/                   — Request/response data classes
├── session/
│   ├── SessionManager.kt         — Lifecycle coordinator
│   ├── SessionState.kt           — Sealed class state machine
│   └── SessionToken.kt           — Token generation/validation
├── messaging/
│   ├── MessageEngine.kt          — Encrypt/send/poll/decrypt
│   └── Message.kt                — Local message model
└── ui/
    ├── theme/
    │   ├── Theme.kt
    │   ├── Color.kt
    │   └── Type.kt
    ├── screens/
    │   ├── PrivacyDisclosureScreen.kt
    │   ├── HomeScreen.kt
    │   ├── TokenScreen.kt
    │   ├── JoinScreen.kt
    │   ├── ChatScreen.kt
    │   └── SessionExpiredScreen.kt
    └── components/
        ├── TokenDisplay.kt
        └── MessageBubble.kt
```

## Key Design Decisions

### Why Cloudflare Workers + KV?

- Zero-server deployment (no VPS to maintain, no IP to subpoena easily)
- KV provides built-in TTL expiry — sessions self-destruct automatically
- Worker code is auditable and minimal
- Geographic distribution reduces correlation attack surface
- Free tier adequate for v1

Tradeoff: Cloudflare itself is a trusted party for transport. We accept this because:
1. TLS in transit
2. Ciphertext only at rest
3. KV TTL auto-deletes everything

### Why X25519 + ChaCha20-Poly1305?

- X25519: Modern ECDH on Curve25519. Fast, constant-time, no patent risk
- ChaCha20-Poly1305: AEAD cipher. No timing side-channels. Android-native support via BouncyCastle/Tink
- Both are in Android's standard crypto providers
- Libsodium-equivalent security without the native library complexity

Tradeoff vs Signal Protocol: Signal's Double Ratchet provides stronger forward secrecy (per-message key rotation). For v1, we use a simpler single-session derived key. Sessions are short-lived (24h max), which limits the damage window. Full ratchet can be added in v2.

### Session Token Design

Tokens are 6-character alphanumeric codes (uppercase, no ambiguous chars: 0/O, 1/I/L).
Character set: `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (32 chars)
Space: 32^6 = ~1 billion combinations
TTL: 24h
Brute force at 100 req/s with rate limiting: ~115 days to exhaust space

This is adequate for v1. Tokens are not secret — they're like room codes. Security comes from encryption, not token secrecy.

### Polling vs WebSockets

v1 uses polling (GET /message/poll) every 2 seconds.
- Simpler Worker implementation
- No persistent connections (better for serverless)
- Acceptable latency for a messaging app
- WebSocket upgrade is a clean v2 path

### No Push Notifications

Push notifications require device identifiers (FCM token = identity).
v1 uses foreground polling only. Background delivery is out of scope until we solve it without identity.
