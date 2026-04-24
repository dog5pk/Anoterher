// Anotether Relay — Cloudflare Worker entry point
//
// Routing table:
//   POST /session/create  — create a new session
//   POST /session/join    — join an existing session
//   POST /session/close   — close a session
//   POST /message/send    — send a message to a session
//   GET  /message/poll    — poll for new messages
//
// The SessionDO Durable Object is exported here so Cloudflare can bind it.
// It handles all mutable session state with atomic single-threaded execution.

import { Env } from "./types.js";
import {
  handleSessionCreate,
  handleSessionJoin,
  handleMessageSend,
  handleMessagePoll,
  handleSessionClose,
} from "./handlers.js";
import { jsonError } from "./response.js";

// Re-export the DO class — Cloudflare requires named exports for DO bindings
export { SessionDO } from "./session_do.js";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();
    const path = url.pathname;

    try {
      // Route matching
      if (method === "POST" && path === "/session/create") {
        return await handleSessionCreate(request, env);
      }

      if (method === "POST" && path === "/session/join") {
        return await handleSessionJoin(request, env);
      }

      if (method === "POST" && path === "/session/close") {
        return await handleSessionClose(request, env);
      }

      if (method === "POST" && path === "/message/send") {
        return await handleMessageSend(request, env);
      }

      if (method === "GET" && path === "/message/poll") {
        return await handleMessagePoll(request, env);
      }

      // Health check — returns minimal info, useful for monitoring
      if (method === "GET" && path === "/health") {
        return new Response(JSON.stringify({ status: "ok" }), {
          headers: { "Content-Type": "application/json" },
        });
      }

      return jsonError(404, "not_found", "Endpoint not found");
    } catch (err) {
      // Catch-all: never expose internal error details to clients
      console.error("Unhandled error:", err);
      return jsonError(500, "internal_error", "An internal error occurred");
    }
  },
};
