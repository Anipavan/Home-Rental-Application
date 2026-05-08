import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Building2,
  Loader2,
  Search,
  User,
  X,
  Home,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { usersApi } from "@/lib/api/users";
import type {
  BuildingResponseDTO,
  Role,
  UserResponseDto,
} from "@/types/api";
import { cn } from "@/lib/utils";

/**
 * Cross-entity global search bar for the AppShell.
 *
 * Fans out to two endpoints in parallel — properties.buildings.search and
 * users.search — debounced at 250 ms. Results are grouped into sections;
 * each row is a router Link to the appropriate detail page.
 *
 * Role-aware:
 *   OWNER  → properties (own only) + tenants (own only via /users/role/TENANT)
 *   ADMIN  → all properties + all users
 *   TENANT → no global search (returns null; the input area is hidden)
 *
 * Keyboard:
 *   ⌘K / Ctrl-K    focus
 *   Esc            close the dropdown
 *   ↑ / ↓ / Enter  navigate (basic — full a11y left for follow-up)
 */
export function GlobalSearch() {
  const { role, authUserId } = useAuthStore();
  const [raw, setRaw] = useState("");
  const [debounced, setDebounced] = useState("");
  const [open, setOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  // Debounce the typed query so we don't spam the backend on every keystroke.
  useEffect(() => {
    const t = setTimeout(() => setDebounced(raw.trim()), 250);
    return () => clearTimeout(t);
  }, [raw]);

  // Cmd-K / Ctrl-K to focus.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        inputRef.current?.focus();
      } else if (e.key === "Escape") {
        setOpen(false);
        inputRef.current?.blur();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  // Close when clicking outside.
  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  // Tenants don't get global search yet.
  if (role === "TENANT" || !role) return null;

  const enabled = debounced.length >= 2;
  // Owner-scoped property search; admins see everything.
  const ownerScope =
    role === "OWNER" && authUserId ? authUserId : undefined;

  const buildingsQ = useQuery({
    queryKey: ["search-buildings", debounced, ownerScope],
    queryFn: () => propertiesApi.buildings.search(debounced, ownerScope, 6),
    enabled,
    staleTime: 30_000,
  });

  const usersQ = useQuery({
    queryKey: ["search-users", debounced],
    queryFn: () => usersApi.search(debounced),
    enabled,
    staleTime: 30_000,
  });

  const buildings: BuildingResponseDTO[] = buildingsQ.data ?? [];
  const users: UserResponseDto[] = (usersQ.data ?? []).slice(0, 6);

  const isLoading = buildingsQ.isFetching || usersQ.isFetching;
  const noHits =
    enabled && !isLoading && buildings.length === 0 && users.length === 0;

  return (
    <div ref={containerRef} className="relative w-full">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground pointer-events-none" />
      <Input
        ref={inputRef}
        value={raw}
        onChange={(e) => setRaw(e.target.value)}
        onFocus={() => setOpen(true)}
        placeholder="Search homes, tenants…  (⌘K)"
        className="pl-10 pr-9 bg-secondary/60 border-transparent"
      />
      {raw.length > 0 && (
        <button
          type="button"
          onClick={() => {
            setRaw("");
            inputRef.current?.focus();
          }}
          aria-label="Clear search"
          className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded hover:bg-muted text-muted-foreground"
        >
          <X className="size-3.5" />
        </button>
      )}

      {open && enabled && (
        <div className="absolute left-0 right-0 mt-2 rounded-xl border border-border bg-popover shadow-lg overflow-hidden z-50">
          {isLoading && (
            <div className="px-4 py-6 text-sm text-muted-foreground flex items-center gap-2">
              <Loader2 className="size-4 animate-spin" />
              Searching…
            </div>
          )}

          {!isLoading && noHits && (
            <div className="px-4 py-6 text-sm text-muted-foreground text-center">
              Nothing matches "{debounced}"
            </div>
          )}

          {!isLoading && buildings.length > 0 && (
            <Section title="Properties">
              {buildings.map((b) => (
                <ResultRow
                  key={b.buildingId}
                  icon={Building2}
                  title={b.buildingName ?? "Unnamed building"}
                  subtitle={[b.buildingAddress, b.buildingCity, b.buildingState]
                    .filter(Boolean)
                    .join(", ")}
                  to={
                    role === "OWNER"
                      ? `/owner/buildings/${b.buildingId}`
                      : `/admin/properties`
                  }
                  onSelect={() => {
                    setOpen(false);
                  }}
                />
              ))}
            </Section>
          )}

          {!isLoading && users.length > 0 && (
            <Section title={role === "OWNER" ? "Tenants" : "Users"}>
              {users.map((u) => (
                <ResultRow
                  key={u.id}
                  icon={u.role === "TENANT" ? Home : User}
                  title={`${u.firstName ?? ""} ${u.lastName ?? ""}`.trim() ||
                    u.email}
                  subtitle={[u.email, u.phone].filter(Boolean).join(" · ")}
                  to={detailLinkForUser(u, role)}
                  onSelect={() => setOpen(false)}
                />
              ))}
            </Section>
          )}

          {!isLoading && (buildings.length > 0 || users.length > 0) && (
            <button
              type="button"
              onClick={() => {
                setOpen(false);
                navigate(
                  role === "ADMIN"
                    ? `/admin/properties?q=${encodeURIComponent(debounced)}`
                    : `/owner/buildings?q=${encodeURIComponent(debounced)}`,
                );
              }}
              className="w-full text-left px-4 py-2.5 text-xs text-muted-foreground hover:bg-muted border-t border-border/60"
            >
              Press Enter to see all results for "{debounced}" →
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="border-b border-border/60 last:border-0">
      <p className="px-4 pt-2.5 pb-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
        {title}
      </p>
      <div className="pb-1">{children}</div>
    </div>
  );
}

function ResultRow({
  icon: Icon,
  title,
  subtitle,
  to,
  onSelect,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  subtitle?: string;
  to: string;
  onSelect?: () => void;
}) {
  return (
    <Link
      to={to}
      onClick={onSelect}
      className={cn(
        "flex items-center gap-3 px-4 py-2 hover:bg-muted transition-colors",
      )}
    >
      <span className="size-8 rounded-md bg-secondary grid place-items-center shrink-0">
        <Icon className="size-4 text-muted-foreground" />
      </span>
      <div className="min-w-0">
        <p className="text-sm font-medium truncate">{title}</p>
        {subtitle && (
          <p className="text-xs text-muted-foreground truncate">{subtitle}</p>
        )}
      </div>
    </Link>
  );
}

/**
 * Pick the right detail page for a user given the viewer's role.
 *  - ADMIN: /admin/users (single list for now)
 *  - OWNER + tenant result: /owner/tenants/{authUserId}
 *  - OWNER + non-tenant: /owner/tenants (no detail page for owners-of-owners)
 */
function detailLinkForUser(u: UserResponseDto, viewerRole: Role): string {
  if (viewerRole === "ADMIN") return `/admin/users`;
  if (viewerRole === "OWNER" && u.role === "TENANT" && u.authUserId) {
    return `/owner/tenants/${u.authUserId}`;
  }
  return `/owner/tenants`;
}
