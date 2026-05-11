import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Info, Loader2 } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { complaintsApi } from "@/lib/api/maintenance";
import { propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FileUpload, FilePreview } from "@/components/ui/file-upload";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type {
  ComplaintCategory,
  MaintenancePriority,
} from "@/types/api";

/**
 * Display-order list for the dropdown. Keep this aligned with
 * {@link ComplaintCategory} on the backend — adding a new value here
 * without adding the enum constant will produce a 400 on submit.
 */
const CATEGORIES: { value: ComplaintCategory; label: string; help: string }[] = [
  { value: "NOISE", label: "Noise", help: "Loud neighbours, late-night noise, construction." },
  { value: "NEIGHBOR_DISPUTE", label: "Neighbour dispute", help: "Non-noise issues with another tenant." },
  { value: "SECURITY_CONCERN", label: "Security concern", help: "Broken locks, gate failures, suspicious activity." },
  { value: "OWNER_BEHAVIOR", label: "Owner behaviour", help: "Unannounced visits, harassment, privacy issues. Routed to admin." },
  { value: "BILLING_DISPUTE", label: "Billing dispute", help: "Disagreement over rent, deposits, or maintenance charges." },
  { value: "SAFETY_HAZARD", label: "Safety hazard", help: "Fire, gas leak, structural risk. Use HIGH/CRITICAL priority." },
  { value: "COMMON_AREA", label: "Common area", help: "Lift, garbage, cleanliness, parking." },
  { value: "LEASE_VIOLATION", label: "Lease violation", help: "Owner breaking terms of the lease." },
  { value: "OTHER", label: "Other", help: "Anything that doesn't fit the categories above." },
];

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
  // does. Resolve it so we can hand the backend a stable owner reference
  // and the notification listener can ping the right inbox.
  const buildingQ = useQuery({
    queryKey: ["building", flat?.buildingId],
    queryFn: () => propertiesApi.buildings.get(flat!.buildingId),
    enabled: !!flat?.buildingId,
  });

  const [category, setCategory] = useState<ComplaintCategory>("NOISE");
  const [priority, setPriority] = useState<MaintenancePriority>("MEDIUM");
  const [photos, setPhotos] = useState<StagedPhoto[]>([]);
  const [uploadingPhotos, setUploadingPhotos] = useState(false);

  const helpText = CATEGORIES.find((c) => c.value === category)?.help;

  const mutation = useMutation({
    mutationFn: complaintsApi.create,
    onSuccess: async (data) => {
      // Attach evidence photos AFTER the ticket is created so we have
      // a stable id to associate them with. Failures here don't roll
      // back the complaint — the user can re-attach from the detail
      // page. We surface a non-blocking toast instead.
      if (photos.length > 0) {
        setUploadingPhotos(true);
        try {
          for (const p of photos) {
            await complaintsApi.uploadImage(data.id, p.file);
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
      qc.invalidateQueries({ queryKey: ["my-complaints"] });
      toast({
        title: "Complaint filed",
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
      // Pass owner id when known so the backend can route the notification.
      // For OWNER_BEHAVIOR complaints the notification listener skips the
      // owner ping so the grievance doesn't loop back to the subject.
      ownerId: buildingQ.data?.ownerId ?? undefined,
      complaintCategory: category,
      priority,
      title: String(fd.get("title") ?? ""),
      description: String(fd.get("description") ?? ""),
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

  return (
    <div className="animate-fade-in max-w-2xl">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/app/complaints">
          <ArrowLeft /> Back
        </Link>
      </Button>
      <PageHeader
        title="File a complaint"
        description="Tell us what's wrong. We'll route it to the right person and keep a record."
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
                placeholder="e.g. Loud parties on weekends from Flat 3B"
                className="mt-1.5"
              />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <Label>Category</Label>
                <Select
                  value={category}
                  onValueChange={(v) => setCategory(v as ComplaintCategory)}
                >
                  <SelectTrigger className="mt-1.5">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORIES.map((c) => (
                      <SelectItem key={c.value} value={c.value}>
                        {c.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {helpText && (
                  <p className="text-xs text-muted-foreground mt-1.5 flex items-start gap-1.5">
                    <Info className="size-3.5 mt-0.5 shrink-0" />
                    <span>{helpText}</span>
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
                    <SelectItem value="LOW">Low — annoying but bearable</SelectItem>
                    <SelectItem value="MEDIUM">Medium — please look into it</SelectItem>
                    <SelectItem value="HIGH">High — ongoing problem</SelectItem>
                    <SelectItem value="CRITICAL">
                      Critical — safety / emergency
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div>
              <Label htmlFor="description">What happened?</Label>
              <Textarea
                id="description"
                name="description"
                required
                minLength={20}
                rows={6}
                placeholder="When did it start? Who is involved? What have you already tried? The more we know, the faster we resolve."
                className="mt-1.5"
              />
              <p className="text-xs text-muted-foreground mt-1.5">
                Be specific. Dates, times, and names help us act faster.
              </p>
            </div>
            <div>
              <Label>Evidence (optional)</Label>
              <p className="text-xs text-muted-foreground mb-2">
                Photos, screenshots, or scans. Up to 6 files, JPG/PNG, 5 MB each.
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

            {category === "OWNER_BEHAVIOR" && (
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
                {uploadingPhotos ? "Uploading evidence…" : "File complaint"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
