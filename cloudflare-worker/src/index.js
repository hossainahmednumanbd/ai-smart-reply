// Cloudflare Worker: Google ID-token authentication + Gemini AI replies.
// Bind a D1 database as DB and set GOOGLE_WEB_CLIENT_ID and GEMINI_API_KEY.
// Do not expose GEMINI_API_KEY or accept arbitrary user-supplied API keys.
const GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
const encoder = new TextEncoder();
function decodePart(s) {
  const normalized = s.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="));
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}
function json(status, body) {
  return new Response(JSON.stringify(body), {
    status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
}
async function verifyGoogleIdToken(token, audience) {
  if (!token || token.length > 12000 || !audience) throw Error("Invalid login configuration");
  const parts = token.split(".");
  if (parts.length !== 3) throw Error("Invalid Google token");
  const header = JSON.parse(new TextDecoder().decode(decodePart(parts[0])));
  const payload = JSON.parse(new TextDecoder().decode(decodePart(parts[1])));
  if (header.alg !== "RS256" || !header.kid) throw Error("Unsupported token signature");
  const now = Math.floor(Date.now() / 1000);
  if (!["accounts.google.com", "https://accounts.google.com"].includes(payload.iss) ||
      payload.aud !== audience || !payload.sub || payload.exp <= now ||
      payload.iat > now + 60) throw Error("Google token expired or audience mismatch");
  const keysResponse = await fetch(GOOGLE_JWKS);
  if (!keysResponse.ok) throw Error("Google signing keys unavailable");
  const { keys } = await keysResponse.json();
  const jwk = keys.find(k => k.kid === header.kid && k.kty === "RSA" && k.use === "sig");
  if (!jwk) throw Error("Unknown Google signing key");
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, decodePart(parts[2]), encoder.encode(parts[0] + "." + parts[1]));
  if (!valid) throw Error("Invalid Google token signature");
  return payload.sub;
}
async function enforceQuota(db, userId) {
  if (!db) throw Error("D1 database not configured");
  const date = new Date().toISOString().slice(0, 10);
  const bucket = date + ":" + userId;
  // Atomic increment prevents concurrent requests bypassing daily limit.
  const result = await db.prepare(
    "INSERT INTO usage (bucket, count) VALUES (?1, 1) ON CONFLICT(bucket) DO UPDATE SET count=count+1 WHERE count < 20 RETURNING count"
  ).bind(bucket).first();
  return result !== null;
}
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET") return json(200, { status: "ok" });
    if (url.pathname !== "/reply") return json(404, { error: "Not found" });
    if (request.method !== "POST") return json(405, { error: "POST required" });
    if (!env.GOOGLE_WEB_CLIENT_ID || !env.GEMINI_API_KEY || !env.DB) return json(503, { error: "Backend not configured" });
    const bearer = request.headers.get("authorization") || "";
    if (!bearer.startsWith("Bearer ")) return json(401, { error: "Google login required" });
    let userId;
    try { userId = await verifyGoogleIdToken(bearer.slice(7), env.GOOGLE_WEB_CLIENT_ID); }
    catch { return json(401, { error: "Invalid or expired Google login" }); }
    let body;
    try {
      if (Number(request.headers.get("content-length") || 0) > 10000) return json(413, { error: "Request too large" });
      body = await request.json();
    } catch { return json(400, { error: "Invalid JSON" }); }
    const message = typeof body?.message === "string" ? body.message.trim() : "";
    if (!message || message.length > 3000) return json(400, { error: "Message must be 1-3000 characters" });
    try {
      if (!await enforceQuota(env.DB, userId)) return json(429, { error: "Daily limit reached (20 requests)" });
    } catch { return json(503, { error: "Usage database unavailable" }); }
    const model = env.GEMINI_MODEL || "gemini-2.5-flash-lite";
    const endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + encodeURIComponent(model) + ":generateContent";
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 20000);
      let response;
      try {
        response = await fetch(endpoint, {
          method: "POST", signal: controller.signal,
          headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: "Write a short, helpful, natural reply to the incoming message in the same language (Bangla, Banglish, or English). Do not invent prices, availability, business policies, or personal facts. Do not provide definitive medical, legal, or financial advice. Return only the suggested reply." }] },
            contents: [{ role: "user", parts: [{ text: message }] }],
            generationConfig: { maxOutputTokens: 220, temperature: 0.5 }
          })
        });
      } finally { clearTimeout(timer); }
      if (!response.ok) return json(502, { error: "AI provider request failed" });
      const data = await response.json();
      const reply = data?.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("").trim();
      if (!reply) return json(502, { error: "No AI reply generated" });
      return json(200, { reply: reply.slice(0, 2000) });
    } catch { return json(502, { error: "AI service temporarily unavailable" }); }
  }
};
