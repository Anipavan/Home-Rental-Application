import * as React from "react";
import { useCallback, useEffect, useImperativeHandle, useRef, useState } from "react";
import { Eraser, Pencil } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

export interface SignaturePadHandle {
  /** Returns the signature as a base64-encoded PNG (data URL stripped). */
  toDataUrl(): string | null;
  clear(): void;
  isEmpty(): boolean;
}

export interface SignaturePadProps {
  /** Pad height in px. Width fills the container. */
  height?: number;
  /** Pen colour. */
  color?: string;
  /** Stroke width in px. */
  lineWidth?: number;
  /** Fired whenever the user lifts their pointer after a stroke. */
  onChange?: (empty: boolean) => void;
  className?: string;
}

/**
 * Lightweight signature canvas — no external deps. Handles mouse + touch +
 * pointer events, scales correctly under devicePixelRatio, and serialises
 * to a base64 PNG via toDataUrl().
 */
export const SignaturePad = React.forwardRef<SignaturePadHandle, SignaturePadProps>(
  ({ height = 180, color = "#0f172a", lineWidth = 2.4, onChange, className }, ref) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const [drawing, setDrawing] = useState(false);
    const [empty, setEmpty] = useState(true);

    const setEmptyState = useCallback(
      (next: boolean) => {
        setEmpty((prev) => {
          if (prev !== next) onChange?.(next);
          return next;
        });
      },
      [onChange],
    );

    const resize = useCallback(() => {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.getBoundingClientRect();
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;
      ctx.scale(dpr, dpr);
      ctx.lineWidth = lineWidth;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      ctx.strokeStyle = color;
    }, [color, lineWidth]);

    useEffect(() => {
      resize();
      const obs = new ResizeObserver(() => resize());
      if (canvasRef.current) obs.observe(canvasRef.current);
      return () => obs.disconnect();
    }, [resize]);

    function pointer(e: React.PointerEvent<HTMLCanvasElement>) {
      const canvas = canvasRef.current;
      if (!canvas) return { x: 0, y: 0 };
      const rect = canvas.getBoundingClientRect();
      return { x: e.clientX - rect.left, y: e.clientY - rect.top };
    }

    function start(e: React.PointerEvent<HTMLCanvasElement>) {
      e.preventDefault();
      const ctx = canvasRef.current?.getContext("2d");
      if (!ctx) return;
      canvasRef.current?.setPointerCapture(e.pointerId);
      const { x, y } = pointer(e);
      ctx.beginPath();
      ctx.moveTo(x, y);
      setDrawing(true);
    }

    function move(e: React.PointerEvent<HTMLCanvasElement>) {
      if (!drawing) return;
      const ctx = canvasRef.current?.getContext("2d");
      if (!ctx) return;
      const { x, y } = pointer(e);
      ctx.lineTo(x, y);
      ctx.stroke();
    }

    function end(e: React.PointerEvent<HTMLCanvasElement>) {
      if (!drawing) return;
      canvasRef.current?.releasePointerCapture(e.pointerId);
      setDrawing(false);
      setEmptyState(false);
    }

    useImperativeHandle(ref, () => ({
      toDataUrl: () => {
        const canvas = canvasRef.current;
        if (!canvas) return null;
        return canvas.toDataURL("image/png");
      },
      clear: () => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        ctx.save();
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.restore();
        setEmptyState(true);
      },
      isEmpty: () => empty,
    }));

    return (
      <div className={cn("rounded-xl border bg-card overflow-hidden", className)}>
        <div className="px-4 py-2 border-b bg-secondary/40 text-xs text-muted-foreground flex items-center gap-2">
          <Pencil className="size-3" /> Sign in the box below
        </div>
        <canvas
          ref={canvasRef}
          style={{ width: "100%", height, touchAction: "none" }}
          className="block bg-[linear-gradient(to_top,_hsl(var(--border)/0.5)_1px,_transparent_1px)] bg-[size:100%_24px]"
          onPointerDown={start}
          onPointerMove={move}
          onPointerUp={end}
          onPointerCancel={end}
        />
      </div>
    );
  },
);
SignaturePad.displayName = "SignaturePad";

export function SignaturePadClearButton({
  onClick,
  disabled,
}: {
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <Button type="button" variant="ghost" size="sm" onClick={onClick} disabled={disabled}>
      <Eraser /> Clear
    </Button>
  );
}
