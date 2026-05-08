import { useQuery } from "@tanstack/react-query";
import {
  Phone,
  Mail,
  Copy,
  Check,
  Loader2,
  UserCircle,
} from "lucide-react";
import { useState } from "react";
import { usersApi } from "@/lib/api/users";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { toast } from "@/hooks/use-toast";

/**
 * Reusable contact-this-person popover. Used on:
 *   • Tenant "My Home → Contact owner"
 *   • Owner tenants list (Phone / Mail buttons on each tenant card)
 *
 * Resolves the user via {@link usersApi.byAuthId} (lazy — on first open) and
 * surfaces three actions: tel:, mailto:, and a "copy phone" fallback for
 * desktop browsers where tel: links may not do anything useful.
 *
 * Two trigger styles are supported:
 *   - `variant="button"` — full-width button (used on My Home Quick Actions)
 *   - `variant="icon"`   — small square icon button (used on owner tenant cards)
 */
interface Props {
  /**
   * The {@code authUserId} (NOT the user-service primary id). User Service
   * exposes `GET /users/auth/{authUserId}` for cross-service lookup, so we
   * accept whatever id the calling page already has on hand.
   */
  authUserId: string;
  variant?: "button" | "icon-phone" | "icon-mail";
  className?: string;
  /** Used in dropdown header — defaults to the resolved firstName/lastName. */
  label?: string;
}

export function ContactPersonPopover({
  authUserId,
  variant = "button",
  className,
  label,
}: Props) {
  const [open, setOpen] = useState(false);

  // Lazy: only fire the fetch once the dropdown opens. Avoids N+1 on tenants
  // page where we render this component once per tenant card.
  const userQ = useQuery({
    queryKey: ["user-by-auth", authUserId],
    queryFn: () => usersApi.byAuthId(authUserId),
    enabled: !!authUserId && open,
    staleTime: 60_000,
  });

  const trigger = renderTrigger(variant, className);

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-72">
        {userQ.isLoading ? (
          <div className="px-3 py-4 text-center text-sm text-muted-foreground flex items-center justify-center gap-2">
            <Loader2 className="size-3.5 animate-spin" />
            Looking up contact…
          </div>
        ) : userQ.isError || !userQ.data ? (
          <div className="px-3 py-4 text-sm">
            <UserCircle className="size-5 mx-auto text-muted-foreground" />
            <p className="text-center text-muted-foreground mt-2">
              Couldn't load contact info
            </p>
          </div>
        ) : (
          <>
            <DropdownMenuLabel>
              {label ??
                `${userQ.data.firstName ?? ""} ${userQ.data.lastName ?? ""}`.trim() ??
                "Contact"}
            </DropdownMenuLabel>
            <DropdownMenuSeparator />

            {userQ.data.phone ? (
              <>
                <DropdownMenuItem asChild>
                  <a
                    href={`tel:${userQ.data.phone}`}
                    className="flex items-center gap-2"
                  >
                    <Phone className="size-4" />
                    <div className="flex-1">
                      <p className="text-sm">Call</p>
                      <p className="text-[11px] text-muted-foreground">
                        {userQ.data.phone}
                      </p>
                    </div>
                  </a>
                </DropdownMenuItem>
                <CopyItem
                  value={userQ.data.phone}
                  successText="Phone copied"
                />
              </>
            ) : (
              <DropdownMenuItem disabled>
                <Phone className="size-4 text-muted-foreground" />
                <span className="text-sm text-muted-foreground">
                  No phone on file
                </span>
              </DropdownMenuItem>
            )}

            {userQ.data.email ? (
              <DropdownMenuItem asChild>
                <a
                  href={`mailto:${userQ.data.email}`}
                  className="flex items-center gap-2"
                >
                  <Mail className="size-4" />
                  <div className="flex-1">
                    <p className="text-sm">Email</p>
                    <p className="text-[11px] text-muted-foreground">
                      {userQ.data.email}
                    </p>
                  </div>
                </a>
              </DropdownMenuItem>
            ) : (
              <DropdownMenuItem disabled>
                <Mail className="size-4 text-muted-foreground" />
                <span className="text-sm text-muted-foreground">
                  No email on file
                </span>
              </DropdownMenuItem>
            )}
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function CopyItem({
  value,
  successText,
}: {
  value: string;
  successText: string;
}) {
  const [copied, setCopied] = useState(false);
  return (
    <DropdownMenuItem
      onSelect={async (e) => {
        e.preventDefault();
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
          toast({ title: successText });
        } catch {
          toast({
            variant: "destructive",
            title: "Couldn't copy",
            description: "Your browser blocked clipboard access.",
          });
        }
      }}
    >
      {copied ? <Check className="size-4 text-emerald-500" /> : <Copy className="size-4" />}
      <span className="text-sm">{copied ? "Copied" : "Copy phone"}</span>
    </DropdownMenuItem>
  );
}

function renderTrigger(
  variant: NonNullable<Props["variant"]>,
  className?: string,
) {
  if (variant === "button") {
    // Has Phone icon — used on My Home Quick Actions
    return (
      <button
        type="button"
        className={`inline-flex items-center justify-center gap-2 w-full h-10 rounded-lg border bg-background hover:bg-secondary text-sm font-medium transition-colors ${className ?? ""}`}
      >
        <Phone className="size-4" /> Contact owner
      </button>
    );
  }
  if (variant === "icon-mail") {
    return (
      <button
        type="button"
        className={`size-8 rounded-md bg-secondary hover:bg-primary/10 hover:text-primary grid place-items-center text-muted-foreground transition-colors ${className ?? ""}`}
        aria-label="Email"
      >
        <Mail className="size-4" />
      </button>
    );
  }
  // icon-phone (default for icon)
  return (
    <button
      type="button"
      className={`size-8 rounded-md bg-secondary hover:bg-primary/10 hover:text-primary grid place-items-center text-muted-foreground transition-colors ${className ?? ""}`}
      aria-label="Call"
    >
      <Phone className="size-4" />
    </button>
  );
}
