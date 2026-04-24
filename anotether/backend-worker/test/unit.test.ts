// Backend unit tests for token generation and validation logic.
// These don't require a Worker runtime — pure TypeScript.

import { describe, it, expect } from "vitest";
import { generateToken, isValidToken, normalizeToken } from "../src/token.js";
import { isValidPublicKey, isValidNonce, isValidSenderId } from "../src/validation.js";

describe("Token generation", () => {
  it("generates a 6-character token", () => {
    const token = generateToken();
    expect(token).toHaveLength(6);
  });

  it("uses only characters from the allowed alphabet", () => {
    const ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    for (let i = 0; i < 100; i++) {
      const token = generateToken();
      for (const char of token) {
        expect(ALPHABET).toContain(char);
      }
    }
  });

  it("generates unique tokens (probabilistic)", () => {
    const tokens = new Set(Array.from({ length: 1000 }, generateToken));
    // With 1B+ combinations, 1000 tokens should all be unique
    expect(tokens.size).toBe(1000);
  });

  it("never contains ambiguous characters", () => {
    const AMBIGUOUS = ["0", "O", "1", "I", "L"];
    for (let i = 0; i < 500; i++) {
      const token = generateToken();
      for (const char of AMBIGUOUS) {
        expect(token).not.toContain(char);
      }
    }
  });
});

describe("Token validation", () => {
  it("accepts valid tokens", () => {
    expect(isValidToken("A3B7KM")).toBe(true);
    expect(isValidToken("223456")).toBe(true);
    expect(isValidToken("ZZZZZZ")).toBe(true);
  });

  it("rejects invalid tokens", () => {
    expect(isValidToken("")).toBe(false);
    expect(isValidToken("TOOSHO")).toBe(false); // contains O
    expect(isValidToken("A3B7K")).toBe(false);  // too short
    expect(isValidToken("A3B7KMM")).toBe(false); // too long
    expect(isValidToken("a3b7km")).toBe(false);  // lowercase
    expect(isValidToken(null)).toBe(false);
    expect(isValidToken(undefined)).toBe(false);
    expect(isValidToken(123456)).toBe(false);
  });
});

describe("Token normalization", () => {
  it("uppercases and trims", () => {
    expect(normalizeToken("  a3b7km  ")).toBe("A3B7KM");
    expect(normalizeToken("a3b7km")).toBe("A3B7KM");
  });
});

describe("Public key validation", () => {
  // 32 bytes in base64url = 43 chars (without padding)
  const validKey = "A".repeat(43);

  it("accepts a valid 43-char base64url key", () => {
    expect(isValidPublicKey(validKey)).toBe(true);
  });

  it("accepts padded base64url", () => {
    expect(isValidPublicKey(validKey + "=")).toBe(true);
  });

  it("rejects wrong length", () => {
    expect(isValidPublicKey("A".repeat(42))).toBe(false);
    expect(isValidPublicKey("A".repeat(44))).toBe(false);
  });

  it("rejects non-string", () => {
    expect(isValidPublicKey(null)).toBe(false);
    expect(isValidPublicKey(42)).toBe(false);
  });

  it("rejects invalid characters", () => {
    expect(isValidPublicKey("A".repeat(42) + "!")).toBe(false);
  });
});

describe("Nonce validation", () => {
  // 12 bytes in base64url = 16 chars
  const validNonce = "AAAAAAAAAAAAAAAA"; // 16 chars

  it("accepts valid nonce", () => {
    expect(isValidNonce(validNonce)).toBe(true);
  });

  it("rejects wrong length", () => {
    expect(isValidNonce("A".repeat(15))).toBe(false);
    expect(isValidNonce("A".repeat(17))).toBe(false);
  });
});

describe("Sender ID validation", () => {
  it("accepts a and b", () => {
    expect(isValidSenderId("a")).toBe(true);
    expect(isValidSenderId("b")).toBe(true);
  });

  it("rejects anything else", () => {
    expect(isValidSenderId("c")).toBe(false);
    expect(isValidSenderId("A")).toBe(false);
    expect(isValidSenderId("")).toBe(false);
    expect(isValidSenderId(null)).toBe(false);
  });
});
