// Rate limiting for the Anotether relay.
//
// Strategy: sliding window counters stored in KV with TTL equal to the window size.
// Simple and effective. Not perfectly accurate under high concurrency (KV is eventually
// consistent), but adequate for abuse prevention on a privacy messenger.
//
// We hash the IP before storing as a KV key to avoid logging raw IPs in KV.
// Even the hash isn't great from a privacy standpoint, but it's better than plaintext,
// and the TTL ensures it disappears with the window.

import { Env } from "./types.js";

type RateLimitWindow = "minute" | "hour";

interface RateLimitConfig {
  limit: number;
  window: RateLimitWindow;
}

const WINDOW_SECONDS: Record<RateLimitWindow, number> = {
  minute: 60,
  hour: 3600,
};

/**
 * Check and increment a rate limit counter.
 * Returns true if the request is allowed, false if it should be rejected.
 */
export async function checkRateLimit(
  kv: KVNamespace,
  identifier: string, // IP address or token
  action: string,
  config: RateLimitConfig,
): Promise<{ allowed: boolean; remaining: number; reset: number }> {
  const windowSeconds = WINDOW_SECONDS[config.window];
  const windowKey = Math.floor(Date.now() / 1000 / windowSeconds);
  const hash = await hashIdentifier(identifier + action + windowKey);
  const kvKey = `rl:${hash}`;

  const current = await kv.get(kvKey);
  const count = current ? parseInt(current, 10) : 0;
  const reset = (windowKey + 1) * windowSeconds;

  if (count >= config.limit) {
    return { allowed: false, remaining: 0, reset };
  }

  // Increment counter. Fire-and-forget is acceptable here —
  // if the increment fails, we're slightly under-counting which is safe.
  // We don't want to fail the request just because the counter update failed.
  kv.put(kvKey, String(count + 1), { expirationTtl: windowSeconds }).catch(
    () => {/* intentionally ignored */},
  );

  return {
    allowed: true,
    remaining: config.limit - count - 1,
    reset,
  };
}

/**
 * Hash an identifier for privacy before using as a KV key.
 * We don't want raw IP addresses sitting in KV.
 */
async function hashIdentifier(input: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(input);
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  const hashArray = new Uint8Array(hashBuffer);
  // Take first 16 bytes (128 bits) — sufficient for key uniqueness
  return Array.from(hashArray.slice(0, 16))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * Get client IP from Cloudflare request headers.
 * Cloudflare sets CF-Connecting-IP which is the real client IP, not a proxy hop.
 */
export function getClientIp(request: Request): string {
  return (
    request.headers.get("CF-Connecting-IP") ??
    request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ??
    "unknown"
  );
}

/**
 * Build rate limit configs from environment variables.
 */
export function getRateLimitConfigs(env: Env) {
  return {
    sessionCreate: {
      limit: parseInt(env.RATE_LIMIT_SESSION_CREATE_PER_HOUR, 10),
      window: "hour" as RateLimitWindow,
    },
    sessionJoin: {
      limit: parseInt(env.RATE_LIMIT_SESSION_JOIN_PER_HOUR, 10),
      window: "hour" as RateLimitWindow,
    },
    messageSend: {
      limit: parseInt(env.RATE_LIMIT_MESSAGE_SEND_PER_MINUTE, 10),
      window: "minute" as RateLimitWindow,
    },
    messagePoll: {
      limit: parseInt(env.RATE_LIMIT_MESSAGE_POLL_PER_MINUTE, 10),
      window: "minute" as RateLimitWindow,
    },
  };
}
