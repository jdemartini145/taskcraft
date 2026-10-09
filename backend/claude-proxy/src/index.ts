/**
 * Proxy de APhid hacia Claude. El APK nunca contiene claves: la app envía un token
 * aleatorio por instalación (X-Install-Token) y este Worker aplica un límite diario.
 *
 * POST /v1/{parse|explain|diagnose}  body: {task, prompt, imageBase64?, locale?}  ->  {text}
 * La app valida el JSON devuelto contra su propio esquema antes de usarlo.
 */
import Anthropic from "@anthropic-ai/sdk";

interface Env {
  ANTHROPIC_API_KEY: string;
  USAGE: KVNamespace;
  DAILY_LIMIT: string;
  MODEL: string;
}

interface ProxyRequest {
  task: string;
  prompt: string;
  imageBase64?: string | null;
  locale?: string;
}

const TASKS = new Set(["parse", "explain", "diagnose"]);
const MAX_PROMPT_CHARS = 8_000;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024; // límite de imagen de la API: 5 MB

const SYSTEM =
  "Eres el asistente de APhid, una app de hidroponía para Latinoamérica. Responde en el idioma del usuario. " +
  "Nunca calcules gramos ni dosis de fertilizantes: eso lo hace el motor determinista de la app. " +
  "Cuando se pida JSON, responde solo con el objeto JSON solicitado.";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

function mediaType(b64: string): "image/jpeg" | "image/png" | "image/webp" {
  if (b64.startsWith("iVBOR")) return "image/png";
  if (b64.startsWith("UklGR")) return "image/webp";
  return "image/jpeg";
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    const task = url.pathname.replace(/^\/v1\//, "");
    if (req.method !== "POST" || !TASKS.has(task)) return json({ error: "not_found" }, 404);

    const token = req.headers.get("X-Install-Token") ?? "";
    if (!/^[0-9a-f-]{36}$/i.test(token)) return json({ error: "invalid_token" }, 401);

    // Límite de uso diario por instalación.
    const day = new Date().toISOString().slice(0, 10);
    const key = `${token}:${day}`;
    const used = parseInt((await env.USAGE.get(key)) ?? "0", 10);
    if (used >= parseInt(env.DAILY_LIMIT, 10)) return json({ error: "rate_limited" }, 429);
    await env.USAGE.put(key, String(used + 1), { expirationTtl: 60 * 60 * 48 });

    let body: ProxyRequest;
    try {
      body = await req.json<ProxyRequest>();
    } catch {
      return json({ error: "bad_json" }, 400);
    }
    if (typeof body.prompt !== "string" || body.prompt.length > MAX_PROMPT_CHARS) return json({ error: "bad_prompt" }, 400);

    const content: Anthropic.ContentBlockParam[] = [];
    if (task === "diagnose" && body.imageBase64) {
      if (body.imageBase64.length * 0.75 > MAX_IMAGE_BYTES) return json({ error: "image_too_large" }, 413);
      content.push({ type: "image", source: { type: "base64", media_type: mediaType(body.imageBase64), data: body.imageBase64 } });
    }
    content.push({ type: "text", text: body.prompt });

    const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY });
    try {
      const params = {
        model: env.MODEL,
        max_tokens: 16000,
        system: SYSTEM,
        // Tareas cortas: esfuerzo bajo/medio basta y reduce costo y latencia.
        output_config: { effort: task === "diagnose" ? "medium" : "low" },
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default", // si el modelo declina, la API reintenta con el respaldo recomendado
        messages: [{ role: "user", content }],
      };
      // Los tipos del SDK pueden ir por detrás de `fallbacks: "default"`; se envía tal cual.
      const response = await client.beta.messages.create(params as unknown as Anthropic.Beta.Messages.MessageCreateParamsNonStreaming);
      if (response.stop_reason === "refusal") return json({ error: "refused" }, 422);
      const text = response.content
        .filter((b): b is Anthropic.Beta.Messages.BetaTextBlock => b.type === "text")
        .map((b) => b.text)
        .join("\n");
      return json({ text });
    } catch (e) {
      if (e instanceof Anthropic.RateLimitError) return json({ error: "upstream_rate_limited" }, 503);
      if (e instanceof Anthropic.BadRequestError) return json({ error: "bad_request" }, 400);
      if (e instanceof Anthropic.APIError) return json({ error: "upstream_error", status: e.status }, 502);
      return json({ error: "internal" }, 500);
    }
  },
};
