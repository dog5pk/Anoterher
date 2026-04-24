// Types used throughout the Anotether relay Worker

export interface Env {
  ANOTETHER_KV: KVNamespace;
  SESSION_DO: DurableObjectNamespace;
  SESSION_TTL_SECONDS: string;
  MAX_MESSAGE_SIZE_BYTES: string;
  RATE_LIMIT_SESSION_CREATE_PER_HOUR: string;
  RATE_LIMIT_MESSAGE_SEND_PER_MINUTE: string;
  RATE_LIMIT_MESSAGE_POLL_PER_MINUTE: string;
  RATE_LIMIT_SESSION_JOIN_PER_HOUR: string;
}

// Stored in KV under key: session:{token} — read-only metadata for the Worker.
// Mutable state (seq_counter, messages) lives exclusively in the Durable Object.
export interface SessionRecord {
  token: string;
  initiator_public_key: string; // base64url
  joiner_public_key?: string;   // base64url, set on join
  status: "waiting" | "active" | "closed";
  created_at: number;           // unix timestamp
  expires_at: number;           // unix timestamp
}

// Stored inside the Durable Object (DO storage, not KV)
export interface MessageRecord {
  seq: number;
  token: string;
  sender_id: "a" | "b";
  ciphertext: string;           // base64url
  nonce: string;                // base64url
  sent_at: number;              // unix timestamp
}

// Request bodies
export interface CreateSessionRequest {
  public_key: string;
}

export interface JoinSessionRequest {
  token: string;
  public_key: string;
}

export interface SendMessageRequest {
  token: string;
  sender_id: "a" | "b";
  ciphertext: string;
  nonce: string;
}

export interface CloseSessionRequest {
  token: string;
}

// Response bodies
export interface CreateSessionResponse {
  token: string;
  expires_at: number;
  role: "initiator";
}

export interface JoinSessionResponse {
  peer_public_key: string;
  expires_at: number;
  role: "joiner";
}

export interface SendMessageResponse {
  seq: number;
}

export interface PollResponse {
  messages: MessageRecord[];
  session_expires_at: number;
  peer_joined: boolean;
  peer_public_key: string | null; // joiner's X25519 public key, base64url; null until peer joins
  session_status: SessionRecord["status"];
}

export interface CloseSessionResponse {
  closed: boolean;
}

export interface ErrorResponse {
  error: {
    code: string;
    message: string;
  };
}
