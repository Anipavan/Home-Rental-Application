import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Loader2, Search, UserPlus } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { usersApi } from "@/lib/api/users";
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

  // Reset state every time the dialog opens, so a stale selection /
  // search from a previous flat doesn't leak in.
  useEffect(() => {
    if (open) {
      setTenantAuthId("");
      setTenantSearch("");
      setLeaseStart(toDateInput(new Date()));
      setLeaseEnd(toDateInput(addMonths(new Date(), 12)));
    }
  }, [open]);

  const tenantsQ = useQuery({
    queryKey: ["users-by-role", "TENANT"],
    queryFn: () => usersApi.byRole("TENANT"),
    enabled: open,
    staleTime: 60_000,
  });

  const tenants = useMemo(
    () =>
      (tenantsQ.data ?? []).filter(
        (t): t is typeof t & { authUserId: string } =>
          !!t.authUserId && t.authUserId.length > 0,
      ),
    [tenantsQ.data],
  );

  /**
   * Case-insensitive filter across firstName + lastName + userName +
   * email. Triggered live as the owner types — for ~50 tenants this
   * is cheap. If the list ever grows past a few hundred we'd want to
   * debounce + paginate, but the backend already returns the full
   * set so client-side is fine for now.
   */
  const filteredTenants = useMemo(() => {
    const q = tenantSearch.trim().toLowerCase();
    if (!q) return tenants;
    return tenants.filter((t) => {
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
  }, [tenants, tenantSearch]);

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
            <Label htmlFor="tenantSearch">Tenant</Label>
            {tenantsQ.isLoading ? (
              <div className="mt-1.5 text-sm text-muted-foreground flex items-center gap-2">
                <Loader2 className="size-4 animate-spin" />
                Loading tenants…
              </div>
            ) : tenants.length === 0 ? (
              <p className="mt-1.5 text-sm text-muted-foreground">
                No registered tenants yet — once someone signs up with the
                TENANT role, they'll show here.
              </p>
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
                  {tenantAuthId
                    ? "1 tenant selected."
                    : `${filteredTenants.length} of ${tenants.length} matching — tap a row to pick.`}
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
