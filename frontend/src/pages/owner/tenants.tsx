import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ChevronRight, Users } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { useUserByAuth } from "@/hooks/use-user-by-auth";
import { Card, CardContent } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { PageHeader } from "@/components/layout/page-header";
import { ContactPersonPopover } from "@/components/common/contact-person-popover";
import { formatDate, formatINR, initials } from "@/lib/utils";
import type { FlatResponseDTO } from "@/types/api";

export function TenantsPage() {
  const { authUserId } = useAuthStore();

  const buildingsQ = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const flatsQ = useQuery({
    queryKey: ["owner-all-flats", buildingsQ.data?.map((b) => b.buildingId).join(",")],
    queryFn: async () => {
      const buildings = buildingsQ.data ?? [];
      const all = await Promise.all(
        buildings.map((b) =>
          propertiesApi.flats
            .byBuilding(b.buildingId)
            .then((flats) =>
              flats.map((f) => ({ ...f, _buildingName: b.buildingName })),
            ),
        ),
      );
      return all.flat();
    },
    enabled: !!buildingsQ.data,
  });

  const tenantedFlats = (flatsQ.data ?? []).filter((f) => f.tenantId);

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Tenants"
        description="People living in your homes. Click any card to see their full activity."
      />

      {flatsQ.isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-44 rounded-2xl" />
          ))}
        </div>
      )}

      {!flatsQ.isLoading && tenantedFlats.length === 0 && (
        <Card className="p-12 text-center">
          <Users className="size-10 mx-auto text-muted-foreground" />
          <p className="font-display font-semibold text-lg mt-3">
            No tenants yet.
          </p>
          <p className="text-muted-foreground text-sm mt-1">
            Once you assign tenants to flats, they'll show up here.
          </p>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tenantedFlats.map((f) => (
          <TenantCard
            key={f.id}
            flat={f as FlatResponseDTO & { _buildingName?: string }}
          />
        ))}
      </div>
    </div>
  );
}

/**
 * One tenant tile. The whole card is a link to /owner/tenants/:tenantId
 * EXCEPT the contact-icon row at the bottom, which sits outside the link
 * so opening the popover doesn't trigger navigation.
 */
function TenantCard({
  flat,
}: {
  flat: FlatResponseDTO & { _buildingName?: string };
}) {
  const tenantId = flat.tenantId!;
  const { user, fullName, isLoading } = useUserByAuth(tenantId);

  return (
    <Card className="overflow-hidden">
      {/* Clickable header / stats area */}
      <Link
        to={`/owner/tenants/${tenantId}`}
        className="block p-5 hover:bg-secondary/40 transition-colors"
      >
        <div className="flex items-center gap-3">
          <Avatar className="size-12">
            {user?.profilePictureUrl && (
              <AvatarImage src={user.profilePictureUrl} />
            )}
            <AvatarFallback>
              {initials(fullName ?? tenantId.slice(0, 2))}
            </AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <p className="font-semibold truncate">
              {isLoading ? (
                <span className="inline-block h-4 w-28 rounded bg-secondary animate-pulse align-middle" />
              ) : (
                fullName ?? `Tenant ${tenantId.slice(0, 8)}…`
              )}
            </p>
            <p className="text-xs text-muted-foreground truncate">
              {flat._buildingName ?? "Building"} · {flat.flatNumber}
            </p>
          </div>
          <ChevronRight className="size-4 text-muted-foreground shrink-0" />
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
          <div>
            <p className="text-muted-foreground">Rent</p>
            <p className="font-semibold mt-0.5">
              {formatINR(flat.rentAmount)}
            </p>
          </div>
          <div>
            <p className="text-muted-foreground">Lease ends</p>
            <p className="font-semibold mt-0.5">
              {formatDate(flat.leaseEndDate) ?? "—"}
            </p>
          </div>
        </div>
      </Link>

      {/* Action bar — outside the Link so dropdowns don't navigate */}
      <CardContent className="px-5 pb-5 pt-0 flex items-center gap-2">
        <ContactPersonPopover authUserId={tenantId} variant="icon-mail" />
        <ContactPersonPopover authUserId={tenantId} variant="icon-phone" />
        <Badge variant="success" className="ml-auto">
          Active
        </Badge>
      </CardContent>
    </Card>
  );
}
