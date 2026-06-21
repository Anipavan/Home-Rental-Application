import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// ─── PWA service worker registration ──────────────────────────────
// Self-contained block — delete it (along with public/sw.js,
// public/manifest.webmanifest, and the two PWA <link> tags in
// index.html) to fully remove PWA support. Users with the SW
// already cached can unregister manually via DevTools.
//
// Only registered in production builds — Vite's dev server doesn't
// serve /sw.js cleanly and HMR fights the cache-first strategy.
if ("serviceWorker" in navigator && import.meta.env.PROD) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch((err) => {
      // Failure is non-fatal — the app keeps working without PWA
      // install / offline support, just no app-install prompt.
      // eslint-disable-next-line no-console
      console.warn("Service worker registration failed:", err);
    });
  });
}
