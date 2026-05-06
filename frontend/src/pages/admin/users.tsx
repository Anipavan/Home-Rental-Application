import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, ShieldCheck, Home, Building2 } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "@/components/ui/tabs";
import { formatDate, initials } from "@/lib/utils";
import type { AuthUserResponse, Role } from "@/types/api";

export function AdminUsersPage() {
  const [q, setQ] = useState("");

  const tenantsQ = useQuery({
    queryKey: ["admin", "users-tenant"],
    queryFn: () => authApi.byRole("TENANT"),
  });
  const ownersQ = useQuery({
    queryKey: ["admin", "users-owner"],
    queryFn: () => authApi.byRole("OWNER"),
  });
  const adminsQ = useQuery({
    queryKey: ["admin", "users-admin"],
    queryFn: () => authApi.byRole("ADMIN"),
  });

  const all: AuthUserResponse[] = useMemo(
    () => [
      ...(tenantsQ.data ?? []),
      ...(ownersQ.data ?? []),
      ...(adminsQ.data ?? []),
    ],
    [tenantsQ.data, ownersQ.data, adminsQ.data],
  );

  const filter = (list: AuthUserResponse[]) =>
    !q
      ? list
      : list.filter(
          (u) =>
            u.userName.toLowerCase().includes(q.toLowerCase()) ||
            u.email?.toLowerCase().includes(q.toLowerCase()),
        );

  const loading =
    tenantsQ.isLoading || ownersQ.isLoading || adminsQ.isLoading;

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Users"
        description="Everyone with an account on Hearth."
      />

      <Card className="p-3 mb-5">
        <div className="relative">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search by username or email"
            className="pl-10"
          />
        </div>
      </Card>

      <Tabs defaultValue="all">
        <TabsList>
          <TabsTrigger value="all">All ({all.length})</TabsTrigger>
          <TabsTrigger value="tenant">Tenants ({tenantsQ.data?.length ?? 0})</TabsTrigger>
          <TabsTrigger value="owner">Owners ({ownersQ.data?.length ?? 0})</TabsTrigger>
          <TabsTrigger value="admin">Admins ({adminsQ.data?.length ?? 0})</TabsTrigger>
        </TabsList>
        <TabsContent value="all">
          <UserTable users={filter(all)} loading={loading} />
        </TabsContent>
        <TabsContent value="tenant">
          <UserTable users={filter(tenantsQ.data ?? [])} loading={loading} />
        </TabsContent>
        <TabsContent value="owner">
          <UserTable users={filter(ownersQ.data ?? [])} loading={loading} />
        </TabsContent>
        <TabsContent value="admin">
          <UserTable users={filter(adminsQ.data ?? [])} loading={loading} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function UserTable({
  users,
  loading,
}: {
  users: AuthUserResponse[];
  loading?: boolean;
}) {
  if (loading) {
    return (
      <Card className="p-3 space-y-2">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="h-14" />
        ))}
      </Card>
    );
  }
  if (users.length === 0) {
    return (
      <Card className="p-12 text-center text-muted-foreground">
        No users match.
      </Card>
    );
  }
  return (
    <Card>
      <div className="hidden sm:grid grid-cols-[1.4fr_1.6fr_120px_120px_100px] gap-3 px-5 py-3 text-xs uppercase tracking-wider text-muted-foreground border-b">
        <span>User</span>
        <span>Email</span>
        <span>Role</span>
        <span>Joined</span>
        <span>Status</span>
      </div>
      <div className="divide-y">
        {users.map((u) => (
          <div
            key={u.id}
            className="grid grid-cols-2 sm:grid-cols-[1.4fr_1.6fr_120px_120px_100px] gap-3 px-5 py-3.5 text-sm items-center"
          >
            <div className="flex items-center gap-3 min-w-0">
              <Avatar className="size-9">
                <AvatarFallback>{initials(u.userName)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0">
                <p className="font-medium truncate">{u.userName}</p>
                <p className="text-xs text-muted-foreground font-mono truncate">
                  {u.id}
                </p>
              </div>
            </div>
            <span className="text-muted-foreground truncate hidden sm:block">
              {u.email ?? "—"}
            </span>
            <RoleBadge role={u.role} />
            <span className="text-muted-foreground hidden sm:block">
              {formatDate(u.createdAt)}
            </span>
            <Badge variant={u.isActive === false ? "secondary" : "success"}>
              {u.isActive === false ? "Disabled" : "Active"}
            </Badge>
          </div>
        ))}
      </div>
    </Card>
  );
}

function RoleBadge({ role }: { role: Role }) {
  if (role === "ADMIN")
    return (
      <Badge>
        <ShieldCheck className="size-3" /> Admin
      </Badge>
    );
  if (role === "OWNER")
    return (
      <Badge variant="secondary">
        <Building2 className="size-3" /> Owner
      </Badge>
    );
  return (
    <Badge variant="secondary">
      <Home className="size-3" /> Tenant
    </Badge>
  );
}
