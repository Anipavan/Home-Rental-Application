import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  FileText,
  Check,
  ShieldCheck,
  Pencil,
  Save,
  X,
  Download,
  Loader2,
  CheckCircle2,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { usersApi } from "@/lib/api/users";
import { documentsApi } from "@/lib/api/documents";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FileUpload } from "@/components/ui/file-upload";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { initials } from "@/lib/utils";
import type { UserRequestDto, UserResponseDto } from "@/types/api";

/**
 * Tenant Profile page (stabilization rebuild).
 *
 * What changed vs. the prior version:
 *  - **Edit / Save / Cancel toggle** — fields are read-only by default; click
 *    "Edit" to switch to inputs. Stops the "I clicked the wrong thing and now
 *    my profile changed" failure mode.
 *  - **Document Service** for both ID proof and profile pic — no more local
 *    user-service upload directory. AADHAAR / PAN documents auto-OCR via
 *    Document Service which round-trips back to User Service to auto-fill
 *    the profile (see `DocumentEventListener` in user-service).
 *  - **AvatarImage** uses a pre-signed URL refreshed at upload time. URL
 *    expires after 15 min; on next page load, if `profilePictureUrl` is
 *    stale, we silently fall back to initials. A follow-up will introduce
 *    a thumbnail-resolver helper for permanent display.
 */
export function ProfilePage() {
  const { authUserId, userName, role } = useAuthStore();
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);
  // Local copy of editable form state. Initialised from `q.data` and reset
  // whenever the user enters edit mode or successfully saves.
  const [draft, setDraft] = useState<Partial<UserResponseDto>>({});

  const q = useQuery({
    queryKey: ["me", authUserId],
    queryFn: () => usersApi.byAuthId(authUserId!),
    enabled: !!authUserId,
    // Don't loop on a 404 — we surface the "Complete your profile" form instead.
    retry: (failureCount, err) => {
      const status = (err as { response?: { status?: number } })?.response?.status;
      return status !== 404 && failureCount < 2;
    },
  });

  // No user-service profile exists yet for this authUserId. The previous
  // version only triggered on a strict 404 — but in practice the lookup
  // can also surface as a 500 (Feign timeout in the gateway), an empty
  // 200 response from a fallback handler, or even a network error during
  // a tunnel-mode demo. In every one of those cases the page used to
  // render with empty fields and no edit/save affordance, leaving the
  // user stuck. Now we surface the bootstrap form whenever the data
  // simply isn't there — `q.data` is undefined and we're done loading.
  // The user-service `UserRegisteredListener` self-heal still creates
  // profiles on its own from the user.registered event; this branch
  // is a belt-and-braces fallback.
  const profileMissing =
    !!authUserId && !q.isLoading && !q.data;

  // Bootstrap mutation — creates the user-service record. After success
  // the byAuthId query is invalidated and the regular profile flow takes over.
  const bootstrapM = useMutation({
    mutationFn: (body: UserRequestDto) => usersApi.create(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["me", authUserId] });
      toast({ title: "Profile created" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't create profile",
        description: extractErrorMessage(e),
      }),
  });

  // Keep the draft in sync with fetched profile when not editing.
  useEffect(() => {
    if (q.data && !editing) setDraft(q.data);
  }, [q.data, editing]);

  /* ── ID proof upload (Aadhaar / PAN) via Document Service ────────────── */
  const idUploadM = useMutation({
    mutationFn: ({ file, type }: { file: File; type: "AADHAAR" | "PAN" }) =>
      documentsApi.upload(String(q.data!.id), type, file),
    onSuccess: async (doc) => {
      // Save a presigned URL on the user record so the View link works.
      try {
        const url = await documentsApi.getDownloadUrl(doc.id);
        await usersApi.update(q.data!.id, {
          ...userToUpdateDto(q.data!),
          idProofUrl: url.url,
        });
      } catch {
        /* non-fatal — document is uploaded; URL refresh failed */
      }
      qc.invalidateQueries({ queryKey: ["me", authUserId] });
      // Trigger OCR — extracted fields will land in the user via Kafka
      // (DocumentEventListener auto-fills firstName/dob/address/etc.).
      documentsApi.extract(doc.id).catch(() => {});
      toast({
        title: "ID uploaded",
        description: "We're extracting details — refresh in a few seconds.",
      });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "ID upload failed",
        description: extractErrorMessage(e),
      }),
  });

  /* ── Profile pic upload via Document Service ─────────────────────────── */
  const photoUploadM = useMutation({
    mutationFn: async (file: File) => {
      // Guard: q.data may be undefined if the bootstrap form is still on
      // screen. Without this, `String(q.data!.id)` would post a row with
      // userId="undefined" — backend accepts it, but the upload trivially
      // succeeds against the wrong owner and looks like data corruption.
      if (!q.data?.id) {
        throw new Error("Complete your profile first, then upload a photo.");
      }
      return documentsApi.upload(String(q.data.id), "PHOTO", file);
    },
    onSuccess: async (doc) => {
      // Stage 2: get a presigned download URL for the just-uploaded blob.
      // Stage 3: persist that URL on the user-service profile row, so
      // the header avatar + profile page render it without an extra
      // round-trip.
      //
      // Previously stages 2/3 were swallowed silently — the toast said
      // "Photo updated" even when the URL never landed on the user row,
      // so the avatar stayed as initials and the user thought the
      // upload had failed. Surface failures explicitly now so the user
      // can act on them (re-try, contact support).
      let stage = "presign";
      try {
        const url = await documentsApi.getDownloadUrl(doc.id);
        stage = "persist";
        if (q.data) {
          await usersApi.update(q.data.id, {
            ...userToUpdateDto(q.data),
            profilePictureUrl: url.url,
          });
        }
        qc.invalidateQueries({ queryKey: ["me", authUserId] });
        toast({ title: "Photo updated" });
      } catch (e) {
        // eslint-disable-next-line no-console
        console.warn(
          `[profile] photo uploaded (id=${doc.id}) but ${stage} step failed:`,
          extractErrorMessage(e),
        );
        // Still invalidate — the upload itself succeeded and the next
        // page load may pick up the new state somehow (e.g. an admin
        // tool fixed it server-side).
        qc.invalidateQueries({ queryKey: ["me", authUserId] });
        toast({
          variant: "destructive",
          title: "Photo upload incomplete",
          description:
            stage === "presign"
              ? "Uploaded the file, but couldn't generate a download URL for it. The avatar may not appear. Try refreshing the page or re-uploading."
              : "Uploaded the file, but couldn't save it to your profile. The avatar may not appear. Try refreshing the page or re-uploading.",
        });
      }
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't upload photo",
        description: extractErrorMessage(e),
      }),
  });

  /* ── Profile save ────────────────────────────────────────────────────── */
  const updateM = useMutation({
    mutationFn: (body: UserRequestDto) => usersApi.update(q.data!.id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["me", authUserId] });
      setEditing(false);
      toast({ title: "Profile saved" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't save",
        description: extractErrorMessage(e),
      }),
  });

  function onSave() {
    if (!q.data) return;
    updateM.mutate({
      authUserId: q.data.authUserId,
      firstName: draft.firstName ?? "",
      lastName: draft.lastName ?? "",
      email: draft.email ?? "",
      phone: draft.phone || undefined,
      dateOfBirth: draft.dateOfBirth || undefined,
      gender: draft.gender || undefined,
      address: draft.address || undefined,
      profilePictureUrl: q.data.profilePictureUrl,
      idProofUrl: q.data.idProofUrl,
    });
  }

  function onCancel() {
    if (q.data) setDraft(q.data);
    setEditing(false);
  }

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Profile"
        description="Your details, your way. Click Edit to change anything."
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
              accept="image/png,image/jpeg,image/jpg"
              maxSizeMB={5}
              loading={photoUploadM.isPending}
              // Disabled until the user-service record exists. Trying
              // to attach a photo to a non-existent profile is the
              // textbook "Unexpected error" path — guard at the source.
              disabled={!q.data?.id}
              onFiles={async (files) => {
                if (files[0]) await photoUploadM.mutateAsync(files[0]);
              }}
              className="absolute -bottom-1 -right-1"
            />
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-display text-xl font-semibold truncate">
              {q.data ? `${q.data.firstName} ${q.data.lastName}` : userName}
            </p>
            <p className="text-sm text-muted-foreground">{role}</p>
            {q.data?.email && (
              <p className="text-xs text-muted-foreground mt-0.5 truncate">
                {q.data.email}
              </p>
            )}
          </div>
          {q.data && !editing && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setEditing(true)}
            >
              <Pencil /> Edit
            </Button>
          )}
        </CardContent>
      </Card>

      {q.isLoading ? (
        <Skeleton className="h-64 rounded-2xl" />
      ) : profileMissing ? (
        // First-visit case: auth-service created the account but no
        // user-service record exists yet. Show a bootstrap form to capture
        // the missing fields, then refetch.
        <ProfileBootstrap
          authUserId={authUserId!}
          defaultName={userName ?? ""}
          pending={bootstrapM.isPending}
          onSubmit={(body) => bootstrapM.mutate(body)}
        />
      ) : (
        <Card className="mb-6">
          <CardContent className="p-6 sm:p-8">
            <div className="flex items-center justify-between mb-5">
              <h3 className="font-display font-semibold text-lg">
                Personal information
              </h3>
              {editing && (
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={onCancel}
                    disabled={updateM.isPending}
                  >
                    <X /> Cancel
                  </Button>
                  <Button
                    variant="gradient"
                    size="sm"
                    onClick={onSave}
                    disabled={updateM.isPending}
                  >
                    {updateM.isPending ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <Save />
                    )}
                    Save
                  </Button>
                </div>
              )}
            </div>

            {editing ? (
              <EditForm draft={draft} setDraft={setDraft} />
            ) : (
              <ViewGrid user={q.data} />
            )}
          </CardContent>
        </Card>
      )}

      {/*
        ID verification only makes sense once we have a user-service record —
        Document Service uploads need a real userId to attach to.
      */}
      {q.data && (
      <Card>
        <CardContent className="p-6 sm:p-8">
          <h3 className="font-display font-semibold text-lg flex items-center gap-2">
            <ShieldCheck className="size-4 text-primary" /> ID verification
          </h3>
          <p className="text-sm text-muted-foreground mt-1">
            Upload Aadhaar or PAN — encrypted at rest, OCR'd automatically.
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
                        <Check className="size-3" /> Uploaded
                      </Badge>
                    </p>
                    <a
                      href={q.data.idProofUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-xs text-primary hover:underline inline-flex items-center gap-1 mt-0.5"
                    >
                      <Download className="size-3" /> View document
                    </a>
                  </>
                ) : (
                  <>
                    <p className="font-medium text-sm">No ID uploaded yet</p>
                    <p className="text-xs text-muted-foreground">
                      PDF or image · up to 10 MB
                    </p>
                  </>
                )}
              </div>
            </div>
            <IdUploader
              pending={idUploadM.isPending}
              onUpload={(file, type) => idUploadM.mutateAsync({ file, type })}
            />
          </div>
        </CardContent>
      </Card>
      )}
    </div>
  );
}

/* ─────────────────────── Inner components ─────────────────────── */

function ViewGrid({ user }: { user?: UserResponseDto }) {
  if (!user) return null;
  const rows: { label: string; value: string }[] = [
    { label: "First name", value: user.firstName ?? "—" },
    { label: "Last name", value: user.lastName ?? "—" },
    { label: "Email", value: user.email ?? "—" },
    { label: "Phone", value: user.phone ?? "—" },
    { label: "Date of birth", value: user.dateOfBirth?.slice(0, 10) ?? "—" },
    { label: "Gender", value: user.gender ?? "—" },
    { label: "Address", value: user.address ?? "—" },
  ];
  return (
    <dl className="grid sm:grid-cols-2 gap-4">
      {rows.map((r) => (
        <div key={r.label}>
          <dt className="text-xs text-muted-foreground">{r.label}</dt>
          <dd className="font-medium mt-0.5">{r.value}</dd>
        </div>
      ))}
    </dl>
  );
}

function EditForm({
  draft,
  setDraft,
}: {
  draft: Partial<UserResponseDto>;
  setDraft: React.Dispatch<React.SetStateAction<Partial<UserResponseDto>>>;
}) {
  const set = (k: keyof UserResponseDto) => (v: string) =>
    setDraft((d) => ({ ...d, [k]: v }));
  return (
    <div className="space-y-5">
      <div className="grid sm:grid-cols-2 gap-4">
        <Field
          label="First name"
          value={draft.firstName ?? ""}
          onChange={set("firstName")}
        />
        <Field
          label="Last name"
          value={draft.lastName ?? ""}
          onChange={set("lastName")}
        />
        <Field
          label="Email"
          value={draft.email ?? ""}
          onChange={set("email")}
          type="email"
        />
        <Field
          label="Phone"
          value={draft.phone ?? ""}
          onChange={set("phone")}
          type="tel"
        />
        <Field
          label="Date of birth"
          value={draft.dateOfBirth?.slice(0, 10) ?? ""}
          onChange={set("dateOfBirth")}
          type="date"
        />
        <div>
          <Label>Gender</Label>
          <Select
            value={draft.gender ?? ""}
            onValueChange={set("gender")}
          >
            <SelectTrigger className="mt-1.5">
              <SelectValue placeholder="Select…" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="MALE">Male</SelectItem>
              <SelectItem value="FEMALE">Female</SelectItem>
              <SelectItem value="OTHER">Other</SelectItem>
              <SelectItem value="PREFER_NOT_TO_SAY">Prefer not to say</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      <Field
        label="Address"
        value={draft.address ?? ""}
        onChange={set("address")}
      />
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
}) {
  return (
    <div>
      <Label>{label}</Label>
      <Input
        className="mt-1.5"
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}

function IdUploader({
  pending,
  onUpload,
}: {
  pending: boolean;
  onUpload: (file: File, type: "AADHAAR" | "PAN") => Promise<unknown>;
}) {
  const [docType, setDocType] = useState<"AADHAAR" | "PAN">("AADHAAR");

  return (
    <div className="w-full sm:w-72 space-y-2">
      <Select
        value={docType}
        onValueChange={(v) => setDocType(v as "AADHAAR" | "PAN")}
      >
        <SelectTrigger>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="AADHAAR">Aadhaar card</SelectItem>
          <SelectItem value="PAN">PAN card</SelectItem>
        </SelectContent>
      </Select>
      <FileUpload
        accept="image/png,image/jpeg,image/jpg,application/pdf"
        maxSizeMB={10}
        loading={pending}
        onFiles={async (files) => {
          if (files[0]) await onUpload(files[0], docType);
        }}
        hint={`Uploading as ${docType}`}
      />
      <p className="text-[11px] text-muted-foreground inline-flex items-center gap-1">
        <CheckCircle2 className="size-3" />
        Aadhaar / PAN auto-extract details and update your profile.
      </p>
    </div>
  );
}

/** Strip a UserResponseDto down to the shape `usersApi.update` accepts. */
function userToUpdateDto(u: UserResponseDto): UserRequestDto {
  return {
    authUserId: u.authUserId,
    firstName: u.firstName,
    lastName: u.lastName,
    email: u.email,
    phone: u.phone,
    dateOfBirth: u.dateOfBirth,
    gender: u.gender,
    address: u.address,
  } as UserRequestDto;
}

/**
 * First-visit bootstrap: shown when the User Service has no record for this
 * authUserId. The auth-service register flow only creates an auth record;
 * the user-service record is created here on the user's first profile visit.
 *
 * <p>Backend follow-up planned for Day 8: a user-service Kafka listener on
 * `user.registered` so this bootstrap form is never needed in practice.
 * Until that ships, this form is the unblock.
 */
function ProfileBootstrap({
  authUserId,
  defaultName,
  pending,
  onSubmit,
}: {
  authUserId: string;
  defaultName: string;
  pending: boolean;
  onSubmit: (body: UserRequestDto) => void;
}) {
  // Best-effort split of the auth-service userName into first/last.
  const [firstGuess, ...rest] = defaultName.split(/\s+/);
  const lastGuess = rest.join(" ");

  return (
    <Card className="mb-6">
      <CardContent className="p-6 sm:p-8">
        <h3 className="font-display font-semibold text-lg">
          Complete your profile
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          Just a few details to finish setting up your account.
        </p>

        <form
          className="mt-5 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            const fd = new FormData(e.currentTarget);
            onSubmit({
              authUserId,
              firstName: String(fd.get("firstName") ?? "").trim(),
              lastName: String(fd.get("lastName") ?? "").trim(),
              email: String(fd.get("email") ?? "").trim(),
              phone: String(fd.get("phone") ?? "").trim() || undefined,
              dateOfBirth: String(fd.get("dateOfBirth") ?? "") || undefined,
              gender: (String(fd.get("gender") ?? "") || undefined) as
                | string
                | undefined,
              address: String(fd.get("address") ?? "") || undefined,
            });
          }}
        >
          <div className="grid sm:grid-cols-2 gap-4">
            <div>
              <Label htmlFor="firstName">First name</Label>
              <Input
                id="firstName"
                name="firstName"
                required
                defaultValue={firstGuess ?? ""}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="lastName">Last name</Label>
              <Input
                id="lastName"
                name="lastName"
                defaultValue={lastGuess}
                className="mt-1.5"
              />
            </div>
          </div>
          <div>
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              required
              className="mt-1.5"
            />
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <div>
              <Label htmlFor="phone">Phone</Label>
              <Input
                id="phone"
                name="phone"
                type="tel"
                className="mt-1.5"
                placeholder="+91 …"
              />
            </div>
            <div>
              <Label htmlFor="dateOfBirth">Date of birth</Label>
              <Input
                id="dateOfBirth"
                name="dateOfBirth"
                type="date"
                className="mt-1.5"
              />
            </div>
          </div>
          <div>
            <Label htmlFor="address">Address</Label>
            <Input
              id="address"
              name="address"
              className="mt-1.5"
            />
          </div>
          <div>
            <Label htmlFor="gender">Gender</Label>
            <select
              id="gender"
              name="gender"
              className="mt-1.5 flex h-10 w-full rounded-lg border bg-background px-3 text-sm"
              defaultValue=""
            >
              <option value="">Prefer not to say</option>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
          <div className="pt-2 flex justify-end">
            <Button type="submit" variant="gradient" disabled={pending}>
              {pending ? <Loader2 className="size-4 animate-spin" /> : null}
              Save profile
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

