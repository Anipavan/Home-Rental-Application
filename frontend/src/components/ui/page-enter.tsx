import { useEffect, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";

/**
 * One-time gentle fade + slide-up on every route change. Wraps the
 * shell's <Outlet /> so every page transition feels intentional
 * without each individual page having to opt in.
 *
 * <p>Mechanism: keys the inner wrapper on {@link useLocation}.pathname,
 * so React remounts whenever the route changes — which triggers the
 * `animate-fade-in` keyframe afresh. No IntersectionObserver, no
 * scroll listeners, no per-element animation on the page itself.
 *
 * <p>Motion budget: the existing `animate-fade-in` (0.4s ease-out,
 * 8px translate) — same animation already used on a handful of pages.
 * Deliberately MUCH lighter than the marketing-page reveal vocabulary,
 * which is full-section cascade with longer travel and slower easing.
 * Internal pages need to feel fast first, polished second.
 *
 * <p>Respects {@code prefers-reduced-motion}: when the OS toggle is on
 * we omit the animation class entirely. Listens via {@code matchMedia}
 * so flipping the setting mid-session takes effect on the next nav.
 */
export function PageEnter({ children }: { children: ReactNode }) {
  const location = useLocation();
  const [reducedMotion, setReducedMotion] = useState(false);

  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    setReducedMotion(mq.matches);
    const onChange = () => setReducedMotion(mq.matches);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  return (
    <div
      key={location.pathname}
      className={reducedMotion ? undefined : "animate-fade-in"}
    >
      {children}
    </div>
  );
}
