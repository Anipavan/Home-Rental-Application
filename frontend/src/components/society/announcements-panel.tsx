import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Megaphone, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { societyApi } from "@/lib/api/society";
import { extractErrorMessage } from "@/lib/api/client";
import { useToast } from "@/hooks/use-toast";
import { useAuthStore } from "@/stores/auth-store";
import type { SocietyAnnouncement } from "@/types/api";

/**
 * V17 — building notice-board panel. Two modes:
 *
 *   `canPost=true`  — maintainer/owner view. Shows a compact "Post
 *                     new announcement" form above the list; each
 *                     row has a Delete button (author + privileged
 *                     roles only, but we surface the button for
 *                     everyone with post rights since they can all
 *                     delete anything on their board).
 *
 *   `canPost=false` — resident view. Read-only list. Nothing else.
 *
 * <p>Feed auto-refreshes every 30 seconds so residents see fresh
 * notices without a manual reload.
 */
export function AnnouncementsPanel({
  buildingId,
  canPost,
}: {
  buildingId: string;
  canPost: boolean;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const { authUserId } = useAuthStore();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");

  const listQ = useQuery({
    queryKey: ["society-announcements", buildingId],
    queryFn: () => societyApi.announcements.list(buildingId),
    enabled: !!buildingId,
    refetchInterval: 30_000,
    staleTime: 15_000,
  });

  const createMut = useMutation({
    mutationFn: () =>
      societyApi.announcements.create(buildingId, {
        title: title.trim(),
        body: body.trim(),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["society-announcements", buildingId] });
      toast({ title: "Announcement posted." });
      setTitle("");
      setBody("");
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't post",
        description: extractErrorMessage(e),
      }),
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => societyApi.announcements.remove(buildingId, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["society-announcements", buildingId] });
      toast({ title: "Announcement deleted." });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't delete",
        description: extractErrorMessage(e),
      }),
  });

  const list = listQ.data ?? [];
  const canSubmit =
    title.trim().length > 0
    && body.trim().length > 0
    && !createMut.isPending;

  return (
    <Card className="mb-6">
      <CardContent className="p-5">
        <div className="flex items-center gap-2 mb-3">
          <Megaphone className="size-4 text-primary" />
          <h3 className="font-display font-semibold text-sm">
            Announcements
          </h3>
        </div>

        {canPost && (
          <div className="mb-4 rounded-lg border border-border/60 bg-secondary/20 p-3 space-y-2">
            <div>
              <Label htmlFor="ann-title" className="text-xs">Title</Label>
              <Input
                id="ann-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={200}
                placeholder="e.g. Water tank cleaning on Sunday 10am"
                className="mt-1 h-9"
              />
            </div>
            <div>
              <Label htmlFor="ann-body" className="text-xs">Message</Label>
              <Textarea
                id="ann-body"
                value={body}
                onChange={(e) => setBody(e.target.value)}
                maxLength={4000}
                rows={3}
                placeholder="Details residents should know…"
                className="mt-1"
              />
              <p className="text-[10px] text-muted-foreground mt-0.5">
                {body.length}/4000
              </p>
            </div>
            <div className="flex justify-end">
              <Button
                variant="gradient"
                size="sm"
                disabled={!canSubmit}
                onClick={() => createMut.mutate()}
              >
                {createMut.isPending ? "Posting…" : "Post announcement"}
              </Button>
            </div>
          </div>
        )}

        {listQ.isLoading ? (
          <Skeleton className="h-24 rounded-md" />
        ) : list.length === 0 ? (
          <p className="text-xs text-muted-foreground italic">
            {canPost
              ? "No announcements yet. Post something above and every resident of this building will see it here."
              : "No announcements yet. Your building's maintainer hasn't posted anything."}
          </p>
        ) : (
          <div className="space-y-2">
            {list.map((a) => (
              <AnnouncementRow
                key={a.id}
                a={a}
                canDelete={canPost || a.authorUserId === authUserId}
                onDelete={() => {
                  if (confirm(`Delete "${a.title}"?`)) deleteMut.mutate(a.id);
                }}
                deleting={
                  deleteMut.isPending && deleteMut.variables === a.id
                }
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function AnnouncementRow({
  a,
  canDelete,
  onDelete,
  deleting,
}: {
  a: SocietyAnnouncement;
  canDelete: boolean;
  onDelete: () => void;
  deleting: boolean;
}) {
  return (
    <div className="rounded-lg border border-border/60 bg-card p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-sm">{a.title}</p>
          <p className="text-xs text-muted-foreground mt-0.5">
            {a.authorName} · {formatWhen(a.createdAt)}
          </p>
        </div>
        {canDelete && (
          <button
            type="button"
            onClick={onDelete}
            disabled={deleting}
            className="text-muted-foreground hover:text-destructive p-1 rounded"
            aria-label="Delete announcement"
          >
            <Trash2 className="size-3.5" />
          </button>
        )}
      </div>
      <p className="text-sm mt-2 whitespace-pre-wrap">{a.body}</p>
    </div>
  );
}

function formatWhen(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleString("en-IN", {
      day: "numeric",
      month: "short",
      hour: "numeric",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}
