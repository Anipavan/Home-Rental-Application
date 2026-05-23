import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Loader2, Search, UserPlus, Info } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { usersApi } from "@/lib/api/users";
import { visitRequestsApi } from "@/lib/api/notifications";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

/**
 * Owner-side dialog for assigning a tenant to a vacant flat.
 *
 * <p>Flow:
 * <ol>
 *   <li>Loads every TENANT-role user via {@link usersApi.byRole} on
 *       open. The endpoint now carries {@code authUserId} on every row,
 *       which is exactly what the assign API expects in
 *       {@code AssignFlatRequest.tenantId}.</li>
 *   <li>Owner picks a tenant + a lease window (defaults: today →
 *       today + 12 months).</li>
 *   <li>Submit posts to {@code POST /properties/flats/{id}/assign}
 *       which fires the {@code flat.occupied} Kafka event and lets the
 *       agreement-service create a {@code PENDING_SIGNATURE} agreement
 *       automatically.</li>
 *   <li>On success we invalidate every cached query that surfaces flat
 *       assignments — owner flats list, my-buildings, the tenant's
 *       my-flats, agreement lists — so the UI updates without a
 *       manual refresh.</li>
 * </ol>
 */
interface Props {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  flatId: string;
  flatNumber: string;
  buildingName?: string;
}

export function AssignTenantDialog({
  open,
  onOpenChange,
  flatId,
  flatNumber,
  buildingName,
}: Props) {
  const qc = useQueryClient();

  const [tenantAuthId, setTenantAuthId] = useState<string>("");
  const [tenantSearch, setTenantSearch] = useState<string>("");
  const [leaseStart, setLeaseStart] = useState<string>(toDateInput(new Date()));
  const [leaseEnd, setLeaseEnd] = useState<string>(
    toDateInput(addMonths(new Date(), 12)),
  );
  /**
   * "Show only visited tenants" toggle.
   *
   * <p>By default we filter the picker to tenants who have actually
   * raised a visit request for this specific flat — the business
   * justification: an owner shouldn't be able to assign a flat to a
   * random tenant who's never seen it. This also caps the list size:
   * even with 10,000 registered tenants on the platform, a typical
   * flat will see 5–20 visits, so the dropdown stays usable.
   *
   * <p>Owner can flip this off for the "we already chatted on WhatsApp"
   * edge case where assignment happens without a recorded visit.
   */
  const [visitedOnly, setVisitedOnly] = useState<boolean>(true);

  // Reset state every time the dialog opens, so a stale selection /
  // search from a previous flat doesn't leak in.
  useEffect(() => {
    if (open) {
      setTenantAuthId("");
      setTenantSearch("");
      setVisitedOnly(true);
      setLeaseStart(toDateInput(new Date()));
      setLeaseEnd(toDateInput(addMonths(new Date(), 12)));
    }
  }, [open]);

  /**
   * Visit requests submitted for THIS flat. Page-size 200 because we
   * expect the user count to be small per-flat (most visits come
   * from a few interested tenants); if a flat ever exceeds 200
   * visits we'd add pagination here, but for now one page is plenty.
   *
   * Filter to non-cancelled visits — we want PENDING, CONFIRMED, and
   * COMPLETED visits to qualify. Cancelled = the tenant changed their
   * mind and shouldn't appear in the picker.
   */
  const visitsQ = useQuery({
    queryKey: ["flat-visits", flatId],
    queryFn: () => visitRequestsApi.byFlat(flatId, 0, 200),
    enabled: open && visitedOnly,
    staleTime: 30_000,
  });

  /** Set of tenant authUserIds who have a non-cancelled visit on this flat. */
  const visitedTenantIds = useMemo(() => {
    const set = new Set<string>();
    for (const v of visitsQ.data?.content ?? []) {
      // status field shape: PENDING | CONFIRMED | COMPLETED | CANCELLED
      if (v.status === "CANCELLED") continue;
      if (v.userId) set.add(v.userId);
    }
    return set;
  }, [visitsQ.data]);

  const tenantsQ = useQuery({
    queryKey: ["users-by-role", "TENANT"],
    queryFn: () => usersApi.byRole("TENANT"),
    enabled: open,
    staleTime: 60_000,
  });

  // Fetch every active flat so we can hide tenants who are already
  // occupying one. A tenant can only hold one flat at a time — the
  // backend's FlatServiceImpul.assignFlat enforces this and throws
  // FlatOccupiedException on violations, but raising the error AFTER
  // the owner picks a tenant + clicks Assign is bad UX. Pre-filter
  // here so ineligible tenants never show up in the picker.
  //
  // Page size 500 matches the FlatController's bumped @Max cap;
  // sufficient for any city-scale deployment. For metropolitan-scale
  // we'd swap this for a dedicated /flats/occupied-tenant-ids
  // endpoint returning just String[].
  const allFlatsQ = useQuery({
    queryKey: ["all-flats-for-assignment", flatId],
    queryFn: () => propertiesApi.flats.list(0, 500),
    enabled: open,
    staleTime: 30_000,
  });

  // Build the set of authUserIds currently holding an active,
  // occupied flat. Exclude the CURRENT flat we're assigning to —
  // if it already has a tenant (re-assign flow), they'd otherwise
  // self-filter out and the dialog would look like "no tenants
  // available".
  const occupiedTenantIds = useMemo(() => {
    const set = new Set<string>();
    for (const f of allFlatsQ.data?.content ?? []) {
      if (f.id === flatId) continue; // don't filter against ourselves
      if (f.isOccupied && f.tenantId && f.tenantId.length > 0) {
        set.add(f.tenantId);
      }
    }
    return set;
  }, [allFlatsQ.data, flatId]);

  const tenants = useMemo(
    () =>
      (tenantsQ.data ?? []).filter(
        (t): t is typeof t & { authUserId: string } =>
          !!t.authUserId && t.authUserId.length > 0,
      ),
    [tenantsQ.data],
  );

  // Tenants minus the ones already assigned to another flat, and
  // (when `visitedOnly` is on) further filtered to tenants who have
  // visited THIS flat. The backend's FlatOccupiedException is a
  // defence-in-depth check for race conditions / direct API calls,
  // never a normal UX path.
  const eligibleTenants = useMemo(
    () =>
      tenants
        .filter((t) => !occupiedTenantIds.has(t.authUserId))
        .filter((t) =>
          // When the visited-only toggle is on, the tenant must also
          // appear in the visit-request set for THIS flat. When off,
          // every unassigned tenant is fair game.
          visitedOnly ? visitedTenantIds.has(t.authUserId) : true,
        ),
    [tenants, occupiedTenantIds, visitedTenantIds, visitedOnly],
  );
  const hiddenCount = tenants.length - eligibleTenants.length;

  /**
   * Case-insensitive filter across firstName + lastName + userName +
   * email. Triggered live as the owner types — for ~50 tenants this
   * is cheap. If the list ever grows past a few hundred we'd want to
   * debounce + paginate, but the backend already returns the full
   * set so client-side is fine for now.
   */
  const filteredTenants = useMemo(() => {
    const q = tenantSearch.trim().toLowerCase();
    if (!q) return eligibleTenants;
    return eligibleTenants.filter((t) => {
      const haystack = [
        t.firstName,
        t.lastName,
        t.userName,
        t.email,
        t.phone,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [eligibleTenants, tenantSearch]);

  const assignM = useMutation({
    mutationFn: () =>
      propertiesApi.flats.assign(flatId, {
        tenantId: tenantAuthId,
        leaseStartDate: leaseStart,
        leaseEndDate: leaseEnd,
      }),
    onSuccess: () => {
      // Owner-side caches that show flats / occupancy / tenants
      qc.invalidateQueries({ queryKey: ["owner-all-flats"] });
      qc.invalidateQueries({ queryKey: ["my-buildings"] });
      qc.invalidateQueries({ queryKey: ["building"] });
      qc.invalidateQueries({ queryKey: ["flats-by-building"] });
      qc.invalidateQueries({ queryKey: ["flat", flatId] });
      // Tenant-side cache so the gate lifts on the next visit
      qc.invalidateQueries({ queryKey: ["my-flats"] });
      // Agreements get auto-created server-side when a flat is assigned
      qc.invalidateQueries({ queryKey: ["agreements"] });
      qc.invalidateQueries({ queryKey: ["my-agreements"] });
      toast({
        title: "Tenant assigned",
        description:
          "A pending agreement has been created. The tenant can now sign in to review and sign.",
      });
      onOpenChange(false);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't assign",
        description: extractErrorMessage(e),
      }),
  });

  const valid =
    tenantAuthId.length > 0 &&
    !!leaseStart &&
    !!leaseEnd &&
    leaseStart < leaseEnd;

  const propertyLabel = buildingName
    ? `${buildingName} · Flat ${flatNumber}`
    : `Flat ${flatNumber}`;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UserPlus className="size-4 text-primary" /> Assign tenant
          </DialogTitle>
          <DialogDescription>
            Pick a tenant and a lease window for{" "}
            <span className="font-medium">{propertyLabel}</span>. A pending
            agreement is created automatically — the tenant signs from
            their app.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div>
            <div className="flex items-center justify-between">
              <Label htmlFor="tenantSearch">Tenant</Label>
              {/* Visited-only toggle. Default ON — only tenants who
                  actually visited THIS flat appear. Owner can flip off
                  for the "we agreed via WhatsApp without a recorded
                  visit" edge case. */}
              <label className="flex items-center gap-1.5 text-[11px] text-muted-foreground cursor-pointer select-none">
                <input
                  type="checkbox"
                  className="size-3 rounded accent-emerald-600"
                  checked={visitedOnly}
                  onChange={(e) => setVisitedOnly(e.target.checked)}
                />
                Only show tenants who visited this flat
              </label>
            </div>
            {tenantsQ.isLoading || allFlatsQ.isLoading || (visitedOnly && visitsQ.isLoading) ? (
              <div className="mt-1.5 text-sm text-muted-foreground flex items-center gap-2">
                <Loader2 className="size-4 animate-spin" />
                Loading tenants…
              </div>
            ) : tenants.length === 0 ? (
              <p className="mt-1.5 text-sm text-muted-foreground">
                No registered tenants yet — once someone signs up with the
                TENANT role, they'll show here.
              </p>
            ) : eligibleTenants.length === 0 ? (
              <div className="mt-1.5 rounded-lg border bg-secondary/30 p-3 text-sm">
                {visitedOnly ? (
                  <div className="flex items-start gap-2">
                    <Info className="size-4 text-muted-foreground mt-0.5 shrink-0" />
                    <div>
                      <p className="font-medium">
                        No one has visited this flat yet.
                      </p>
                      <p className="text-xs text-muted-foreground mt-1">
                        Tenants who book a visit via the public listing
                        page will appear here. To assign without a visit,
                        uncheck the toggle above.
                      </p>
                    </div>
                  </div>
                ) : (
                  <p className="text-muted-foreground">
                    Every registered tenant is already assigned to a flat.
                    Vacate an existing flat first, or wait for a new tenant
                    to sign up.
                  </p>
                )}
              </div>
            ) : (
              <>
                {/* Live search across name/userName/email/phone. */}
                <div className="relative mt-1.5">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                  <Input
                    id="tenantSearch"
                    type="search"
                    autoComplete="off"
                    placeholder="Search by name, username, email or phone…"
                    value={tenantSearch}
                    onChange={(e) => setTenantSearch(e.target.value)}
                    className="pl-9"
                  />
                </div>

                {/* Scrollable result list. Each row is a button so
                    keyboard nav works. The selected row gets the
                    primary-tinted background + check icon. */}
                <div className="mt-2 max-h-56 overflow-y-auto rounded-lg border">
                  {filteredTenants.length === 0 ? (
                    <p className="p-4 text-sm text-muted-foreground text-center">
                      No tenants match "{tenantSearch}".
                    </p>
                  ) : (
                    <ul className="divide-y">
                      {filteredTenants.map((t) => {
                        const selected = t.authUserId === tenantAuthId;
                        return (
                          <li key={t.authUserId}>
                            <button
                              type="button"
                              onClick={() => setTenantAuthId(t.authUserId)}
                              className={cn(
                                "w-full text-left px-3 py-2.5 text-sm flex items-center gap-2 hover:bg-secondary/60 transition-colors",
                                selected && "bg-primary/10",
                              )}
                            >
                              <div
                                className={cn(
                                  "size-4 shrink-0 grid place-items-center rounded-full border",
                                  selected
                                    ? "bg-primary text-primary-foreground border-primary"
                                    : "border-muted-foreground/30",
                                )}
                              >
                                {selected && <Check className="size-3" />}
                              </div>
                              <div className="flex-1 min-w-0">
                                <p className="font-medium truncate">
                                  {labelFor(t)}
                                </p>
                                {t.phone && (
                                  <p className="text-xs text-muted-foreground truncate">
                                    {t.phone}
                                  </p>
                                )}
                              </div>
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>

                <p className="mt-1 text-[11px] text-muted-foreground">
                  {tenantAuthId ? (
                    "1 tenant selected."
                  ) : (
                    <>
                      {filteredTenants.length} of {eligibleTenants.length}{" "}
                      eligible — tap a row to pick.
                      {hiddenCount > 0 && (
                        <>
                          {" "}
                          <span className="text-muted-foreground/70">
                            ({hiddenCount} hidden — already assigned to other
                            flats.)
                          </span>
                        </>
                      )}
                    </>
                  )}
                </p>
              </>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="leaseStart">Lease start</Label>
              <Input
                id="leaseStart"
                type="date"
                className="mt-1.5"
                value={leaseStart}
                onChange={(e) => setLeaseStart(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="leaseEnd">Lease end</Label>
              <Input
                id="leaseEnd"
                type="date"
                className="mt-1.5"
                value={leaseEnd}
                onChange={(e) => setLeaseEnd(e.target.value)}
              />
            </div>
          </div>
          {leaseStart && leaseEnd && leaseStart >= leaseEnd && (
            <p className="text-xs text-destructive">
              Lease end must be after lease start.
            </p>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={assignM.isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant="gradient"
            disabled={!valid || assignM.isPending}
            onClick={() => assignM.mutate()}
          >
            {assignM.isPending && (
              <Loader2 className="size-4 animate-spin" />
            )}
            <UserPlus /> Assign
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/* ───────────── helpers ───────────── */

function labelFor(t: {
  firstName?: string;
  lastName?: string;
  userName?: string;
  email?: string;
  authUserId?: string;
}) {
  const fullName = `${t.firstName ?? ""} ${t.lastName ?? ""}`.trim();
  const head = fullName || t.userName || t.email || `auth ${t.authUserId}`;
  const tail = t.email ?? t.userName ?? "";
  return tail && head !== tail ? `${head} · ${tail}` : head;
}

function toDateInput(d: Date): string {
  // YYYY-MM-DD in the local timezone (input[type="date"] expects this).
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function addMonths(d: Date, months: number): Date {
  const out = new Date(d);
  out.setMonth(out.getMonth() + months);
  return out;
}
