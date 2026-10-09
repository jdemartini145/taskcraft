# Proxy Claude de APhid (Cloudflare Worker)

La app nunca incluye claves de API. Este Worker guarda la clave de Anthropic como secreto,
identifica cada instalación con un token aleatorio y aplica un límite diario de uso.

```bash
npm install
wrangler kv namespace create USAGE      # copia el id a wrangler.toml
wrangler secret put ANTHROPIC_API_KEY
wrangler deploy
```

Luego compila la app con la URL pública (no es un secreto):

```bash
./gradlew assembleRelease -Paphid.aiProxyUrl=https://aphid-claude-proxy.<tu-cuenta>.workers.dev
```

- Modelo: `claude-opus-5-5` (configurable en `MODEL`), esfuerzo `low` para texto y `medium` para fotos.
- Fallback de rechazos: `fallbacks: "default"` (beta `server-side-fallback-2026-07-01`).
- Imagen: base64 sin saltos de línea, máximo 5 MB, JPEG/PNG/WebP.
- La app valida todo JSON devuelto contra su esquema; si no cumple, lo descarta.
