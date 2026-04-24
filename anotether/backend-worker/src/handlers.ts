// Route handlers for the Anotether relay Worker.
//
// Architecture after the DO migration:
//
//   KV  — immutable session metadata (public keys, expiry). Written once on
//          create/join, read on poll. Never stores messages or seq_counter.
//
//   DO  — all mutable state: seq_counter, message storage, session status.
//          Single-threaded per session instance → atomic seq increment, no race.
//
// Request flow for message/send:
//   Client → Worker (validate, rate-limit) → DO (atomic seq++, write msg) → Worker → Client
//
// Request flow for message/poll:
//   Client → Worker (validate, rate-limit) → KV (read metadata) + DO (read msgs) → Client

import {
  Env,
  SessionRecord,
  MessageRecord,
  CreateSessionRequest,
  JoinSessionRequest,
  SendMessageRequest,
  CloseSessionRequest,
} from "./types.js";
import { generateToken, isValidToken, normalizeToken } from "./token.js";
import {
  isValidPublicKey,
  isValidBase64Url,
  isValidNonce,
  isValidSenderId,
  parseJson,
} from "./validation.js";
import { checkRateLimit, getClientIp, getRateLimitConfigs } from "./ratelimit.js";
import { jsonOk, jsonError, rateLimited } from "./response.js";

const SESSION_PREFIX = "session:";

function sessionKvKey(token: string): string {
  return `${SESSION_PREFIX}${token}`;
}

/** Get a stub for the DO instance that owns this session token. */
function getSessionDO(env: Env, token: string): DurableObjectStub {
  const id = env.SESSION_DO.idFromName(token);
  return env.SESSION_DO.get(id);
}

/** Forward a request to the DO and surface errors consistently. */
async function doFetch(
  stub: DurableObjectStub,
  path: string,
  init?: RequestInit,
): Promise<{ ok: true; data: unknown } | { ok: false; status: number; code: string; message: string }> {
  const resp = await stub.fetch(`http://do${path}`, init);
  const body = await resp.json() as any;

  if (!resp.ok) {
    return {
      ok: false,
      status: resp.status,
      code: body?.error?.code ?? "do_error",
      message: body?.error?.message ?? "Internal DO error",
    };
  }
  return { ok: true, data: body };
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /session/create
// ─────────────────────────────────────────────────────────────────────────────

export async function handleSessionCreate(
  request: Request,
  env: Env,
): Promise<Response> {
  const ip = getClientIp(request);
  const configs = getRateLimitConfigs(env);
  const rl = await checkRateLimit(env.ANOTETHER_KV, ip, "session_create", configs.sessionCreate);
  if (!rl.allowed) return rateLimited(rl.remaining, rl.reset);

  const body = await parseJson(request) as CreateSessionRequest | null;
  if (!body || !isValidPublicKey(body.public_key)) {
    return jsonError(400, "invalid_public_key", "public_key must be a valid 32-byte X25519 public key in base64url format");
  }

  const ttl = parseInt(env.SESSION_TTL_SECONDS, 10);
  const now = Math.floor(Date.now() / 1000);
  const expiresAt = now + ttl;

  // Collision check — negligible probability but cheap to verify
  let token = generateToken();
  for (let i = 0; i < 3; i++) {
    if (!await env.ANOTETHER_KV.get(sessionKvKey(token))) break;
    token = generateToken();
  }

  const session: SessionRecord = {
    token,
    initiator_public_key: body.public_key,
    status: "waiting",
    created_at: now,
    expires_at: expiresAt,
  };

  // Write KV metadata and initialize the DO in parallel
  const doStub = getSessionDO(env, token);
  await Promise.all([
    env.ANOTETHER_KV.put(sessionKvKey(token), JSON.stringify(session), { expirationTtl: ttl }),
    doFetch(doStub, "/do/init", {
      method: "POST",
      body: JSON.stringify({ expires_at: expiresAt }),
      headers: { "Content-Type": "application/json" },
    }),
  ]);

  return jsonOk({ token, expires_at: expiresAt, role: "initiator" });
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /session/join
// ─────────────────────────────────────────────────────────────────────────────

export async function handleSessionJoin(
  request: Request,
  env: Env,
): Promise<Response> {
  const ip = getClientIp(request);
  const configs = getRateLimitConfigs(env);
  const rl = await checkRateLimit(env.ANOTETHER_KV, ip, "session_join", configs.sessionJoin);
  if (!rl.allowed) return rateLimited(rl.remaining, rl.reset);

  const body = await parseJson(request) as JoinSessionRequest | null;
  if (!body) return jsonError(400, "invalid_body", "Request body required");

  const token = normalizeToken(String(body.token ?? ""));
  if (!isValidToken(token)) {
    return jsonError(400, "invalid_token", "Token must be a 6-character alphanumeric code");
  }
  if (!isValidPublicKey(body.public_key)) {
    return jsonError(400, "invalid_public_key", "public_key must be a valid 32-byte X25519 public key in base64url format");
  }

  const raw = await env.ANOTETHER_KV.get(sessionKvKey(token));
  if (!raw) return jsonError(404, "session_not_found", "Session not found or expired");

  const session: SessionRecord = JSON.parse(raw);

  // Check KV metadata first — cheap guard before hitting the DO
  if (session.status !== "waiting") {
    return jsonError(409, "session_full", "Session already has two participants");
  }
  const remainingTtl = session.expires_at - Math.floor(Date.now() / 1000);
  if (remainingTtl <= 0) return jsonError(404, "session_expired", "Session has expired");

  // Tell the DO to activate — it does the authoritative status check
  const doStub = getSessionDO(env, token);
  const doResult = await doFetch(doStub, "/do/activate", { method: "POST" });
  if (!doResult.ok) {
    return jsonError(doResult.status, doResult.code, doResult.message);
  }

  // Update KV metadata with joiner's public key and active status
  const updatedSession: SessionRecord = {
    ...session,
    joiner_public_key: body.public_key,
    status: "active",
  };
  await env.ANOTETHER_KV.put(sessionKvKey(token), JSON.stringify(updatedSession), {
    expirationTtl: remainingTtl,
  });

  return jsonOk({
    peer_public_key: session.initiator_public_key,
    expires_at: session.expires_at,
    role: "joiner",
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /message/send
// ─────────────────────────────────────────────────────────────────────────────

export async function handleMessageSend(
  request: Request,
  env: Env,
): Promise<Response> {
  const body = await parseJson(request) as SendMessageRequest | null;
  if (!body) return jsonError(400, "invalid_body", "Request body required");

  const token = normalizeToken(String(body.token ?? ""));
  if (!isValidToken(token)) {
    return jsonError(400, "invalid_token", "Token must be a 6-character alphanumeric code");
  }
  if (!isValidSenderId(body.sender_id)) {
    return jsonError(400, "invalid_sender_id", "sender_id must be 'a' or 'b'");
  }
  if (!isValidBase64Url(body.ciphertext)) {
    return jsonError(400, "invalid_ciphertext", "ciphertext must be a non-empty base64url string");
  }
  if (!isValidNonce(body.nonce)) {
    return jsonError(400, "invalid_nonce", "nonce must be a 12-byte value in base64url format");
  }

  const maxSize = parseInt(env.MAX_MESSAGE_SIZE_BYTES, 10);
  if (body.ciphertext.length > Math.ceil(maxSize * 4 / 3)) {
    return jsonError(413, "message_too_large", `Messages must be under ${maxSize} bytes`);
  }

  const configs = getRateLimitConfigs(env);
  const rl = await checkRateLimit(env.ANOTETHER_KV, token, "message_send", configs.messageSend);
  if (!rl.allowed) return rateLimited(rl.remaining, rl.reset);

  // Delegate entirely to the DO — it owns seq and message storage atomically.
  // We do not read KV here. The DO has the authoritative session status.
  const doStub = getSessionDO(env, token);
  const doResult = await doFetch(doStub, "/do/send", {
    method: "POST",
    body: JSON.stringify({
      token,
      sender_id: body.sender_id,
      ciphertext: body.ciphertext,
      nonce: body.nonce,
    }),
    headers: { "Content-Type": "application/json" },
  });

  if (!doResult.ok) {
    return jsonError(doResult.status, doResult.code, doResult.message);
  }

  const { seq } = doResult.data as { seq: number };
  return jsonOk({ seq });
}

// ─────────────────────────────────────────────────────────────────────────────
// GET /message/poll
// ─────────────────────────────────────────────────────────────────────────────

export async function handleMessagePoll(
  request: Request,
  env: Env,
): Promise<Response> {
  const url = new URL(request.url);
  const token = normalizeToken(url.searchParams.get("token") ?? "");
  const afterParam = url.searchParams.get("after") ?? "0";

  if (!isValidToken(token)) {
    return jsonError(400, "invalid_token", "Token must be a 6-character alphanumeric code");
  }
  const after = parseInt(afterParam, 10);
  if (isNaN(after) || after < 0) {
    return jsonError(400, "invalid_after", "after must be a non-negative integer");
  }

  const configs = getRateLimitConfigs(env);
  const rl = await checkRateLimit(env.ANOTETHER_KV, token, "message_poll", configs.messagePoll);
  if (!rl.allowed) return rateLimited(rl.remaining, rl.reset);

  // Read KV for public key metadata, DO for messages — in parallel
  const doStub = getSessionDO(env, token);
  const [kvRaw, doResult] = await Promise.all([
    env.ANOTETHER_KV.get(sessionKvKey(token)),
    doFetch(doStub, `/do/poll?after=${after}`),
  ]);

  if (!kvRaw) return jsonError(404, "session_not_found", "Session not found or expired");
  if (!doResult.ok) return jsonError(doResult.status, doResult.code, doResult.message);

  const session: SessionRecord = JSON.parse(kvRaw);
  const doData = doResult.data as {
    messages: MessageRecord[];
    status: string;
    expires_at: number;
  };

  return jsonOk({
    messages: doData.messages,
    session_expires_at: doData.expires_at,
    peer_joined: doData.status === "active" || doData.status === "closed",
    peer_public_key: session.joiner_public_key ?? null,
    session_status: doData.status,
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /session/close
// ─────────────────────────────────────────────────────────────────────────────

export async function handleSessionClose(
  request: Request,
  env: Env,
): Promise<Response> {
  const body = await parseJson(request) as CloseSessionRequest | null;
  if (!body) return jsonError(400, "invalid_body", "Request body required");

  const token = normalizeToken(String(body.token ?? ""));
  if (!isValidToken(token)) {
    return jsonError(400, "invalid_token", "Token must be a 6-character alphanumeric code");
  }

  // Close in both DO (authoritative) and KV (metadata cache) — best effort on both
  const doStub = getSessionDO(env, token);
  const kvRaw = await env.ANOTETHER_KV.get(sessionKvKey(token));

  await Promise.allSettled([
    doFetch(doStub, "/do/close", { method: "POST" }),
    kvRaw
      ? env.ANOTETHER_KV.put(
          sessionKvKey(token),
          JSON.stringify({ ...JSON.parse(kvRaw), status: "closed" } as SessionRecord),
          { expirationTtl: 3600 },
        )
      : Promise.resolve(),
  ]);

  return jsonOk({ closed: true });
}
