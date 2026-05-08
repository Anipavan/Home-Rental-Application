import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ImageOff } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { cn } from "@/lib/utils";

/**
 * Renders a single stored property image inside an &lt;img&gt; element.
 *
 * The backend stores each image as a row in {@code propertyimages} whose
 * {@code imageUrl} is a server-side filesystem path — not a URL the browser
 * can load. So this component:
 *   1. Fetches the bytes via the auth-tracked axios client at
 *      {@code /properties/images/{imageId}/raw} (returns a Blob).
 *   2. Wraps the Blob in an object URL with {@link URL.createObjectURL}.
 *   3. Renders the object URL in a real &lt;img&gt;.
 *   4. Revokes the object URL on unmount / id change so we don't leak.
 *
 * <p>Skeleton placeholder while loading, generic icon if the fetch fails
 * (deleted file, expired session, etc.).
 */
export function PropertyImage({
  imageId,
  alt = "",
  className,
}: {
  imageId: string;
  alt?: string;
  className?: string;
}) {
  const q = useQuery({
    queryKey: ["property-image-blob", imageId],
    queryFn: () => propertiesApi.buildings.imageRaw(imageId),
    enabled: !!imageId,
    staleTime: 5 * 60_000,
    retry: 1,
  });

  const [objectUrl, setObjectUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!q.data) {
      setObjectUrl(null);
      return;
    }
    const url = URL.createObjectURL(q.data);
    setObjectUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [q.data]);

  if (q.isLoading) {
    return (
      <div
        className={cn(
          "bg-secondary animate-pulse",
          className,
        )}
        aria-busy="true"
      />
    );
  }

  if (q.isError || !objectUrl) {
    return (
      <div
        className={cn(
          "bg-muted flex items-center justify-center text-muted-foreground",
          className,
        )}
      >
        <ImageOff className="size-6" />
      </div>
    );
  }

  return (
    <img
      src={objectUrl}
      alt={alt}
      className={cn("object-cover", className)}
      loading="lazy"
    />
  );
}
