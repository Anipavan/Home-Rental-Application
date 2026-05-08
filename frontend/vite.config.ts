import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

/**
 * The API base URL the dev server proxies to. Defaults to the local API
 * gateway so `npm run dev` works out of the box. Override with
 * {@code VITE_PROXY_TARGET} when pointing the proxy somewhere else.
 */
const PROXY_TARGET = process.env.VITE_PROXY_TARGET ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
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
});
