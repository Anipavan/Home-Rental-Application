import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
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
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentProofSummary } from "@/types/api";

/**
 * "Receipt attached" pill rendered next to the PAID chip on the
 * maintainer's Flat charges table when one or more payment-proof
 * screenshots have been uploaded across the row's payment history.
 * When multiple proofs exist (e.g. a collection row went through
 * two payment cycles because the maintainer bumped amountDue after
 * a first payment), a small count badge tells the maintainer to
 * expect a gallery in the lightbox.
 */
export function PaymentProofChip({
  proofs,
}: {
  /** Full proof history for this collection row, newest first.
   *  Empty / undefined → the pill renders nothing. */
  proofs?: PaymentProofSummary[];
}) {
  const [open, setOpen] = useState(false);
  const list = proofs ?? [];
  if (list.length === 0) return null;
  const label =
    list.length === 1
      ? "View tenant-uploaded payment receipt"
      : `View ${list.length} payment receipts (multiple pay cycles)`;
  return (
    <>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setOpen(true);
        }}
        aria-label={label}
        title={label}
        className="ml-1 inline-flex items-center gap-0.5 rounded-full bg-primary/10 text-primary hover:bg-primary hover:text-primary-foreground transition-colors px-1.5 py-0.5"
      >
        <Receipt className="size-3" />
        {list.length > 1 && (
          <span className="text-[10px] font-semibold leading-none tabular-nums">
            {list.length}
          </span>
        )}
      </button>
      <PaymentProofGallery
        proofs={list}
        open={open}
        onOpenChange={setOpen}
      />
    </>
  );
}

/**
 * Full-size gallery viewer. Renders a thumbnail strip when there's
 * more than one proof + a large view of the currently-selected
 * proof. Single-proof mode is degenerate — one thumbnail, one large
 * view — which still gives the maintainer a caption of "how much
 * was paid, on which date" instead of a bare image.
 */
function PaymentProofGallery({
  proofs,
  open,
  onOpenChange,
}: {
  proofs: PaymentProofSummary[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [selectedIdx, setSelectedIdx] = useState(0);

  // Reset the selection every time the lightbox opens — if the
  // maintainer opens the gallery for a different row, the previous
  // selection index is stale.
  useEffect(() => {
    if (open) setSelectedIdx(0);
  }, [open]);

  const selected = proofs[selectedIdx] ?? proofs[0];
  const paidAt = selected?.paidAt ? formatDate(selected.paidAt) : "date unknown";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>
            Tenant-submitted payment proof
            {proofs.length > 1 && (
              <span className="ml-2 text-xs font-normal text-muted-foreground">
                · {proofs.length} pay cycles
              </span>
            )}
          </DialogTitle>
          <DialogDescription>
            Always verify against your bank statement before treating
            this as settled — a screenshot on its own is not proof
            the money reached your account.
          </DialogDescription>
        </DialogHeader>

        {/* Thumbnail strip — only shown when more than one proof
            exists. Each thumb lazily fetches its own blob via
            react-query; blobs are cached by paymentId so clicking
            back and forth costs a single fetch per proof. */}
        {proofs.length > 1 && (
          <div className="flex gap-2 overflow-x-auto py-1">
            {proofs.map((p, i) => (
              <ProofThumbButton
                key={p.paymentId}
                proof={p}
                selected={i === selectedIdx}
                onClick={() => setSelectedIdx(i)}
              />
            ))}
          </div>
        )}

        {/* Caption for the currently selected proof — cycle count,
            amount, and paid-at date. Cycle numbering runs newest = 1
            because the maintainer typically wants the freshest
            first when auditing "did the tenant really pay both
            times". */}
        {selected && (
          <p className="text-xs text-muted-foreground">
            {proofs.length > 1 && (
              <>
                <span className="font-semibold text-foreground">
                  Cycle {selectedIdx + 1} of {proofs.length}
                </span>
                {" · "}
              </>
            )}
            {formatINR(selected.amount ?? 0)} · paid {paidAt}
          </p>
        )}

        {selected && (
          <ProofLargeView paymentId={selected.paymentId} />
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * One thumbnail in the gallery strip. Fetches its own blob so the
 * strip can render even before the maintainer clicks anything —
 * the visual gives them the "this is which cycle" preview they
 * need to pick.
 */
function ProofThumbButton({
  proof,
  selected,
  onClick,
}: {
  proof: PaymentProofSummary;
  selected: boolean;
  onClick: () => void;
}) {
  const blobQ = useQuery({
    queryKey: ["payment-proof-blob", proof.paymentId],
    queryFn: () => paymentsApi.proofBlob(proof.paymentId),
    staleTime: 5 * 60_000,
    retry: false,
  });
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blobQ.data) return;
    const u = URL.createObjectURL(blobQ.data);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [blobQ.data]);

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={`View proof for ${formatINR(proof.amount ?? 0)}`}
      className={
        "size-16 shrink-0 overflow-hidden rounded-md border-2 transition-colors " +
        (selected
          ? "border-primary"
          : "border-border/60 hover:border-primary/60")
      }
    >
      {blobQ.isLoading ? (
        <Skeleton className="size-full" />
      ) : blobQ.isError || !url ? (
        <div className="grid size-full place-items-center text-muted-foreground bg-secondary/30">
          <ImageOff className="size-4" />
        </div>
      ) : (
        <img
          src={url}
          alt="Payment proof thumbnail"
          className="size-full object-cover"
        />
      )}
    </button>
  );
}

/**
 * The large image view. Reuses the same blob cache the thumbnail
 * fetched — clicking a thumbnail in the strip switches paymentId
 * but the blob is already in cache, so the swap is instant.
 */
function ProofLargeView({ paymentId }: { paymentId: string }) {
  const blobQ = useQuery({
    queryKey: ["payment-proof-blob", paymentId],
    queryFn: () => paymentsApi.proofBlob(paymentId),
    staleTime: 5 * 60_000,
    retry: false,
  });
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blobQ.data) return;
    const u = URL.createObjectURL(blobQ.data);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [blobQ.data]);

  return (
    <div className="rounded-lg border border-border/60 bg-secondary/30 overflow-hidden max-h-[70vh]">
      {blobQ.isLoading ? (
        <Skeleton className="h-64 w-full" />
      ) : blobQ.isError || !url ? (
        <div className="p-10 text-center text-sm text-muted-foreground">
          Couldn't load this screenshot. The file may have been
          rotated on the server or is temporarily unavailable.
        </div>
      ) : (
        <img
          src={url}
          alt="Payment proof full size"
          className="mx-auto max-h-[70vh] w-auto"
        />
      )}
    </div>
  );
}
