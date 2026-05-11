import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { getPlaceholderImage } from "@/components/property/property-card";
import { cn } from "@/lib/utils";

/**
 * Hero + thumbnail gallery rendered on the public property-detail
 * page. Falls back to placeholder Unsplash photos when the building
 * has no images, so the page never renders a broken layout.
 *
 * <p>Click any thumbnail or the hero → opens a lightbox dialog with
 * prev/next arrows. Esc + click-outside both close. Keyboard
 * left/right cycle within the lightbox.
 */
export function PropertyGallery({
  buildingId,
  flatSeed,
  alt,
}: {
  buildingId: string;
  /** Used to deterministically pick a placeholder when no images exist. */
  flatSeed: string;
  alt: string;
}) {
  const imgsQ = useQuery({
    queryKey: ["building", buildingId, "images"],
    queryFn: () => propertiesApi.buildings.images(buildingId),
    enabled: !!buildingId,
    staleTime: 60_000,
  });

  const images = imgsQ.data ?? [];
  // Map server-side propertyImage rows → loadable URLs via the
  // /images/{id}/raw streaming endpoint.
  const srcs = images.map((img) => propertiesApi.buildings.imageRawUrl(img.id));

  // Build the gallery: real cover first, then the rest. When the
  // building has no images, generate 4 deterministic placeholders so
  // the layout still fills out.
  const placeholders = [
    getPlaceholderImage(flatSeed),
    getPlaceholderImage(flatSeed + "1"),
    getPlaceholderImage(flatSeed + "2"),
    getPlaceholderImage(flatSeed + "3"),
  ];
  const gallery: string[] = srcs.length > 0 ? srcs : placeholders;

  // Lightbox state. null = closed. Number = open at that index.
  const [lightboxIdx, setLightboxIdx] = useState<number | null>(null);

  return (
    <>
      {/* Hero + thumbs grid — large hero on the left, 4 thumbs in a
          2×2 grid on the right at md+, collapses to a single hero on
          mobile. */}
      <div className="grid md:grid-cols-[2fr_1fr] gap-2 mb-6">
        <button
          type="button"
          onClick={() => setLightboxIdx(0)}
          className="aspect-[4/3] overflow-hidden rounded-2xl bg-muted relative group"
        >
          <img
            src={gallery[0]}
            alt={alt}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/30 via-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
        </button>
        <div className="hidden md:grid grid-cols-2 gap-2">
          {[1, 2, 3, 4].map((i) => {
            const src = gallery[i];
            if (!src)
              return (
                <div
                  key={i}
                  className="aspect-square rounded-2xl bg-secondary/50"
                  aria-hidden="true"
                />
              );
            return (
              <button
                key={i}
                type="button"
                onClick={() => setLightboxIdx(i)}
                className="aspect-square overflow-hidden rounded-2xl bg-muted group relative"
              >
                <img
                  src={src}
                  alt={`${alt} — view ${i + 1}`}
                  className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                />
                {/* "+N more" overlay on the last visible tile when
                    there are extra images beyond the grid. */}
                {i === 4 && gallery.length > 5 && (
                  <div className="absolute inset-0 bg-black/55 grid place-items-center text-white font-display font-semibold">
                    +{gallery.length - 5} more
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {lightboxIdx !== null && (
        <Lightbox
          srcs={gallery}
          index={lightboxIdx}
          onClose={() => setLightboxIdx(null)}
          onIndexChange={setLightboxIdx}
          alt={alt}
        />
      )}
    </>
  );
}

function Lightbox({
  srcs,
  index,
  onClose,
  onIndexChange,
  alt,
}: {
  srcs: string[];
  index: number;
  onClose: () => void;
  onIndexChange: (i: number) => void;
  alt: string;
}) {
  // Listen for keyboard nav.
  if (typeof window !== "undefined") {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    useKeyboardNav(onClose, onIndexChange, index, srcs.length);
  }

  const prev = () => onIndexChange((index - 1 + srcs.length) % srcs.length);
  const next = () => onIndexChange((index + 1) % srcs.length);

  return (
    <div
      role="dialog"
      aria-label="Image viewer"
      className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm grid place-items-center"
      onClick={onClose}
    >
      <button
        type="button"
        onClick={onClose}
        aria-label="Close"
        className="absolute top-4 right-4 size-10 rounded-full bg-white/10 hover:bg-white/20 text-white grid place-items-center"
      >
        <X className="size-5" />
      </button>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          prev();
        }}
        aria-label="Previous image"
        className={cn(
          "absolute left-4 top-1/2 -translate-y-1/2 size-12 rounded-full bg-white/10 hover:bg-white/20 text-white grid place-items-center",
          srcs.length < 2 && "hidden",
        )}
      >
        <ChevronLeft className="size-6" />
      </button>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          next();
        }}
        aria-label="Next image"
        className={cn(
          "absolute right-4 top-1/2 -translate-y-1/2 size-12 rounded-full bg-white/10 hover:bg-white/20 text-white grid place-items-center",
          srcs.length < 2 && "hidden",
        )}
      >
        <ChevronRight className="size-6" />
      </button>
      <img
        src={srcs[index]}
        alt={alt}
        onClick={(e) => e.stopPropagation()}
        className="max-h-[88vh] max-w-[88vw] rounded-2xl shadow-2xl"
      />
      <div className="absolute bottom-5 left-1/2 -translate-x-1/2 text-white/80 text-sm font-medium">
        {index + 1} / {srcs.length}
      </div>
    </div>
  );
}

function useKeyboardNav(
  onClose: () => void,
  onIndexChange: (i: number) => void,
  index: number,
  count: number,
) {
  // eslint-disable-next-line react-hooks/rules-of-hooks
  useEffectOnce(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
      else if (e.key === "ArrowLeft") onIndexChange((index - 1 + count) % count);
      else if (e.key === "ArrowRight") onIndexChange((index + 1) % count);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });
}

import { useEffect } from "react";
function useEffectOnce(cb: () => void | (() => void)) {
  useEffect(cb, []);
}
