import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FileText, Check, ShieldCheck } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { usersApi } from "@/lib/api/users";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { FileUpload } from "@/components/ui/file-upload";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { initials } from "@/lib/utils";

export function ProfilePage() {
  const { authUserId, userName, role } = useAuthStore();
  const qc = useQueryClient();

  const q = useQuery({
    queryKey: ["me", authUserId],
    queryFn: () => usersApi.byAuthId(authUserId!),
    enabled: !!authUserId,
  });

  const uploadM = useMutation({
    mutationFn: ({ file, type }: { file: File; type: "PROFILE" | "ID_PROOF" }) =>
      usersApi.uploadDocument(q.data!.id, file, type),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["me", authUserId] });
      toast({ title: "Uploaded" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Upload failed",
        description: extractErrorMessage(e),
      }),
  });

  const updateM = useMutation({
    mutationFn: (body: import("@/types/api").UserRequestDto) =>
      usersApi.update(q.data!.id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["me", authUserId] });
      toast({ title: "Profile updated" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't save",
        description: extractErrorMessage(e),
      }),
  });

  function onSave(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!q.data) return;
    const fd = new FormData(e.currentTarget);
    updateM.mutate({
      authUserId: q.data.authUserId,
      firstName: String(fd.get("firstName") ?? ""),
      lastName: String(fd.get("lastName") ?? ""),
      email: String(fd.get("email") ?? ""),
      phone: String(fd.get("phone") ?? ""),
      dateOfBirth: String(fd.get("dateOfBirth") ?? "") || undefined,
      gender: String(fd.get("gender") ?? "") || undefined,
      address: String(fd.get("address") ?? "") || undefined,
    });
  }

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Profile"
        description="Your details, your way. Update anytime."
      />

      <Card className="mb-6">
        <CardContent className="p-6 flex items-center gap-5">
          <div className="relative">
            <Avatar className="size-20">
              {q.data?.profilePictureUrl && (
                <AvatarImage src={q.data.profilePictureUrl} />
              )}
              <AvatarFallback className="text-2xl">
                {initials(userName ?? "")}
              </AvatarFallback>
            </Avatar>
            <FileUpload
              variant="compact"
              accept="image/*"
              maxSizeMB={5}
              loading={uploadM.isPending && uploadM.variables?.type === "PROFILE"}
              onFiles={async (files) => {
                if (files[0])
                  await uploadM.mutateAsync({ file: files[0], type: "PROFILE" });
              }}
              className="absolute -bottom-1 -right-1"
            />
          </div>
          <div>
            <p className="font-display text-xl font-semibold">
              {q.data ? `${q.data.firstName} ${q.data.lastName}` : userName}
            </p>
            <p className="text-sm text-muted-foreground">{role}</p>
            {q.data?.email && (
              <p className="text-xs text-muted-foreground mt-0.5">
                {q.data.email}
              </p>
            )}
          </div>
        </CardContent>
      </Card>

      {q.isLoading ? (
        <Skeleton className="h-64 rounded-2xl" />
      ) : (
        <Card className="mb-6">
          <CardContent className="p-6 sm:p-8">
            <h3 className="font-display font-semibold text-lg mb-5">
              Personal information
            </h3>
            <form onSubmit={onSave} className="space-y-5">
              <div className="grid sm:grid-cols-2 gap-4">
                <Field label="First name" name="firstName" defaultValue={q.data?.firstName} />
                <Field label="Last name" name="lastName" defaultValue={q.data?.lastName} />
                <Field label="Email" name="email" defaultValue={q.data?.email} type="email" />
                <Field label="Phone" name="phone" defaultValue={q.data?.phone} type="tel" />
                <Field
                  label="Date of birth"
                  name="dateOfBirth"
                  defaultValue={q.data?.dateOfBirth?.slice(0, 10)}
                  type="date"
                />
                <Field label="Gender" name="gender" defaultValue={q.data?.gender} />
              </div>
              <Field label="Address" name="address" defaultValue={q.data?.address} />
              <div className="flex justify-end gap-2 pt-2">
                <Button type="reset" variant="ghost">
                  Reset
                </Button>
                <Button
                  type="submit"
                  variant="gradient"
                  disabled={updateM.isPending}
                >
                  Save changes
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardContent className="p-6 sm:p-8">
          <h3 className="font-display font-semibold text-lg flex items-center gap-2">
            <ShieldCheck className="size-4 text-primary" /> ID verification
          </h3>
          <p className="text-sm text-muted-foreground mt-1">
            Upload one valid government ID — Aadhaar, PAN, passport or
            driver's licence. Encrypted at rest.
          </p>
          <div className="mt-5 flex items-start gap-4 flex-wrap sm:flex-nowrap">
            <div className="rounded-xl border bg-secondary/30 p-4 flex items-center gap-3 flex-1 min-w-[260px]">
              <div className="size-12 rounded-lg bg-background grid place-items-center border">
                <FileText className="size-5 text-muted-foreground" />
              </div>
              <div className="flex-1 min-w-0">
                {q.data?.idProofUrl ? (
                  <>
                    <p className="font-medium text-sm flex items-center gap-1.5">
                      ID on file
                      <Badge variant="success" className="text-[10px]">
                        <Check className="size-3" /> Verified
                      </Badge>
                    </p>
                    <a
                      href={q.data.idProofUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-xs text-primary hover:underline"
                    >
                      View document
                    </a>
                  </>
                ) : (
                  <>
                    <p className="font-medium text-sm">No ID uploaded yet</p>
                    <p className="text-xs text-muted-foreground">
                      PDF or image · up to 5 MB
                    </p>
                  </>
                )}
              </div>
            </div>
            <FileUpload
              accept="image/*,application/pdf"
              maxSizeMB={5}
              loading={uploadM.isPending && uploadM.variables?.type === "ID_PROOF"}
              onFiles={async (files) => {
                if (files[0])
                  await uploadM.mutateAsync({ file: files[0], type: "ID_PROOF" });
              }}
              hint="Aadhaar / PAN / Passport"
              className="w-full sm:w-72"
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function Field({
  label,
  name,
  defaultValue,
  type = "text",
}: {
  label: string;
  name: string;
  defaultValue?: string;
  type?: string;
}) {
  return (
    <div>
      <Label htmlFor={name}>{label}</Label>
      <Input
        id={name}
        name={name}
        className="mt-1.5"
        defaultValue={defaultValue ?? ""}
        type={type}
      />
    </div>
  );
}

