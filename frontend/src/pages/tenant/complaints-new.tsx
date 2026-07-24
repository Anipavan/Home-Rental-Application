import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Info, Loader2 } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { complaintsApi, maintenanceApi } from "@/lib/api/maintenance";
import { propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FileUpload, FilePreview } from "@/components/ui/file-upload";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type {
  ComplaintCategory,
  MaintenanceCategory,
  MaintenancePriority,
} from "@/types/api";

/**
 * Unified category taxonomy for the merged complaints form. Each
 * entry names an option in the picker plus which backend kind it
 * routes to. The frontend picks the API surface (maintenanceApi vs
 * complaintsApi) from {@code kind} — the backend still stores both
 * as MaintenanceRequest rows discriminated by the enum.
 *
 * Keep enum values in sync with backend {@code Kind} and the two
 * category enums; adding a value here without the corresponding
 * backend constant returns 400 on submit.
 */
type Category =
  | {
      kind: "MAINTENANCE";
      value: MaintenanceCategory;
      label: string;
      help: string;
    }
  | {
      kind: "COMPLAINT";
      value: ComplaintCategory;
      label: string;
      help: string;
    };

const CATEGORIES: Category[] = [
  // Physical-repair categories — used to be the maintenance form.
  { kind: "MAINTENANCE", value: "PLUMBING", label: "Plumbing", help: "Leaks, blocked drains, taps, toilets." },
  { kind: "MAINTENANCE", value: "ELECTRICAL", label: "Electrical", help: "Wiring, switches, fans, geysers." },
  { kind: "MAINTENANCE", value: "APPLIANCE", label: "Appliance", help: "Fridge, washing machine, AC, oven." },
  { kind: "MAINTENANCE", value: "PAINTING", label: "Painting", help: "Wall damage, peeling paint, damp patches." },
  { kind: "MAINTENANCE", value: "CLEANING", label: "Cleaning", help: "Deep-clean requests, garbage build-up." },
  { kind: "MAINTENANCE", value: "PEST_CONTROL", label: "Pest control", help: "Rodents, cockroaches, termites, bed bugs." },
  { kind: "MAINTENANCE", value: "GENERAL", label: "General repair", help: "Anything else needing a technician." },
  // Grievance categories — used to be the complaints form.
  { kind: "COMPLAINT", value: "NOISE", label: "Noise", help: "Loud neighbours, late-night noise, construction." },
  { kind: "COMPLAINT", value: "NEIGHBOR_DISPUTE", label: "Neighbour dispute", help: "Non-noise issues with another tenant." },
  { kind: "COMPLAINT", value: "SECURITY_CONCERN", label: "Security concern", help: "Broken locks, gate failures, suspicious activity." },
  { kind: "COMPLAINT", value: "OWNER_BEHAVIOR", label: "Owner behaviour", help: "Unannounced visits, harassment, privacy issues. Routed to admin." },
  { kind: "COMPLAINT", value: "BILLING_DISPUTE", label: "Billing dispute", help: "Disagreement over rent, deposits, or maintenance charges." },
  { kind: "COMPLAINT", value: "SAFETY_HAZARD", label: "Safety hazard", help: "Fire, gas leak, structural risk. Use HIGH/CRITICAL priority." },
  { kind: "COMPLAINT", value: "COMMON_AREA", label: "Common area", help: "Lift, garbage, cleanliness, parking." },
  { kind: "COMPLAINT", value: "LEASE_VIOLATION", label: "Lease violation", help: "Owner breaking terms of the lease." },
  { kind: "COMPLAINT", value: "OTHER", label: "Other", help: "Anything that doesn't fit the categories above." },
];

// Encode/decode "kind:value" as the SelectItem value so a single
// controlled state covers both taxonomies without discriminator gymnastics.
const encode = (c: Category) => `${c.kind}:${c.value}`;
const decode = (s: string) => {
  const c = CATEGORIES.find((x) => encode(x) === s);
  return c ?? CATEGORIES[0];
};

interface StagedPhoto {
  file: File;
  url: string;
}

export function ComplaintsNewPage() {
  const { authUserId } = useAuthStore();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const flatsQ = useQuery({
    queryKey: ["my-flats", authUserId],
    queryFn: () => propertiesApi.flats.byTenant(authUserId!),
    enabled: !!authUserId,
  });
  const flat = flatsQ.data?.[0];
  // FlatResponseDTO doesn't carry ownerId — only the parent Building
  // does. Resolve it so the backend event carries an ownerId and the
  // notification-service can bell the owner about the new item.
  const buildingQ = useQuery({
    queryKey: ["building", flat?.buildingId],
    queryFn: () => propertiesApi.buildings.get(flat!.buildingId),
    enabled: !!flat?.buildingId,
  });

  const [categoryKey, setCategoryKey] = useState<string>(encode(CATEGORIES[0]));
  const category = useMemo(() => decode(categoryKey), [categoryKey]);
  const [priority, setPriority] = useState<MaintenancePriority>("MEDIUM");
  const [photos, setPhotos] = useState<StagedPhoto[]>([]);
  const [uploadingPhotos, setUploadingPhotos] = useState(false);

  const maintenanceCats = CATEGORIES.filter((c) => c.kind === "MAINTENANCE");
  const complaintCats = CATEGORIES.filter((c) => c.kind === "COMPLAINT");

  const mutation = useMutation({
    // Route to the right API based on which kind the category belongs
    // to. Both APIs share a wire shape — only the discriminator field
    // (category vs complaintCategory) differs.
    mutationFn: (body: {
      flatId: string;
      tenantId: string;
      ownerId?: string;
      title: string;
      description: string;
      priority: MaintenancePriority;
    }) => {
      if (category.kind === "MAINTENANCE") {
        return maintenanceApi.create({
          ...body,
          category: category.value as MaintenanceCategory,
        });
      }
      return complaintsApi.create({
        ...body,
        complaintCategory: category.value as ComplaintCategory,
      });
    },
    onSuccess: async (data) => {
      const api = category.kind === "MAINTENANCE" ? maintenanceApi : complaintsApi;
      if (photos.length > 0) {
        setUploadingPhotos(true);
        try {
          for (const p of photos) {
            await api.uploadImage(data.id, p.file);
          }
        } catch (e) {
          toast({
            variant: "destructive",
            title: "Some photos didn't upload",
            description: extractErrorMessage(e),
          });
        } finally {
          setUploadingPhotos(false);
        }
      }
      // Invalidate BOTH caches — the merged list reads them both.
      qc.invalidateQueries({ queryKey: ["my-complaints"] });
      qc.invalidateQueries({ queryKey: ["my-maintenance"] });
      toast({
        title:
          category.kind === "MAINTENANCE"
            ? "Request submitted"
            : "Complaint filed",
        description:
          "We've notified the right person. You'll get a bell entry on every reply.",
      });
      navigate(`/app/complaints/${data.id}`);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't submit",
        description: extractErrorMessage(e),
      }),
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!flat || !authUserId) return;
    const fd = new FormData(e.currentTarget);
    mutation.mutate({
      flatId: flat.id,
      tenantId: authUserId,
      ownerId: buildingQ.data?.ownerId ?? undefined,
      title: String(fd.get("title") ?? ""),
      description: String(fd.get("description") ?? ""),
      priority,
    });
  }

  function addPhotos(files: File[]) {
    const next = files.map((f) => ({ file: f, url: URL.createObjectURL(f) }));
    setPhotos((prev) => [...prev, ...next].slice(0, 6));
  }

  function removePhoto(idx: number) {
    setPhotos((prev) => {
      const next = [...prev];
      const [removed] = next.splice(idx, 1);
      if (removed) URL.revokeObjectURL(removed.url);
      return next;
    });
  }

  const submitting = mutation.isPending || uploadingPhotos;
  const submitLabel =
    category.kind === "MAINTENANCE" ? "Submit request" : "File complaint";

  return (
    <div className="animate-fade-in max-w-2xl">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/app/complaints">
          <ArrowLeft /> Back
        </Link>
      </Button>
      <PageHeader
        title="New complaint"
        description="Tell us what's wrong — a broken tap, a loud neighbour, a safety issue. We'll route it to the right person and keep a record."
      />

      <Card>
        <CardContent className="p-6 sm:p-8">
          <form onSubmit={onSubmit} className="space-y-5">
            <div>
              <Label htmlFor="title">Headline</Label>
              <Input
                id="title"
                name="title"
                required
                maxLength={120}
                placeholder={
                  category.kind === "MAINTENANCE"
                    ? "e.g. Leaking tap in master bathroom"
                    : "e.g. Loud parties on weekends from Flat 3B"
                }
                className="mt-1.5"
              />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <Label>Category</Label>
                <Select
                  value={categoryKey}
                  onValueChange={(v) => setCategoryKey(v)}
                >
                  <SelectTrigger className="mt-1.5">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectLabel>Repairs</SelectLabel>
                      {maintenanceCats.map((c) => (
                        <SelectItem key={encode(c)} value={encode(c)}>
                          {c.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                    <SelectGroup>
                      <SelectLabel>Complaints</SelectLabel>
                      {complaintCats.map((c) => (
                        <SelectItem key={encode(c)} value={encode(c)}>
                          {c.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
                {category.help && (
                  <p className="text-xs text-muted-foreground mt-1.5 flex items-start gap-1.5">
                    <Info className="size-3.5 mt-0.5 shrink-0" />
                    <span>{category.help}</span>
                  </p>
                )}
              </div>
              <div>
                <Label>Urgency</Label>
                <Select
                  value={priority}
                  onValueChange={(v) => setPriority(v as MaintenancePriority)}
                >
                  <SelectTrigger className="mt-1.5">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="LOW">Low — when convenient</SelectItem>
                    <SelectItem value="MEDIUM">Medium — within a few days</SelectItem>
                    <SelectItem value="HIGH">High — urgent</SelectItem>
                    <SelectItem value="CRITICAL">
                      Critical — safety / emergency
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div>
              <Label htmlFor="description">
                {category.kind === "MAINTENANCE"
                  ? "Describe the issue"
                  : "What happened?"}
              </Label>
              <Textarea
                id="description"
                name="description"
                required
                minLength={10}
                rows={5}
                placeholder={
                  category.kind === "MAINTENANCE"
                    ? "When did it start? Any sounds, leaks, smells? The more we know, the faster we fix."
                    : "When did it start? Who is involved? What have you already tried?"
                }
                className="mt-1.5"
              />
              <p className="text-xs text-muted-foreground mt-1.5">
                Be specific. Dates, times, and names help us act faster.
              </p>
            </div>
            <div>
              <Label>
                {category.kind === "MAINTENANCE"
                  ? "Photos (optional)"
                  : "Evidence (optional)"}
              </Label>
              <p className="text-xs text-muted-foreground mb-2">
                Up to 6 files. JPG/PNG, max 5 MB each.
              </p>
              <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
                {photos.map((p, i) => (
                  <FilePreview
                    key={p.url}
                    url={p.url}
                    name={p.file.name}
                    onRemove={() => removePhoto(i)}
                  />
                ))}
                {photos.length < 6 && (
                  <FileUpload
                    accept="image/*"
                    multiple
                    maxSizeMB={5}
                    onFiles={(files) => addPhotos(files)}
                    className="aspect-square"
                  />
                )}
              </div>
            </div>

            {category.kind === "COMPLAINT" &&
              category.value === "OWNER_BEHAVIOR" && (
                <div className="rounded-lg border border-warning/30 bg-warning/5 p-3 text-xs text-muted-foreground">
                  <p className="font-medium text-foreground mb-1">
                    Owner-behaviour complaints go straight to admin.
                  </p>
                  Your owner is not notified. Our team will reach out
                  privately within 1 business day.
                </div>
              )}

            <div className="flex justify-end gap-2 pt-2">
              <Button asChild variant="ghost">
                <Link to="/app/complaints">Cancel</Link>
              </Button>
              <Button
                type="submit"
                variant="gradient"
                disabled={submitting || !flat}
              >
                {submitting && <Loader2 className="animate-spin" />}
                {uploadingPhotos
                  ? category.kind === "MAINTENANCE"
                    ? "Uploading photos…"
                    : "Uploading evidence…"
                  : submitLabel}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
