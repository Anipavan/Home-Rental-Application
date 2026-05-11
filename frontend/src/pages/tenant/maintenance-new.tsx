import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Loader2 } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { maintenanceApi } from "@/lib/api/maintenance";
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
  MaintenanceCategory,
  MaintenancePriority,
} from "@/types/api";

const categories: MaintenanceCategory[] = [
  "PLUMBING",
  "ELECTRICAL",
  "PAINTING",
  "APPLIANCE",
  "CLEANING",
  "PEST_CONTROL",
  "GENERAL",
];

interface StagedPhoto {
  file: File;
  url: string;
}

export function MaintenanceNewPage() {
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
  // does. We resolve it so the maintenance.created event carries the
  // owner, and the notification-service can fan a bell entry out to
  // them ("new ticket on your property"). Without this, the owner
  // side of the bell stays empty even after the Kafka deserializer
  // fix because the event itself has no ownerId.
  const buildingQ = useQuery({
    queryKey: ["building", flat?.buildingId],
    queryFn: () => propertiesApi.buildings.get(flat!.buildingId),
    enabled: !!flat?.buildingId,
  });

  const [category, setCategory] = useState<MaintenanceCategory>("PLUMBING");
  const [priority, setPriority] = useState<MaintenancePriority>("MEDIUM");
  const [photos, setPhotos] = useState<StagedPhoto[]>([]);
  const [uploadingPhotos, setUploadingPhotos] = useState(false);

  const mutation = useMutation({
    mutationFn: maintenanceApi.create,
    onSuccess: async (data) => {
      if (photos.length > 0) {
        setUploadingPhotos(true);
        try {
          for (const p of photos) {
            await maintenanceApi.uploadImage(data.id, p.file);
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
      qc.invalidateQueries({ queryKey: ["my-maintenance"] });
      toast({
        title: "Request submitted",
        description: "We'll keep you posted as it progresses.",
      });
      navigate("/app/maintenance");
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
      // Carry the owner id so the maintenance.created event includes it
      // and notification-service can ping the owner's bell. Falls back
      // to undefined when the building lookup is still in flight — the
      // notification listener handles a missing ownerId gracefully.
      ownerId: buildingQ.data?.ownerId ?? undefined,
      category,
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
        <Link to="/app/maintenance">
          <ArrowLeft /> Back
        </Link>
      </Button>
      <PageHeader
        title="Raise a request"
        description="Tell us what's broken. We'll handle the rest."
      />

      <Card>
        <CardContent className="p-6 sm:p-8">
          <form onSubmit={onSubmit} className="space-y-5">
            <div>
              <Label htmlFor="title">What needs fixing?</Label>
              <Input
                id="title"
                name="title"
                required
                maxLength={120}
                placeholder="e.g. Leaking tap in master bathroom"
                className="mt-1.5"
              />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <Label>Category</Label>
                <Select value={category} onValueChange={(v) => setCategory(v as MaintenanceCategory)}>
                  <SelectTrigger className="mt-1.5">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((c) => (
                      <SelectItem key={c} value={c}>
                        {c.replace("_", " ")}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label>Priority</Label>
                <Select value={priority} onValueChange={(v) => setPriority(v as MaintenancePriority)}>
                  <SelectTrigger className="mt-1.5">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="LOW">Low — when convenient</SelectItem>
                    <SelectItem value="MEDIUM">Medium — within a few days</SelectItem>
                    <SelectItem value="HIGH">High — urgent</SelectItem>
                    <SelectItem value="CRITICAL">Critical — emergency</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div>
              <Label htmlFor="description">Describe the issue</Label>
              <Textarea
                id="description"
                name="description"
                required
                minLength={10}
                rows={5}
                placeholder="When did it start? Any sounds, leaks, smells? The more we know, the faster we fix."
                className="mt-1.5"
              />
            </div>
            <div>
              <Label>Photos (optional)</Label>
              <p className="text-xs text-muted-foreground mb-2">
                Up to 6. JPG/PNG, max 5 MB each.
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
            <div className="flex justify-end gap-2 pt-2">
              <Button asChild variant="ghost">
                <Link to="/app/maintenance">Cancel</Link>
              </Button>
              <Button
                type="submit"
                variant="gradient"
                disabled={submitting || !flat}
              >
                {submitting && <Loader2 className="animate-spin" />}
                {uploadingPhotos ? "Uploading photos…" : "Submit request"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
