import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

/**
 * Inline expand/collapse Card wrapper.
 *
 * <p>Used by the society surfaces (owner / tenant / public) so each
 * card on the page collapses to a one-line header that shows a
 * summary string on the right. A resident or operator can scan the
 * page top-down and drill into one section at a time.
 *
 * <p>Lives in components/ui rather than being inlined in each page
 * because the public + owner + maintainer society pages all need
 * the same affordance with the same visual language.
 *
 * <p>Implementation: hand-rolled rather than depending on
 * @radix-ui/react-collapsible — the accessibility surface is small
 * (a button + a controlled region) and it saves a runtime
 * dependency. {@code aria-expanded} on the button covers screen
 * readers.
 */
export function CollapsibleSection({
  title,
  icon: Icon,
  summary,
  actions,
  defaultOpen = true,
  className,
  children,
}: {
  title: string;
  icon?: React.ComponentType<{ className?: string }>;
  /** Short string shown on the right of the collapsed header so the
   *  reader knows what's inside before clicking. e.g. "12 entries ·
   *  ₹1,00,000". Hidden on small screens. */
  summary?: string;
  /** Optional actions (small buttons) that render inline with the
   *  header but stop click propagation so they don't toggle the
   *  collapse. Useful for "Recorded by maintainer" badges, etc. */
  actions?: React.ReactNode;
  defaultOpen?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <Card className={className}>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center gap-3 p-4 hover:bg-secondary/30 transition-colors text-left"
      >
        {Icon && (
          <Icon className="size-5 text-primary shrink-0" aria-hidden />
        )}
        <h3 className="font-display font-semibold text-base flex-1 min-w-0 truncate">
          {title}
        </h3>
        {summary && (
          <span className="text-xs text-muted-foreground truncate max-w-[40%] hidden sm:inline">
            {summary}
          </span>
        )}
        {actions && (
          // Stop propagation so a button INSIDE actions doesn't
          // also toggle the collapse on click.
          <span onClick={(e) => e.stopPropagation()} className="shrink-0">
            {actions}
          </span>
        )}
        <ChevronDown
          className={`size-4 text-muted-foreground shrink-0 transition-transform ${
            open ? "rotate-180" : ""
          }`}
        />
      </button>
      {open && (
        <CardContent className="px-5 pb-5 pt-0">
          {children}
        </CardContent>
      )}
    </Card>
  );
}
