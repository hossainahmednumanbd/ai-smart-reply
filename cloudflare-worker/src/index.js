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

// Cache only a successfully used fallback, scoped to this Worker's configuration.
let resolvedGeminiModel;
function normalizeGeminiModel(value) {
  return String(value || "gemini-2.5-flash-lite").trim().replace(/^models\//, "");
}
async function generateGemini(env, message) {
  const configured = normalizeGeminiModel(env.GEMINI_MODEL);
  const cached = resolvedGeminiModel;
  const model = cached && cached.apiKey === env.GEMINI_API_KEY &&
    cached.configured === configured && cached.expires > Date.now() ? cached.model : configured;
  const controller = new AbortController();
  // One deadline covers model discovery and retries, within the app's timeout.
  const timer = setTimeout(() => controller.abort(), 20000);
  const headers = { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY };
  const body = JSON.stringify({
    systemInstruction: { parts: [{ text: "Write a short, helpful, natural reply to the incoming message in the same language (Bangla, Banglish, or English). Do not invent prices, availability, business policies, or personal facts. Do not provide definitive medical, legal, or financial advice. Return only the suggested reply." }] },
    contents: [{ role: "user", parts: [{ text: message }] }],
    generationConfig: { maxOutputTokens: 220, temperature: 0.5 }
  });
  async function generate(name) {
    const response = await fetch("https://generativelanguage.googleapis.com/v1beta/models/" +
      encodeURIComponent(name) + ":generateContent", {
        method: "POST", signal: controller.signal, headers, body
      });
    // Read the body under the same deadline as the request.
    const data = await response.json().catch(() => null);
    return { response, data };
  }
  try {
    let result = await generate(model);
    if (result.response.status !== 404) return result;
    resolvedGeminiModel = undefined;
    // Discover models using the existing secret. Never return the key or model list.
    const listing = await fetch("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", {
      signal: controller.signal, headers: { "x-goog-api-key": env.GEMINI_API_KEY }
    });
    if (!listing.ok) {
      return { response: listing, data: await listing.json().catch(() => null) };
    }
    const available = await listing.json();
    const candidates = [...new Set((Array.isArray(available.models) ? available.models : [])
      .filter(m => Array.isArray(m.supportedGenerationMethods) &&
        m.supportedGenerationMethods.includes("generateContent"))
      .map(m => normalizeGeminiModel(m.name))
      .filter(name => /^gemini-[a-z0-9.-]+$/.test(name) && name.includes("flash") &&
        !/image|audio|tts|live|native|robotics|computer-use/.test(name) && name !== model))]
      .sort((a, b) => {
        const rank = name => (/preview|experimental|exp-/.test(name) ? 2 : 0) +
          (name.includes("lite") ? 0 : 1);
        return rank(a) - rank(b) || b.localeCompare(a, "en", { numeric: true });
      });
    // Retry only missing-model errors, never quota/authentication failures.
    for (const candidate of candidates.slice(0, 2)) {
      result = await generate(candidate);
      if (result.response.ok) {
        resolvedGeminiModel = { apiKey: env.GEMINI_API_KEY, configured,
          model: candidate, expires: Date.now() + 60 * 60 * 1000 };
      }
      if (result.response.status !== 404) return result;
    }
    return result;
  } finally { clearTimeout(timer); }
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
    try {
      const { response, data } = await generateGemini(env, message);
      if (!response.ok) {
        // Expose a safe diagnostic category, never the API key or raw provider response.
        let providerStatus = "";
        try {
          const detail = data;
          providerStatus = String(detail?.error?.status || "").slice(0, 60);
        } catch {}
        const category = response.status === 429 ? "Gemini quota exceeded"
          : response.status === 403 || response.status === 401 ? "Gemini API key or access denied"
          : response.status === 404 ? "Gemini model unavailable"
          : response.status === 400 ? "Gemini request rejected"
          : "Gemini provider error";
        return json(502, { error: category + " (provider HTTP " + response.status + (providerStatus ? ", " + providerStatus : "") + ")" });
      }
      const reply = data?.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("").trim();
      if (!reply) return json(502, { error: "No AI reply generated" });
      return json(200, { reply: reply.slice(0, 2000) });
    } catch { return json(502, { error: "AI service temporarily unavailable" }); }
  }
};
