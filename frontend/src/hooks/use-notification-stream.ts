import { useEffect, useRef } from "react";
import { EventSourcePolyfill } from "event-source-polyfill";
import { useAuthStore } from "@/stores/auth-store";
import type { NotificationResponse } from "@/types/api";

/**
 * Subscribe to the notification-service Server-Sent-Events push for a
 * given user. Fires {@code onPush} whenever the backend sends a new
 * INAPP notification — the bell component uses that to invalidate its
 * react-query cache, surface a live toast, and re-render with the new row.
 *
 * <p>Why {@link EventSourcePolyfill} not the native browser
 * {@link EventSource}: native EventSource can't send arbitrary headers
 * — it ships cookies and that's it. Our auth model is JWT in the
 * {@code Authorization: Bearer …} header, so the polyfill is needed
 * to pass that through.
 *
 * <p>Auto-reconnects on transient drops via {@code heartbeatTimeout}.
 * Cleans up the connection on unmount or when the {@code userId}
 * changes so we don't leak a stream after sign-out.
 *
 * <p>If the access token expires mid-stream the server returns 401 →
 * polyfill retries with the same (expired) token forever — that's
 * the polyfill's weakness. To work around it we re-create the
 * EventSource on a token change by depending on
 * {@code accessToken} in the effect; the axios refresh-interceptor
 * does its rotation and the next render brings up a fresh stream.
 */
/**
 * Subset of the server-side {@code NotificationResponse} that backs an
 * SSE "notification" event. Matches the payload built in
 * {@code NotificationStreamRegistry.buildPayload}.
 */
type StreamPushPayload = Partial<NotificationResponse>;

export function useNotificationStream(
  userId: string | null,
  onPush: (payload: StreamPushPayload) => void,
) {
  const accessToken = useAuthStore((s) => s.accessToken);
  // Stash the callback in a ref so its identity changing across renders
  // doesn't re-create the EventSource — only userId/token rotation does.
  const cbRef = useRef(onPush);
  cbRef.current = onPush;

  useEffect(() => {
    if (!userId || !accessToken) return;

    // Same base resolution rule as the axios client — keep gateway
    // routing identical whether we're hitting localhost in dev or a
    // tunnel URL via the SPA proxy.
    const baseUrl =
      (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
      "http://localhost:8080/rentals/v1";
    const url = `${baseUrl}/notifications/stream/${encodeURIComponent(userId)}`;

    const es = new EventSourcePolyfill(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        // ngrok-skip-browser-warning lets us stream through the same
        // tunnel the rest of the API uses — without it the free tier
        // wraps the response in an HTML interstitial that EventSource
        // can't parse.
        "ngrok-skip-browser-warning": "true",
      },
      // 60s — must exceed the server's 25s heartbeat interval.
      heartbeatTimeout: 60_000,
    });

    es.addEventListener("notification", (ev) => {
      // The polyfill exposes the SSE `data:` line on `ev.data` as a
      // string. Parse defensively so a malformed payload never blows
      // up the listener loop.
      let payload: StreamPushPayload = {};
      try {
        const raw = (ev as MessageEvent).data;
        if (typeof raw === "string" && raw.length > 0) {
          payload = JSON.parse(raw) as StreamPushPayload;
        }
      } catch {
        // Leave payload empty — the bell still invalidates its query
        // so the next refetch picks up the new row.
      }
      // Pass-through trigger — bell decides what to do. We deliberately
      // don't try to insert the payload directly into the cache; the
      // single invalidate keeps the source-of-truth on the GET endpoint.
      cbRef.current(payload);
    });

    es.addEventListener("error", (ev) => {
      // EventSourcePolyfill auto-retries. We just log so failures
      // aren't silent — and the user keeps the safety-net poll
      // running underneath in the bell component anyway.
      // eslint-disable-next-line no-console
      console.debug("[notification-stream] error / reconnect", ev);
    });

    return () => {
      es.close();
    };
  }, [userId, accessToken]);
}
