import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

/**
 * The API base URL the dev server proxies to. Defaults to the local API
 * gateway so `npm run dev` works out of the box. Override with
 * {@code VITE_PROXY_TARGET} when pointing the proxy somewhere else.
 */
const PROXY_TARGET = process.env.VITE_PROXY_TARGET ?? "http://localhost:8080";

/**
 * Public origins the prod SPA is allowed to fetch from. Mostly the
 * gateway + any third-party CDNs we lean on (Google Fonts for the
 * brand fonts). Override with VITE_CSP_CONNECT_SRC when the prod
 * gateway lives at a different origin than the API base URL the SPA
 * is built against.
 */
function buildContentSecurityPolicy(mode: string): string {
  const isDev = mode !== "production";

  // The API origin the SPA actually talks to. In direct mode this is
  // the gateway's absolute origin; in proxy mode it's the SPA's own
  // origin (because Vite forwards /api → gateway). Either way we have
  // to allow it under connect-src.
  const apiOrigin =
    process.env.VITE_API_BASE_URL?.replace(/^(https?:\/\/[^/]+).*$/, "$1") ??
    "http://localhost:8080";

  const connectSrcExtra = (process.env.VITE_CSP_CONNECT_SRC ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean)
    .join(" ");

  // In dev Vite injects HMR client + uses module preloading, both of
  // which need 'unsafe-inline' for the injected <script> tags + the
  // WebSocket back to the dev server. We tighten in prod where we
  // control the build output and there are no inline scripts.
  const scriptSrc = isDev
    ? "'self' 'unsafe-inline' 'unsafe-eval'"
    : "'self'";
  const connectSrc = isDev
    ? `'self' ${apiOrigin} ws: wss: ${connectSrcExtra}`.trim()
    : `'self' ${apiOrigin} ${connectSrcExtra}`.trim();

  return [
    "default-src 'self'",
    `script-src ${scriptSrc}`,
    // Tailwind injects style tags; Google Fonts also adds inline styles.
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
    "font-src 'self' https://fonts.gstatic.com data:",
    // data: + blob: needed for image previews + canvas signatures.
    "img-src 'self' data: blob: https:",
    `connect-src ${connectSrc}`,
    // Lock anything trying to embed us in a frame; matches the
    // gateway's X-Frame-Options: DENY (defense in depth).
    "frame-ancestors 'none'",
    "form-action 'self'",
    "base-uri 'self'",
    "object-src 'none'",
    "upgrade-insecure-requests",
  ].join("; ");
}

/**
 * Tiny plugin that injects a <meta http-equiv="Content-Security-Policy">
 * tag plus a couple of secondary security headers into index.html at
 * build time. Doing it here (rather than in the static HTML) lets us
 * compute the policy based on Vite's mode + env vars without
 * duplicating it.
 */
function securityHeadersPlugin() {
  return {
    name: "hearth-security-headers",
    transformIndexHtml: {
      order: "pre" as const,
      handler(html: string, ctx: { server?: unknown }) {
        const mode = ctx.server ? "development" : "production";
        const csp = buildContentSecurityPolicy(mode);
        const tags = [
          `<meta http-equiv="Content-Security-Policy" content="${csp}">`,
          // Referrer hardening — keeps password-reset / signed-URL
          // query params out of upstream analytics referrers.
          `<meta name="referrer" content="strict-origin-when-cross-origin">`,
        ].join("\n    ");
        // Insert just after the <meta charset> line so the CSP applies
        // to every subsequent <link> / <script> in <head>.
        return html.replace(
          /<meta charset="UTF-8" \/>/i,
          `<meta charset="UTF-8" />\n    ${tags}`,
        );
      },
    },
  };
}

export default defineConfig(({ mode }) => ({
  plugins: [react(), securityHeadersPlugin()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  build: {
    // Source maps are useful for error tracking (Sentry) but should
    // never ship to the public web — they leak the entire codebase to
    // anyone who opens DevTools. "hidden" keeps the maps on disk so
    // CI can upload them to Sentry but doesn't reference them from the
    // built JS. Override with VITE_SOURCEMAP for one-off debugging
    // builds.
    sourcemap:
      mode === "production"
        ? (process.env.VITE_SOURCEMAP === "true" ? true : ("hidden" as const))
        : true,
  },
  server: {
    port: 4200,
    host: true,
    /**
     * Same-origin proxy for the API gateway. Used by the demo-tunnel flow:
     * we expose ONE public URL (Vite on :4200), the browser hits
     * /api/rentals/v1/...
     * which Vite forwards to the gateway. No CORS dance, only one tunnel.
     *
     * For this to take effect set VITE_API_BASE_URL=/api/rentals/v1 in the
     * frontend env (see .env.example) so the axios client builds requests
     * against the proxy path instead of the hardcoded localhost URL.
     */
    proxy: {
      "/api": {
        target: PROXY_TARGET,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ""),
        // Strip the cookie domain so cookies set by the gateway round-trip
        // back to the browser even when accessed via a tunnel.
        cookieDomainRewrite: "",
        // Forge the Origin header to match what the gateway's CORS filter
        // allows. When accessed via a tunnel the browser sends e.g.
        //   Origin: https://foo.ngrok-free.dev
        // …which isn't in CORS_ALLOWED_ORIGINS so the gateway 403's. From
        // the gateway's POV every request through this proxy *is* coming
        // from localhost:4200, so we rewrite the header to match. Override
        // with VITE_PROXY_ORIGIN if your gateway whitelists a different
        // origin.
        configure: (proxy) => {
          const forgedOrigin =
            process.env.VITE_PROXY_ORIGIN ?? "http://localhost:4200";
          proxy.on("proxyReq", (proxyReq) => {
            proxyReq.setHeader("origin", forgedOrigin);
            proxyReq.setHeader("referer", forgedOrigin + "/");
          });
        },
      },
    },
    // Vite's HMR + dev server checks the Host header against
    // server.host. With a tunnel domain in front (ngrok, trycloudflare,
    // localtunnel, ...) we'd otherwise get "Blocked request" on every
    // navigation. Allowed hosts can be tightened via VITE_ALLOWED_HOST.
    allowedHosts: process.env.VITE_ALLOWED_HOST
      ? [process.env.VITE_ALLOWED_HOST]
      : true,
  },
}));
