/*
 * Minimal service worker for the Anirudh Homes PWA.
 *
 *  Strategy:
 *   - Pre-cache nothing on install — the Vite-emitted asset hashes
 *     change every build, so a hand-coded list goes stale instantly.
 *   - Runtime cache:
 *       * /assets/* (hashed JS/CSS/img) → cache-first; safe forever
 *         because the hash is in the URL.
 *       * everything else (index.html, /api/*, etc.) → network-first
 *         with a cache fallback, so an updated deploy reaches users
 *         on the next online navigation, and an offline open still
 *         shows the last-good shell.
 *
 *  Why hand-rolled instead of vite-plugin-pwa / Workbox: easy to
 *  revoke. Deleting this file + four lines elsewhere uninstalls the
 *  PWA cleanly. No build-config churn, no new dependencies.
 *
 *  Update flow: `self.skipWaiting()` + a `clients.claim()` on
 *  activate so the freshly-installed SW takes over without forcing
 *  the user to close every tab.
 */

const CACHE = "anirudhhomes-shell-v1";

self.addEventListener("install", (event) => {
  self.skipWaiting();
  event.waitUntil(caches.open(CACHE));
});

self.addEventListener("activate", (event) => {
  // Drop any caches we don't recognise (older builds).
  event.waitUntil(
    (async () => {
      const names = await caches.keys();
      await Promise.all(
        names.filter((n) => n !== CACHE).map((n) => caches.delete(n)),
      );
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;

  // Only handle GET. POST/PUT/DELETE go straight to network; caching
  // them would break the auth + payment flows.
  if (req.method !== "GET") return;

  const url = new URL(req.url);

  // Same-origin only. Razorpay scripts, Google Fonts, etc. go direct.
  if (url.origin !== self.location.origin) return;

  // API calls are never cached — they're per-user, often per-second-
  // fresh, and POST/GET mixed.
  if (url.pathname.startsWith("/api/") || url.pathname.startsWith("/rentals/v1/")) {
    return;
  }

  // /assets/* are content-hashed by Vite, safe to cache forever.
  if (url.pathname.startsWith("/assets/")) {
    event.respondWith(cacheFirst(req));
    return;
  }

  // index.html + favicon + manifest: network-first, fall back to
  // cache when offline so the shell still loads.
  event.respondWith(networkFirst(req));
});

async function cacheFirst(req) {
  const cache = await caches.open(CACHE);
  const hit = await cache.match(req);
  if (hit) return hit;
  const fresh = await fetch(req);
  if (fresh.ok) cache.put(req, fresh.clone());
  return fresh;
}

async function networkFirst(req) {
  const cache = await caches.open(CACHE);
  try {
    const fresh = await fetch(req);
    if (fresh.ok) cache.put(req, fresh.clone());
    return fresh;
  } catch (err) {
    const hit = await cache.match(req);
    if (hit) return hit;
    throw err;
  }
}
