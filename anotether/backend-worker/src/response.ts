// HTTP response helpers for consistent API responses.

import { ErrorResponse } from "./types.js";

export function jsonOk(body: unknown, headers?: Record<string, string>): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      ...securityHeaders(),
      ...headers,
    },
  });
}

export function jsonError(
  status: number,
  code: string,
  message: string,
  extraHeaders?: Record<string, string>,
): Response {
  const body: ErrorResponse = { error: { code, message } };
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...securityHeaders(),
      ...extraHeaders,
    },
  });
}

export function rateLimited(remaining: number, reset: number): Response {
  return jsonError(429, "rate_limited", "Too many requests. Please slow down.", {
    "X-RateLimit-Remaining": String(remaining),
    "X-RateLimit-Reset": String(reset),
    "Retry-After": String(reset - Math.floor(Date.now() / 1000)),
  });
}

function securityHeaders(): Record<string, string> {
  return {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    // No CORS headers — this is a native app API, not for browsers
  };
}
