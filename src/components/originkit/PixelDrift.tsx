"use client";

import { useEffect, useRef, type CSSProperties } from "react";

type Mode = "onEnter" | "onHover";
type Position = "above" | "middle" | "below";

export interface PixelDriftProps {
  text?: string;
  colors?: string[];
  mode?: Mode;
  replay?: boolean;
  position?: Position;
  particleSize?: number;
  particleCount?: number;
  mouseEnabled?: boolean;
  mouseRadius?: number;
  mouseForce?: number;
  fontSize?: number;
  autoFit?: boolean;
  style?: CSSProperties;
  className?: string;
}

function resolveEasing(t: number) {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function sampleGlyph(
  text: string,
  fontPx: number,
  cssW: number,
  cssH: number,
  density: number,
) {
  const off = document.createElement("canvas");
  off.width = Math.max(2, Math.floor(cssW));
  off.height = Math.max(2, Math.floor(cssH));
  const ctx = off.getContext("2d", { willReadFrequently: true });
  if (!ctx) return [] as { x: number; y: number }[];

  ctx.clearRect(0, 0, off.width, off.height);
  ctx.fillStyle = "#ffffff";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.font = `800 ${fontPx}px InterVariableFramer, Inter, system-ui, sans-serif`;
  ctx.fillText(text, off.width / 2, off.height / 2 + fontPx * 0.04);

  const { data } = ctx.getImageData(0, 0, off.width, off.height);
  const step = Math.max(2, Math.round(11 - density / 7));
  const pts: { x: number; y: number }[] = [];
  for (let y = 0; y < off.height; y += step) {
    for (let x = 0; x < off.width; x += step) {
      const i = (y * off.width + x) * 4;
      if (data[i + 3] > 140) pts.push({ x, y });
    }
  }
  return pts;
}

export default function PixelDrift({
  text = "PIXEL DRIFT",
  colors = ["#FFFFFF", "#EF4444", "#FFFFFF"],
  mode = "onEnter",
  replay = true,
  position = "middle",
  particleSize = 12,
  particleCount = 50,
  mouseEnabled = true,
  mouseRadius = 70,
  mouseForce = 30,
  fontSize = 80,
  autoFit = true,
  style,
  className,
}: PixelDriftProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    const canvas = canvasRef.current;
    if (!container || !canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const palette = colors.length ? colors : ["#FFFFFF"];
    const pointer = { x: -9999, y: -9999, active: false };
    let mouseSpeed = 0;
    let lastPx = -9999;
    let lastPy = -9999;
    let smoothX = -99999;
    let smoothY = -99999;
    let formVal = 0;
    let reverse = false;
    let hidden = mode === "onHover";
    let entered = false;
    let lastFrame = performance.now();
    let disposed = false;

    let ox: Float32Array = new Float32Array(0);
    let oy: Float32Array = new Float32Array(0);
    let sx: Float32Array = new Float32Array(0);
    let sy: Float32Array = new Float32Array(0);
    let px: Float32Array = new Float32Array(0);
    let py: Float32Array = new Float32Array(0);
    let repX: Float32Array = new Float32Array(0);
    let repY: Float32Array = new Float32Array(0);
    let cIdx: Uint8Array = new Uint8Array(0);
    let count = 0;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    const build = () => {
      const cssW = container.clientWidth || 800;
      const cssH = container.clientHeight || 300;
      canvas.width = Math.floor(cssW * dpr);
      canvas.height = Math.floor(cssH * dpr);
      canvas.style.width = `${cssW}px`;
      canvas.style.height = `${cssH}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      let fitSize = fontSize;
      if (autoFit) {
        const probe = document.createElement("canvas").getContext("2d");
        if (probe) {
          let lo = 24;
          let hi = Math.min(fontSize * 2.2, cssW * 0.28);
          for (let i = 0; i < 12; i++) {
            const mid = (lo + hi) / 2;
            probe.font = `800 ${mid}px Inter, system-ui, sans-serif`;
            const w = probe.measureText(text).width;
            if (w > cssW * 0.92 || mid > cssH * 0.72) hi = mid;
            else lo = mid;
          }
          fitSize = lo;
        }
      }

      const pts = sampleGlyph(text, fitSize, cssW, cssH, particleCount);
      count = pts.length;
      ox = new Float32Array(count);
      oy = new Float32Array(count);
      sx = new Float32Array(count);
      sy = new Float32Array(count);
      px = new Float32Array(count);
      py = new Float32Array(count);
      repX = new Float32Array(count);
      repY = new Float32Array(count);
      cIdx = new Uint8Array(count);

      for (let i = 0; i < count; i++) {
        ox[i] = pts[i].x;
        oy[i] = pts[i].y;
        const edge = Math.floor(Math.random() * 4);
        if (edge === 0) {
          sx[i] = Math.random() * cssW;
          sy[i] = -40 - Math.random() * 80;
        } else if (edge === 1) {
          sx[i] = cssW + 40 + Math.random() * 80;
          sy[i] = Math.random() * cssH;
        } else if (edge === 2) {
          sx[i] = Math.random() * cssW;
          sy[i] = cssH + 40 + Math.random() * 80;
        } else {
          sx[i] = -40 - Math.random() * 80;
          sy[i] = Math.random() * cssH;
        }
        px[i] = sx[i];
        py[i] = sy[i];
        cIdx[i] = i % palette.length;
      }
    };

    const enter = () => {
      if (entered && !replay) return;
      entered = true;
      hidden = false;
      reverse = false;
    };

    const formOut = () => {
      if (mode === "onHover") reverse = true;
    };

    const onMove = (e: PointerEvent) => {
      const rect = canvas.getBoundingClientRect();
      const nx = e.clientX - rect.left;
      const ny = e.clientY - rect.top;
      if (lastPx > -9000) {
        mouseSpeed = Math.min(40, Math.hypot(nx - lastPx, ny - lastPy));
      }
      lastPx = nx;
      lastPy = ny;
      pointer.x = nx;
      pointer.y = ny;
      pointer.active = true;
    };

    const onLeave = () => {
      pointer.active = false;
      lastPx = -9999;
      lastPy = -9999;
    };

    const drawFrame = (now: number) => {
      const cssW = container.clientWidth || 800;
      const cssH = container.clientHeight || 300;
      ctx.clearRect(0, 0, cssW, cssH);

      const dt = Math.min(64, Math.max(0, now - lastFrame));
      lastFrame = now;
      const target = reverse ? 0 : 1;
      const formMs = 1100;
      const stepv = dt / formMs;
      if (formVal < target) formVal = Math.min(target, formVal + stepv);
      else if (formVal > target) formVal = Math.max(target, formVal - stepv);
      if (reverse && formVal <= 0) hidden = true;
      if (hidden) return;

      const forming = formVal < 1;
      const factor = resolveEasing(formVal);
      const drawSize = Math.max(1, particleSize / 4);
      const half = drawSize / 2;

      mouseSpeed *= 0.88;
      const active = !forming && mouseEnabled && pointer.active;
      if (active) {
        const lerpFactor = Math.max(0.08, 0.3 - mouseSpeed * 0.006);
        if (smoothX < -9000) {
          smoothX = pointer.x;
          smoothY = pointer.y;
        } else {
          smoothX += (pointer.x - smoothX) * lerpFactor;
          smoothY += (pointer.y - smoothY) * lerpFactor;
        }
      } else {
        smoothX = -99999;
        smoothY = -99999;
      }

      const mx = smoothX;
      const my = smoothY;
      const repCutoff = Math.max(1, mouseRadius);
      const repCutoffSq = repCutoff * repCutoff;

      const buckets: number[][] = palette.map(() => []);

      for (let i = 0; i < count; i++) {
        const oxi = ox[i];
        const oyi = oy[i];
        if (forming) {
          px[i] = sx[i] + (oxi - sx[i]) * factor;
          py[i] = sy[i] + (oyi - sy[i]) * factor;
          buckets[cIdx[i]].push(i);
          continue;
        }

        let inZone = false;
        if (active) {
          const dx = oxi - mx;
          const dy = oyi - my;
          const distSq = dx * dx + dy * dy;
          if (distSq > 0 && distSq < repCutoffSq) {
            const dist = Math.sqrt(distSq);
            const nx = dx / dist;
            const ny = dy / dist;
            const falloff = 1 - dist / repCutoff;
            const push = falloff * mouseSpeed * mouseForce * 0.05;
            repX[i] += nx * push;
            repY[i] += ny * push;
            const targetRepX = nx * (repCutoff - dist);
            const targetRepY = ny * (repCutoff - dist);
            repX[i] += (targetRepX - repX[i]) * 0.06;
            repY[i] += (targetRepY - repY[i]) * 0.06;
            inZone = true;
          }
        }
        if (!inZone) {
          repX[i] *= 0.97;
          repY[i] *= 0.97;
        }
        px[i] = oxi + repX[i];
        py[i] = oyi + repY[i];
        buckets[cIdx[i]].push(i);
      }

      ctx.globalAlpha = forming ? Math.min(1, Math.max(0, factor)) : 1;
      for (let b = 0; b < buckets.length; b++) {
        const bucket = buckets[b];
        if (!bucket.length) continue;
        ctx.fillStyle = palette[b];
        for (let k = 0; k < bucket.length; k++) {
          const i = bucket[k];
          ctx.fillRect(px[i] - half, py[i] - half, drawSize, drawSize);
        }
      }
      ctx.globalAlpha = 1;
    };

    build();

    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) enter();
        else if (replay) {
          entered = false;
          hidden = true;
          reverse = false;
          formVal = 0;
        }
      },
      { threshold: 0.15 },
    );
    io.observe(container);

    if (mode === "onHover") {
      container.addEventListener("pointerenter", enter);
      container.addEventListener("pointerleave", formOut);
    }

    canvas.addEventListener("pointermove", onMove);
    canvas.addEventListener("pointerleave", onLeave);
    canvas.addEventListener("pointercancel", onLeave);

    const ro = new ResizeObserver(() => {
      const was = formVal;
      build();
      formVal = was;
    });
    ro.observe(container);

    const loop = (now: number) => {
      if (disposed) return;
      drawFrame(now);
      rafRef.current = requestAnimationFrame(loop);
    };
    rafRef.current = requestAnimationFrame(loop);

    const boot = [
      setTimeout(() => enter(), 80),
      setTimeout(() => enter(), 280),
    ];

    return () => {
      disposed = true;
      boot.forEach(clearTimeout);
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current);
      io.disconnect();
      ro.disconnect();
      container.removeEventListener("pointerenter", enter);
      container.removeEventListener("pointerleave", formOut);
      canvas.removeEventListener("pointermove", onMove);
      canvas.removeEventListener("pointerleave", onLeave);
      canvas.removeEventListener("pointercancel", onLeave);
    };
  }, [
    text,
    colors.join("|"),
    mode,
    replay,
    position,
    particleSize,
    particleCount,
    mouseEnabled,
    mouseRadius,
    mouseForce,
    fontSize,
    autoFit,
  ]);

  return (
    <div
      ref={containerRef}
      className={className}
      style={{
        position: "relative",
        width: "100%",
        height: "100%",
        minHeight: 180,
        overflow: "hidden",
        ...style,
      }}
    >
      <canvas
        ref={canvasRef}
        style={{
          position: "absolute",
          inset: 0,
          width: "100%",
          height: "100%",
          display: "block",
        }}
      />
    </div>
  );
}
