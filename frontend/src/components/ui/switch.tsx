import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * Minimal Switch — native checkbox under the hood, styled to look
 * like a Radix/shadcn Switch. We don't pull in @radix-ui/react-switch
 * because the project has only one Switch in flight (admin Settings
 * page) and the saved bundle weight isn't worth the dep churn.
 *
 * <p>Accessibility: native checkbox keeps keyboard nav + form
 * semantics. The visible thumb is a styled div; the actual input is
 * absolute-positioned + opacity 0 over the entire track.
 */
export interface SwitchProps {
  checked?: boolean;
  defaultChecked?: boolean;
  disabled?: boolean;
  onCheckedChange?: (checked: boolean) => void;
  className?: string;
  "aria-label"?: string;
}

export const Switch = React.forwardRef<HTMLInputElement, SwitchProps>(
  function Switch(
    { checked, defaultChecked, disabled, onCheckedChange, className, ...aria },
    ref,
  ) {
    return (
      <label
        className={cn(
          "relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors",
          "focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2",
          checked ? "bg-primary" : "bg-input",
          disabled && "cursor-not-allowed opacity-50",
          className,
        )}
      >
        <input
          ref={ref}
          type="checkbox"
          className="absolute inset-0 m-0 h-full w-full cursor-inherit opacity-0"
          checked={checked}
          defaultChecked={defaultChecked}
          disabled={disabled}
          onChange={(e) => onCheckedChange?.(e.target.checked)}
          {...aria}
        />
        <span
          aria-hidden
          className={cn(
            "pointer-events-none inline-block h-5 w-5 transform rounded-full bg-background shadow ring-0 transition-transform",
            checked ? "translate-x-5" : "translate-x-0",
          )}
        />
      </label>
    );
  },
);
