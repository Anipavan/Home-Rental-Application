import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Scroll-reveal wrapper. Renders children into a div that fades in
 * and slides up the first time it crosses the viewport. Uses
 * IntersectionObserver under the hood — cheap, no scroll listeners,
 * and disconnects after the single firing so scrolling back up
 * doesn't re-trigger.
 *
 * <p>Props:
 *   <ul>
 *     <li>{@code delay} — milliseconds to wait after the element
 *         enters view before starting the animation. Use to stagger
 *         a grid: pass {@code index * 80} to neighbouring tiles for
 *         a controlled cascade.</li>
 *     <li>{@code as} — semantic wrapper override. Defaults to
 *         {@code "div"}; pass {@code "section"} when you need the
 *         outline to land inside a landmark for accessibility.</li>
 *   </ul>
 *
 * <p>Respects {@code prefers-reduced-motion}: when the OS-level
 * accessibility toggle is on we just render the content statically.
 * Listens to changes via {@code matchMedia} so switching the OS
 * setting mid-session takes effect without a reload.
 *
 * <p>Shared across {@code /about} and {@code /} (landing). Keep this
 * generic — page-specific motion goes in the page file.
 */
export function Reveal({
  children,
  delay = 0,
  as: Tag = "div",
  className,
}: {
  children: ReactNode;
  delay?: number;
  as?: "div" | "section" | "article" | "li";
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [revealed, setRevealed] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);

  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    setReducedMotion(mq.matches);
    const onChange = () => setReducedMotion(mq.matches);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  useEffect(() => {
    if (reducedMotion) {
      setRevealed(true);
      return;
    }
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setRevealed(true);
            io.disconnect();
            break;
          }
        }
      },
      { threshold: 0.15, rootMargin: "0px 0px -10% 0px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [reducedMotion]);

  // `Tag` is a string-literal union; React's typing for that path is
  // narrower than the catch-all element interface. Casting to `any`
  // here avoids the JSX intrinsic narrowing without losing the
  // run-time semantics.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const Component = Tag as any;
  return (
    <Component
      ref={ref}
      style={{ animationDelay: revealed ? `${delay}ms` : undefined }}
      className={cn(
        // Pre-reveal: invisible + nudged down so there's nothing to
        // see before IntersectionObserver fires. Without these initial
        // utilities the element flashes in fully-visible during the
        // brief window before the observer attaches.
        !revealed && !reducedMotion && "opacity-0 translate-y-7",
        revealed && !reducedMotion && "animate-reveal-up",
        className,
      )}
    >
      {children}
    </Component>
  );
}
