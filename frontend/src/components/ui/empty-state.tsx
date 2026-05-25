import { type ReactNode } from "react";
import { Card } from "./card";
import { cn } from "@/lib/utils";

/**
 * Polished empty-state card. Replaces ad-hoc {@code <Card className="p-10
 * text-center">No items yet</Card>} blocks scattered across pages with
 * one consistent visual: centred content, optional icon chip, optional
 * call-to-action.
 *
 * <p>Three brand variants:
 * <ul>
 *   <li><b>default</b> — neutral card surface. The standard empty-list
 *       look.</li>
 *   <li><b>success</b> — gradient-brand-soft wash + primary border tint.
 *       Use when the empty state is GOOD news, like "you're all paid
 *       up" or "no maintenance tickets — your flat is happy".</li>
 *   <li><b>info</b> — slate-tinted background. Use for "nothing in this
 *       tab yet" framing that's neither positive nor negative.</li>
 * </ul>
 *
 * <p>The icon chip auto-tints to match the variant. Pass {@code action}
 * to render a CTA (typically a primary Button → register / new flat /
 * raise ticket).
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  variant = "default",
  className,
}: {
  /** Optional icon — renders inside a tinted square chip above the title. */
  icon?: React.ComponentType<{ className?: string }>;
  title: string;
  description?: string;
  /** Optional CTA below the description. Usually a Button. */
  action?: ReactNode;
  variant?: "default" | "success" | "info";
  className?: string;
}) {
  return (
    <Card
      className={cn(
        "p-10 sm:p-12 text-center",
        variant === "success" && "gradient-brand-soft border-primary/20",
        variant === "info" && "bg-secondary/30 border-border/40",
        className,
      )}
    >
      {Icon && (
        <div
          className={cn(
            "size-14 rounded-2xl grid place-items-center mx-auto mb-4",
            variant === "success"
              ? "bg-primary/15 text-primary"
              : variant === "info"
                ? "bg-foreground/5 text-foreground/70"
                : "bg-primary/10 text-primary",
          )}
        >
          <Icon className="size-6" />
        </div>
      )}
      <p className="font-display font-semibold text-lg sm:text-xl">{title}</p>
      {description && (
        <p className="text-muted-foreground text-sm mt-2 max-w-sm mx-auto leading-relaxed">
          {description}
        </p>
      )}
      {action && <div className="mt-6 flex justify-center">{action}</div>}
    </Card>
  );
}
