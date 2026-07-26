import { useEffect, useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { ImageOff, Receipt } from "lucide-react";
import { paymentsApi } from "@/lib/api/payments";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Fetch Payment rows for a set of collection-row paymentIds in
 * parallel and return a Map of paymentId → paymentProofUrl (or null
 * when no proof attached). React Query dedupes shared paymentIds
 * (bulk-pay rows all point at the same Payment).
 *
 * <p>Skips falsy ids up-front so pre-society-bridge PAID rows
 * (marked manually by the maintainer, no paymentId stamped) don't
 * fire pointless requests.
 */
export function usePaymentProofsByPaymentId(
  paymentIds: Array<string | null | undefined>,
): Map<string, string | null> {
  const uniqueIds = Array.from(
    new Set(
      paymentIds.filter((id): id is string => !!id && id.length > 0),
    ),
  );
  const queries = useQueries({
    queries: uniqueIds.map((id) => ({
      queryKey: ["payment", id] as const,
      queryFn: () => paymentsApi.get(id),
      staleTime: 60_000,
      retry: false,
    })),
  });
  const map = new Map<string, string | null>();
  uniqueIds.forEach((id, i) => {
    const data = queries[i]?.data;
    map.set(id, data?.paymentProofUrl ?? null);
  });
  return map;
}

/**
 * Small "receipt attached" pill rendered next to the PAID chip on
 * the maintainer's flat charges table when a payment-proof
 * screenshot has been uploaded. Clicking opens a lightbox with the
 * full-size image. Renders nothing when no proof is attached — the
 * parent can wrap unconditionally.
 */
export function PaymentProofChip({
  paymentId,
  hasProof,
}: {
  paymentId: string;
  hasProof: boolean;
}) {
  const [open, setOpen] = useState(false);
  if (!hasProof) return null;
  return (
    <>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setOpen(true);
        }}
        aria-label="View payment receipt"
        title="View tenant-uploaded payment receipt"
        className="ml-1 inline-flex size-5 items-center justify-center rounded-full bg-primary/10 text-primary hover:bg-primary hover:text-primary-foreground transition-colors"
      >
        <Receipt className="size-3" />
      </button>
      <PaymentProofLightbox
        paymentId={paymentId}
        open={open}
        onOpenChange={setOpen}
      />
    </>
  );
}

/**
 * Small clickable thumbnail rendered inside the REMARKS cell on the
 * maintainer's flat charges table when a payment-proof screenshot is
 * attached. Opens the same lightbox on click. Sized so it doesn't
 * push the table wider — the thumbnail is 48×48 with rounded
 * corners. Renders nothing when no proof exists.
 */
export function PaymentProofThumbnail({
  paymentId,
  hasProof,
}: {
  paymentId: string;
  hasProof: boolean;
}) {
  const [open, setOpen] = useState(false);
  const blobQ = useQuery({
    queryKey: ["payment-proof-blob", paymentId],
    queryFn: () => paymentsApi.proofBlob(paymentId),
    enabled: hasProof,
    staleTime: 5 * 60_000,
    retry: false,
  });

  const [thumbUrl, setThumbUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blobQ.data) return;
    const url = URL.createObjectURL(blobQ.data);
    setThumbUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [blobQ.data]);

  if (!hasProof) return null;

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="View payment proof full size"
        className="mt-1 block size-12 shrink-0 overflow-hidden rounded-md border border-border/60 bg-secondary/30 hover:border-primary/60 transition-colors"
      >
        {blobQ.isLoading ? (
          <Skeleton className="size-full" />
        ) : blobQ.isError || !thumbUrl ? (
          <div className="grid size-full place-items-center text-muted-foreground">
            <ImageOff className="size-4" />
          </div>
        ) : (
          <img
            src={thumbUrl}
            alt="Payment proof"
            className="size-full object-cover"
          />
        )}
      </button>
      <PaymentProofLightbox
        paymentId={paymentId}
        open={open}
        onOpenChange={setOpen}
      />
    </>
  );
}

/**
 * Full-size proof viewer. Lazily fetches the blob only when open —
 * closed lightboxes cost nothing. Kept as a separate component so
 * both the camera chip and the thumbnail can share the same modal.
 */
function PaymentProofLightbox({
  paymentId,
  open,
  onOpenChange,
}: {
  paymentId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const blobQ = useQuery({
    queryKey: ["payment-proof-blob", paymentId],
    queryFn: () => paymentsApi.proofBlob(paymentId),
    enabled: open,
    staleTime: 5 * 60_000,
    retry: false,
  });
  const [fullUrl, setFullUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blobQ.data) return;
    const url = URL.createObjectURL(blobQ.data);
    setFullUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [blobQ.data]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Tenant-submitted payment proof</DialogTitle>
          <DialogDescription>
            Always verify against your bank statement before treating
            this as settled — a screenshot on its own is not proof
            the money reached your account.
          </DialogDescription>
        </DialogHeader>
        <div className="rounded-lg border border-border/60 bg-secondary/30 overflow-hidden max-h-[70vh]">
          {blobQ.isLoading ? (
            <Skeleton className="h-64 w-full" />
          ) : blobQ.isError || !fullUrl ? (
            <div className="p-10 text-center text-sm text-muted-foreground">
              Couldn't load the screenshot. The file may have been
              rotated on the server or is temporarily unavailable.
            </div>
          ) : (
            <img
              src={fullUrl}
              alt="Payment proof"
              className="mx-auto max-h-[70vh] w-auto"
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
