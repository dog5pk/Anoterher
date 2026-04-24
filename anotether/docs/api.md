# Anotether API Reference v1

Base URL: `https://relay.anotether.app` (or your Cloudflare Worker URL)

All requests: `Content-Type: application/json`
All responses: `Content-Type: application/json`
Transport: HTTPS only

## Rate Limiting

- Session create: 10/hour per IP
- Message send: 60/minute per token
- Message poll: 120/minute per token
- Session join: 30/hour per IP

Rate limit headers returned:
```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1714000060
```

HTTP 429 returned on limit exceeded.

---

## POST /session/create

Creates a new encrypted session and returns a shareable token.

**Request**
```json
{
  "public_key": "string (base64url, 32 bytes X25519 public key)"
}
```

**Response 200**
```json
{
  "token": "string (6 char alphanumeric)",
  "expires_at": "number (unix timestamp)",
  "role": "initiator"
}
```

**Errors**
| Code | Reason |
|------|--------|
| 400 | Missing or invalid public_key |
| 429 | Rate limit exceeded |

**Backend behavior**
- Generates a random 6-character token
- Stores `{public_key, expires_at, seq_counter: 0, status: "waiting"}` in KV
- Key: `session:{token}`, TTL: 86400s (24h)
- Returns token immediately

---

## POST /session/join

Joins an existing session by token.

**Request**
```json
{
  "token": "string (6 char)",
  "public_key": "string (base64url, 32 bytes)"
}
```

**Response 200**
```json
{
  "peer_public_key": "string (base64url, initiator's public key)",
  "expires_at": "number (unix timestamp)",
  "role": "joiner"
}
```

**Errors**
| Code | Reason |
|------|--------|
| 400 | Missing fields |
| 404 | Token not found or expired |
| 409 | Session already has two participants |
| 429 | Rate limit exceeded |

**Backend behavior**
- Looks up `session:{token}` in KV
- If status is not "waiting", returns 409
- Stores joiner's public_key in session record
- Updates status to "active"
- Returns initiator's public_key to joiner

---

## POST /message/send

Sends an encrypted message to a session.

**Request**
```json
{
  "token": "string",
  "sender_id": "string ('a' or 'b')",
  "ciphertext": "string (base64url)",
  "nonce": "string (base64url, 12 bytes)"
}
```

**Response 200**
```json
{
  "seq": "number (sequence number assigned)"
}
```

**Errors**
| Code | Reason |
|------|--------|
| 400 | Missing/invalid fields |
| 404 | Session not found or expired |
| 410 | Session closed |
| 413 | Message too large (max 64KB ciphertext) |
| 429 | Rate limit exceeded |

**Backend behavior**
- Validates session exists and is active
- Increments session's seq_counter
- Stores message in KV under `msg:{token}:{seq}`, TTL 86400s
- Returns assigned seq

---

## GET /message/poll

Retrieves messages sent after a given sequence number.

**Query params**
- `token` — session token (required)
- `after` — sequence number; returns messages with seq > after (default: 0)

**Response 200**
```json
{
  "messages": [
    {
      "seq": "number",
      "sender_id": "string ('a' or 'b')",
      "ciphertext": "string (base64url)",
      "nonce": "string (base64url)",
      "sent_at": "number (unix timestamp)"
    }
  ],
  "session_expires_at": "number",
  "peer_joined": "boolean",
  "session_status": "waiting | active | closed | expired"
}
```

**Errors**
| Code | Reason |
|------|--------|
| 400 | Missing token |
| 404 | Session not found |

**Notes**
- Returns max 50 messages per poll (pagination not needed in v1, sessions are short-lived)
- `peer_joined` lets the initiator know when to derive the shared key
- Client should poll every 2 seconds while session is active

---

## POST /session/close

Closes a session immediately.

**Request**
```json
{
  "token": "string"
}
```

**Response 200**
```json
{
  "closed": true
}
```

**Notes**
- Marks session as closed in KV (does not delete immediately to allow both parties to see the close signal)
- KV TTL handles actual cleanup within 24h
- Either party can close a session

---

## Error Response Format

All errors follow this shape:
```json
{
  "error": {
    "code": "string (machine readable)",
    "message": "string (human readable)"
  }
}
```

Example error codes: `invalid_token`, `session_full`, `rate_limited`, `session_expired`

---

## CORS

Allowed origins: none (API is for the Android app; no browser clients in v1)
If browser client ever needed, add explicit origin allowlist.
