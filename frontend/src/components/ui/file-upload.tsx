import { useRef, useState } from "react";
import { UploadCloud, X, FileText, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

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
}: FileUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
    if (e.target) e.target.value = "";
  }

  function onDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragOver(false);
    if (disabled || loading) return;
    const files = Array.from(e.dataTransfer.files ?? []);
    void handle(files);
  }

  if (variant === "compact") {
    return (
      <div className={className}>
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          multiple={multiple}
          onChange={onPick}
          className="hidden"
          disabled={disabled || loading}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || loading}
          onClick={() => inputRef.current?.click()}
        >
          {loading ? <Loader2 className="animate-spin" /> : <UploadCloud />}
          Upload
        </Button>
        {error && (
          <p className="text-xs text-destructive mt-1.5">{error}</p>
        )}
      </div>
    );
  }

  return (
    <div className={cn("flex", className)}>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        multiple={multiple}
        onChange={onPick}
        className="hidden"
        disabled={disabled || loading}
      />
      <div
        onDrop={onDrop}
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled && !loading) setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onClick={() => !disabled && !loading && inputRef.current?.click()}
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
      </div>
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
