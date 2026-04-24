// Input validation for all relay endpoints.
// We validate early and fail fast with clear errors.

/**
 * Validate a base64url-encoded public key.
 * X25519 public keys are exactly 32 bytes → 43 base64url chars (no padding) or 44 (with =).
 */
export function isValidPublicKey(value: unknown): value is string {
  if (typeof value !== "string") return false;
  // Accept both padded and unpadded base64url
  const stripped = value.replace(/=+$/, "");
  // 32 bytes base64url encodes to 43 chars
  if (stripped.length !== 43) return false;
  // Validate base64url character set
  return /^[A-Za-z0-9\-_]+$/.test(stripped);
}

/**
 * Validate a base64url string of any reasonable length.
 */
export function isValidBase64Url(value: unknown): value is string {
  if (typeof value !== "string") return false;
  if (value.length === 0) return false;
  return /^[A-Za-z0-9\-_]+=*$/.test(value);
}

/**
 * Validate nonce: 12 bytes → 16 base64url chars.
 */
export function isValidNonce(value: unknown): value is string {
  if (typeof value !== "string") return false;
  const stripped = value.replace(/=+$/, "");
  return stripped.length === 16 && /^[A-Za-z0-9\-_]+$/.test(stripped);
}

/**
 * Validate sender_id: only 'a' or 'b' allowed.
 */
export function isValidSenderId(value: unknown): value is "a" | "b" {
  return value === "a" || value === "b";
}

/**
 * Safely parse JSON from a request body.
 * Returns null on any parse failure — no exceptions propagate.
 */
export async function parseJson(request: Request): Promise<unknown> {
  try {
    const contentType = request.headers.get("Content-Type") ?? "";
    if (!contentType.includes("application/json")) return null;
    return await request.json();
  } catch {
    return null;
  }
}
