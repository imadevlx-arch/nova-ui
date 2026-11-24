/**
 * Nova — serveur des codes d'activation (Cloudflare Worker + KV).
 *
 * Le revendeur crée un code court lié aux identifiants du client ;
 * le client tape ce code sur sa TV, l'app récupère les identifiants.
 *
 *   GET  /v1/code/:code                    → { type, server, username, password } | { type, url }   (public, utilisé par l'app)
 *   POST /v1/codes        (Bearer ADMIN)   ← { server, username, password } | { url } [, days]  → { code }
 *   DELETE /v1/code/:code (Bearer ADMIN)   → révoque
 *
 * Bindings : KV namespace "CODES", secret "ADMIN_TOKEN".
 */

const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans 0/O ni 1/I : lisible et tapable à la télécommande
const CODE_LENGTH = 8;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);

    if (request.method === "GET" && parts[0] === "v1" && parts[1] === "code" && parts[2]) {
      const code = normalize(parts[2]);
      const raw = await env.CODES.get(code);
      if (!raw) return json({ error: "unknown_code" }, 404);
      const entry = JSON.parse(raw);
      return json(entry.account);
    }

    if (!authorized(request, env)) return json({ error: "unauthorized" }, 401);

    if (request.method === "POST" && parts[0] === "v1" && parts[1] === "codes") {
      const body = await request.json().catch(() => null);
      const account = toAccount(body);
      if (!account) return json({ error: "need {server, username, password} or {url}" }, 400);
      const code = await freshCode(env);
      const days = Number(body.days) > 0 ? Number(body.days) : null;
      const options = days ? { expirationTtl: Math.round(days * 86400) } : {};
      await env.CODES.put(code, JSON.stringify({ account, createdAt: Date.now(), note: body.note ?? null }), options);
      return json({ code, expiresInDays: days });
    }

    if (request.method === "DELETE" && parts[0] === "v1" && parts[1] === "code" && parts[2]) {
      await env.CODES.delete(normalize(parts[2]));
      return json({ ok: true });
    }

    return json({ error: "not_found" }, 404);
  },
};

function toAccount(b) {
  if (!b) return null;
  if (b.url) return { type: "m3u", url: String(b.url) };
  if (b.server && b.username && b.password)
    return { type: "xtream", server: String(b.server), username: String(b.username), password: String(b.password) };
  return null;
}

async function freshCode(env) {
  for (let i = 0; i < 10; i++) {
    const bytes = crypto.getRandomValues(new Uint8Array(CODE_LENGTH));
    const code = Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join("");
    if (!(await env.CODES.get(code))) return code;
  }
  throw new Error("no free code");
}

function normalize(c) {
  return c.toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function authorized(request, env) {
  const header = request.headers.get("Authorization") || "";
  return env.ADMIN_TOKEN && header === `Bearer ${env.ADMIN_TOKEN}`;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
