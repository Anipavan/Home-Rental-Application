import { useRef, useState } from "react";
import { UploadCloud, X, FileText, Loader2, Camera, FolderOpen } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export interface FileUploadProps {
  accept?: string;
  multiple?: boolean;
  maxSizeMB?: number;
  onFiles: (files: File[]) => void | Promise<void>;
  disabled?: boolean;
  loading?: boolean;
  hint?: string;
  className?: string;
  variant?: "drop" | "compact";
  /**
   * Render the take-photo / upload-from-device chooser on click.
   * Defaults to `true` for image uploads (where the camera path is
   * meaningful), `false` otherwise. Pass `false` explicitly to force
   * the legacy single-input behaviour (PDF/document uploads that
   * shouldn't go through the camera).
   */
  showSourceChooser?: boolean;
}

/**
 * Decide whether the source chooser is meaningful for this MIME
 * accept pattern. The "Take photo" path uses the {@code capture}
 * attribute which only applies to image/video inputs — for PDFs,
 * docx, etc. the chooser would just confuse the user, so the
 * legacy single-input UX is used.
 */
function defaultChooserFor(accept: string | undefined): boolean {
  if (!accept) return true; // image/* is the component default
  const lower = accept.toLowerCase();
  return lower.includes("image/") || lower.includes("video/");
}

export function FileUpload({
  accept = "image/*",
  multiple = false,
  maxSizeMB = 5,
  onFiles,
  disabled,
  loading,
  hint,
  className,
  variant = "drop",
  showSourceChooser,
}: FileUploadProps) {
  // Two hidden inputs: one for the camera path (with capture="environment"
  // so mobile opens the rear camera), one for the file-picker path
  // (no capture, so mobile lets the user browse files / gallery /
  // cloud and desktop opens the regular file dialog).
  //
  // Why two inputs instead of dynamically toggling the attribute on
  // a single one: React state updates don't reliably propagate to
  // the input *before* the click() call fires in the same tick.
  // Two pre-configured inputs avoid that race.
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [dragOver, setDragOver] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const useChooser =
    showSourceChooser ?? defaultChooserFor(accept);

  function validate(files: File[]): File[] | null {
    setError(null);
    const max = maxSizeMB * 1024 * 1024;
    for (const f of files) {
      if (f.size > max) {
        setError(`${f.name} is too large (max ${maxSizeMB} MB)`);
        return null;
      }
    }
    return files;
  }

  async function handle(files: File[]) {
    const ok = validate(files);
    if (!ok || ok.length === 0) return;
    await onFiles(ok);
  }

  function onPick(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    void handle(files);
    // Reset the input so picking the same file twice in a row still
    // fires a change event.
    if (e.target) e.target.value = "";
  }

  function onDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragOver(false);
    if (disabled || loading) return;
    const files = Array.from(e.dataTransfer.files ?? []);
    void handle(files);
  }

  /**
   * Renders the two hidden file inputs that the chooser dispatches
   * to. Both share the same accept/multiple/onChange handlers — only
   * the {@code capture} attribute differs. Always renders both even
   * when {@code useChooser=false}; the file input handles the
   * fall-through click in that mode while the camera one is dormant.
   */
  const hiddenInputs = (
    <>
      <input
        ref={cameraInputRef}
        type="file"
        accept={accept}
        // Environment = rear camera, the right default for property
        // photos and KYC scans. Browsers without camera support
        // ignore the attribute and fall through to the file picker —
        // which is fine.
        capture="environment"
        multiple={multiple}
        onChange={onPick}
        className="hidden"
        disabled={disabled || loading}
      />
      <input
        ref={fileInputRef}
        type="file"
        accept={accept}
        multiple={multiple}
        onChange={onPick}
        className="hidden"
        disabled={disabled || loading}
      />
    </>
  );

  /**
   * Wraps any trigger in either the source-chooser dropdown (image
   * uploads) or a direct click-to-pick (document uploads). Keeps the
   * call sites of FileUpload identical; only the click behaviour
   * changes based on {@code useChooser}.
   */
  function withTrigger(trigger: React.ReactNode) {
    if (!useChooser) {
      return (
        <button
          type="button"
          onClick={() => !disabled && !loading && fileInputRef.current?.click()}
          disabled={disabled || loading}
          className="contents"
        >
          {trigger}
        </button>
      );
    }
    return (
      <DropdownMenu>
        <DropdownMenuTrigger asChild disabled={disabled || loading}>
          <button type="button" className="contents">
            {trigger}
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-56">
          <DropdownMenuItem
            onSelect={(e) => {
              e.preventDefault();
              cameraInputRef.current?.click();
            }}
            className="cursor-pointer"
          >
            <Camera className="size-4" />
            <div className="flex-1">
              <p className="text-sm">Take photo</p>
              <p className="text-[11px] text-muted-foreground">
                Open camera (mobile)
              </p>
            </div>
          </DropdownMenuItem>
          <DropdownMenuItem
            onSelect={(e) => {
              e.preventDefault();
              fileInputRef.current?.click();
            }}
            className="cursor-pointer"
          >
            <FolderOpen className="size-4" />
            <div className="flex-1">
              <p className="text-sm">Upload from device</p>
              <p className="text-[11px] text-muted-foreground">
                Browse files
              </p>
            </div>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
  }

  if (variant === "compact") {
    return (
      <div className={className}>
        {hiddenInputs}
        {withTrigger(
          <Button
            asChild={false}
            type="button"
            variant="outline"
            size="sm"
            disabled={disabled || loading}
            // tabIndex={-1} so the inner button doesn't steal focus
            // from the wrapping DropdownMenuTrigger.
            tabIndex={-1}
          >
            {loading ? <Loader2 className="animate-spin" /> : <UploadCloud />}
            Upload
          </Button>,
        )}
        {error && <p className="text-xs text-destructive mt-1.5">{error}</p>}
      </div>
    );
  }

  // Drop variant — the big dashed area. Drag-and-drop still goes
  // directly to the file handler (no chooser intermediate), since the
  // user already made the "I'm uploading from disk" choice by
  // dragging. The chooser only appears on CLICK.
  return (
    <div className={cn("flex", className)}>
      {hiddenInputs}
      {withTrigger(
        <div
          onDrop={onDrop}
          onDragOver={(e) => {
            e.preventDefault();
            if (!disabled && !loading) setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          className={cn(
            "relative w-full cursor-pointer rounded-2xl border-2 border-dashed p-6 text-center transition-all flex flex-col items-center justify-center gap-2",
            dragOver
              ? "border-primary bg-primary/5"
              : "border-border hover:border-primary/50 hover:bg-secondary/30",
            (disabled || loading) && "cursor-not-allowed opacity-60",
          )}
        >
          <div className="size-10 rounded-xl bg-primary/10 text-primary grid place-items-center">
            {loading ? (
              <Loader2 className="size-5 animate-spin" />
            ) : (
              <UploadCloud className="size-5" />
            )}
          </div>
          <p className="font-medium text-sm">
            {loading ? "Uploading…" : "Drop a file or click"}
          </p>
          {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
          {error && <p className="text-xs text-destructive">{error}</p>}
        </div>,
      )}
    </div>
  );
}

export function FilePreview({
  url,
  name,
  onRemove,
  className,
}: {
  url: string;
  name?: string;
  onRemove?: () => void;
  className?: string;
}) {
  const isImage = /\.(png|jpe?g|gif|webp|svg|avif)$/i.test(url) || url.startsWith("data:image");
  return (
    <div
      className={cn(
        "relative rounded-xl overflow-hidden border bg-card",
        className,
      )}
    >
      {isImage ? (
        <img
          src={url}
          alt={name ?? "preview"}
          className="w-full aspect-square object-cover"
        />
      ) : (
        <div className="aspect-square grid place-items-center bg-secondary">
          <FileText className="size-6 text-muted-foreground" />
        </div>
      )}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label="Remove"
          className="absolute top-2 right-2 size-7 rounded-full bg-black/60 text-white grid place-items-center hover:bg-black/80 transition-colors"
        >
          <X className="size-3.5" />
        </button>
      )}
    </div>
  );
}
