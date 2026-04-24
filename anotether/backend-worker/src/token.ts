// Token generation for Anotether session codes.
//
// Tokens are 6-character codes from an unambiguous alphabet.
// Excluded chars: 0, O (confusable), 1, I, L (confusable)
// Remaining: 32 characters → 32^6 ≈ 1.07 billion combinations
//
// Tokens are NOT secrets — they're room codes.
// Security comes from encryption, not token secrecy.
// With rate limiting (10 creates/hr per IP), brute force is impractical.

const TOKEN_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
const TOKEN_LENGTH = 6;

/**
 * Generate a cryptographically random session token.
 * Uses Web Crypto (available in Cloudflare Workers) for randomness.
 */
export function generateToken(): string {
  const bytes = new Uint8Array(TOKEN_LENGTH);
  crypto.getRandomValues(bytes);
  return Array.from(bytes)
    .map((b) => TOKEN_ALPHABET[b % TOKEN_ALPHABET.length])
    .join("");
}

/**
 * Validate that a token is properly formatted.
 * Does not check existence — only format.
 */
export function isValidToken(token: unknown): token is string {
  if (typeof token !== "string") return false;
  if (token.length !== TOKEN_LENGTH) return false;
  return [...token].every((c) => TOKEN_ALPHABET.includes(c));
}

/**
 * Normalize a token: uppercase, trim whitespace.
 * Handles user input from typing the code by hand.
 */
export function normalizeToken(raw: string): string {
  return raw.trim().toUpperCase();
}
