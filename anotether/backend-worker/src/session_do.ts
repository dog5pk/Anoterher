// SessionDO — Durable Object for atomic session state and message sequencing.
//
// Why this exists:
//   KV is eventually consistent with no atomic read-modify-write. Two clients
//   sending simultaneously would both read seq_counter=N, both write seq=N+1,
//   and one message would silently overwrite the other. For a messenger, lost
//   messages are a blocker, not a footnote.
//
// How Durable Objects fix it:
//   Each DO instance handles one session. The DO runtime guarantees that only
//   one fetch() runs at a time per instance — requests are queued, not concurrent.
//   This makes seq++ atomic without any locking primitives.
//
// Routing:
//   The Worker derives a DO ID from the session token using idFromName(token).
//   Every request for the same token reaches the same DO instance, anywhere
//   in Cloudflare's network.
//
// Storage:
//   DO built-in storage (not KV). It's strongly consistent, transactional,
//   and co-located with the DO instance. Messages are stored under keys
//   msg:{seq} and retrieved by range scan on poll.
//
// Lifecycle:
//   DOs persist until their storage is explicitly deleted or until Cloudflare
//   evicts idle instances (which re-hydrate from storage on next request).
//   We enforce our own TTL by checking expires_at on every request.

import { MessageRecord } from "./types.js";

interface DOState {
  seq_counter: number;
  expires_at: number;
  status: "waiting" | "active" | "closed";
}

const MSG_KEY_PREFIX = "msg:";
const STATE_KEY = "state";

export class SessionDO {
  private state: DurableObjectState;

  constructor(state: DurableObjectState) {
    this.state = state;
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    switch (url.pathname) {
      case "/do/init":      return this.handleInit(request);
      case "/do/activate":  return this.handleActivate(request);
      case "/do/send":      return this.handleSend(request);
      case "/do/poll":      return this.handlePoll(request);
      case "/do/close":     return this.handleClose(request);
      default:
        return new Response(JSON.stringify({ error: "not_found" }), { status: 404 });
    }
  }

  // ── /do/init ─────────────────────────────────────────────────────────────
  // Called when the session is first created. Sets up TTL and initial state.

  private async handleInit(request: Request): Promise<Response> {
    const { expires_at } = await request.json() as { expires_at: number };

    const doState: DOState = { seq_counter: 0, expires_at, status: "waiting" };
    await this.state.storage.put(STATE_KEY, doState);

    return ok({ initialized: true });
  }

  // ── /do/activate ─────────────────────────────────────────────────────────
  // Called when the joiner hits /session/join. Marks session active.

  private async handleActivate(request: Request): Promise<Response> {
    const doState = await this.getState();
    if (!doState) return err(404, "session_not_found", "Session not found");
    if (this.isExpired(doState)) return err(404, "session_expired", "Session has expired");
    if (doState.status !== "waiting") return err(409, "session_full", "Session already has two participants");

    doState.status = "active";
    await this.state.storage.put(STATE_KEY, doState);

    return ok({ activated: true });
  }

  // ── /do/send ──────────────────────────────────────────────────────────────
  // Atomic seq increment + message write. The DO's single-threaded execution
  // means no two /do/send calls run concurrently for the same session.

  private async handleSend(request: Request): Promise<Response> {
    const body = await request.json() as {
      sender_id: "a" | "b";
      ciphertext: string;
      nonce: string;
      token: string;
    };

    const doState = await this.getState();
    if (!doState) return err(404, "session_not_found", "Session not found");
    if (this.isExpired(doState)) return err(404, "session_expired", "Session has expired");
    if (doState.status === "closed") return err(410, "session_closed", "Session has been closed");
    if (doState.status === "waiting") return err(409, "session_not_ready", "Both parties must join before sending");

    // This increment is safe from races: DO processes one request at a time.
    const seq = doState.seq_counter + 1;
    doState.seq_counter = seq;

    const message: MessageRecord = {
      seq,
      token: body.token,
      sender_id: body.sender_id,
      ciphertext: body.ciphertext,
      nonce: body.nonce,
      sent_at: Math.floor(Date.now() / 1000),
    };

    // Write state and message in a single transaction — both commit or neither does.
    await this.state.storage.transaction(async (txn) => {
      txn.put(STATE_KEY, doState);
      txn.put(`${MSG_KEY_PREFIX}${seq}`, message);
    });

    return ok({ seq });
  }

  // ── /do/poll ──────────────────────────────────────────────────────────────
  // Returns all messages with seq > after, up to 50.

  private async handlePoll(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const after = parseInt(url.searchParams.get("after") ?? "0", 10);

    const doState = await this.getState();
    if (!doState) return err(404, "session_not_found", "Session not found");

    if (this.isExpired(doState) && doState.status !== "closed") {
      doState.status = "closed"; // treat as expired for poll purposes
    }

    const maxSeq = doState.seq_counter;
    const messages: MessageRecord[] = [];

    if (maxSeq > after) {
      // DO storage list() with key prefix gives us a sorted map — no race with sends.
      const startKey = `${MSG_KEY_PREFIX}${after + 1}`;
      const endKey = `${MSG_KEY_PREFIX}${Math.min(maxSeq, after + 50)}`;

      const entries = await this.state.storage.list<MessageRecord>({
        start: startKey,
        end: endKey,
        limit: 50,
      });

      for (const msg of entries.values()) {
        messages.push(msg);
      }
    }

    return ok({
      messages,
      seq_counter: doState.seq_counter,
      status: doState.status,
      expires_at: doState.expires_at,
    });
  }

  // ── /do/close ─────────────────────────────────────────────────────────────

  private async handleClose(_request: Request): Promise<Response> {
    const doState = await this.getState();
    if (!doState) return ok({ closed: true }); // already gone

    doState.status = "closed";
    await this.state.storage.put(STATE_KEY, doState);

    return ok({ closed: true });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private async getState(): Promise<DOState | null> {
    return (await this.state.storage.get<DOState>(STATE_KEY)) ?? null;
  }

  private isExpired(doState: DOState): boolean {
    return Math.floor(Date.now() / 1000) > doState.expires_at;
  }
}

function ok(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function err(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
