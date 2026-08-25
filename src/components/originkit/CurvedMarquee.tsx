"use client";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
} from "react";

const MAX_SPEED = 280;

export interface CurvedMarqueeProps {
  text?: string;
  font?: CSSProperties;
  color?: string;
  direction?: "left" | "right";
  baseVelocity?: number;
  curveAmount?: number;
  gap?: number;
  draggable?: boolean;
  dragIntensity?: number;
  fade?: boolean;
  fadePercent?: number;
  style?: CSSProperties;
  className?: string;
}

export default function CurvedMarquee({
  text = "Curved Marquee",
  font = {
    fontFamily: "Inter, system-ui, sans-serif",
    fontWeight: 600,
    fontSize: 56,
    lineHeight: "1.5em",
    letterSpacing: "1px",
  },
  color = "#FFFFFF",
  direction = "right",
  baseVelocity = 35,
  curveAmount = -400,
  gap = 12,
  draggable = true,
  dragIntensity = 10,
  fade = true,
  fadePercent = 12,
  style,
  className,
}: CurvedMarqueeProps) {
  const measureRef = useRef<SVGTextElement>(null);
  const pathRef = useRef<SVGPathElement>(null);
  const tspansRef = useRef<Array<SVGTSpanElement | null>>([]);
  const [textWidth, setTextWidth] = useState(0);
  const [pathLength, setPathLength] = useState(0);

  const staticId = useMemo(() => {
    const propsString = `${text}|${curveAmount}|${direction}|${baseVelocity}`;
    let hash = 0;
    for (let i = 0; i < propsString.length; i++) {
      const char = propsString.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash;
    }
    return Math.abs(hash).toString(36);
  }, [text, curveAmount, direction, baseVelocity]);

  const pathId = `curve-${staticId}`;
  const fadeGradientId = `fadeGradient-${staticId}`;
  const fadeMaskId = `fadeMask-${staticId}`;
  const pathD = `M-100,400 Q720,${400 + curveAmount} 1540,400`;

  const isDragging = useRef(false);
  const dragVelocity = useRef(0);
  const effectiveVelocity = (baseVelocity / 100) * MAX_SPEED;
  const actualBaseVelocity =
    direction === "left" ? -effectiveVelocity : effectiveVelocity;
  const dragFactor = dragIntensity * 0.1;
  const gapPx = (gap + 1) * 10;
  const processedText = useMemo(() => text.trim(), [text]);
  const spacing = textWidth + gapPx;

  useEffect(() => {
    let cancelled = false;
    const measure = () => {
      if (!cancelled && measureRef.current) {
        setTextWidth(measureRef.current.getComputedTextLength());
      }
    };
    measure();
    document.fonts?.ready.then(measure);
    return () => {
      cancelled = true;
    };
  }, [
    text,
    font,
    color,
    direction,
    baseVelocity,
    curveAmount,
    gap,
    draggable,
    dragIntensity,
    fade,
    fadePercent,
  ]);

  useEffect(() => {
    if (pathRef.current) {
      setPathLength(pathRef.current.getTotalLength());
    }
  }, [curveAmount]);

  const calculatedRepeats = spacing > 0 ? Math.ceil(pathLength / spacing) + 2 : 0;
  const ready = pathLength > 0 && spacing > 0;

  useEffect(() => {
    if (!ready) return;
    let raf = 0;
    let last = performance.now();
    const tick = (now: number) => {
      const delta = now - last;
      last = now;
      const spans = tspansRef.current;
      if (spans.length > 0) {
        const maxX = (spans.length - 1) * spacing;
        if (isDragging.current) {
          for (const tspan of spans) {
            if (!tspan) continue;
            let x = parseFloat(tspan.getAttribute("x") || "0");
            x += dragVelocity.current;
            if (x < -spacing) x = maxX;
            if (x > maxX) x = -spacing;
            tspan.setAttribute("x", x.toString());
          }
          dragVelocity.current *= 0.9;
          if (Math.abs(dragVelocity.current) < 0.01) {
            dragVelocity.current = 0;
          }
        } else {
          let moveBy = actualBaseVelocity * (delta / 1e3);
          moveBy += dragVelocity.current;
          if (Math.abs(dragVelocity.current) > 0.01) {
            dragVelocity.current *= 0.96;
          } else {
            dragVelocity.current = 0;
          }
          for (const tspan of spans) {
            if (!tspan) continue;
            let x = parseFloat(tspan.getAttribute("x") || "0");
            x += moveBy;
            if (x < -spacing) x = maxX;
            if (x > maxX) x = -spacing;
            tspan.setAttribute("x", x.toString());
          }
        }
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [ready, spacing, actualBaseVelocity]);

  const lastPointerPosition = useRef({ x: 0, y: 0 });

  const handlePointerDown = (e: ReactPointerEvent<SVGTextElement>) => {
    if (!draggable) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    e.currentTarget.style.cursor = "grabbing";
    isDragging.current = true;
    lastPointerPosition.current = { x: e.clientX, y: e.clientY };
    dragVelocity.current = 0;
  };

  const handlePointerMove = (e: ReactPointerEvent<SVGTextElement>) => {
    if (!draggable || !isDragging.current) return;
    const currentPosition = { x: e.clientX, y: e.clientY };
    const deltaX = currentPosition.x - lastPointerPosition.current.x;
    dragVelocity.current = deltaX * dragFactor;
    lastPointerPosition.current = currentPosition;
  };

  const handlePointerUp = (e: ReactPointerEvent<SVGTextElement>) => {
    if (!draggable) return;
    e.currentTarget.releasePointerCapture(e.pointerId);
    e.currentTarget.style.cursor = "grab";
    isDragging.current = false;
  };

  const cursorStyle = draggable ? "grab" : "default";
  const fadeStart = `${fadePercent}%`;
  const fadeEnd = `${100 - fadePercent}%`;

  return (
    <div
      className={className}
      style={{
        visibility: ready ? "visible" : "hidden",
        width: "100%",
        height: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        position: "relative",
        overflow: "hidden",
        ...style,
      }}
    >
      <svg
        viewBox="0 0 1440 800"
        style={{
          position: "absolute",
          top: "50%",
          left: "50%",
          transform: "translate(-50%, -50%)",
          userSelect: "none",
          width: "120%",
          aspectRatio: "1440 / 800",
          overflow: "visible",
          display: "block",
          fill: color,
          fontFamily: font.fontFamily,
          fontSize: font.fontSize,
          letterSpacing: font.letterSpacing,
          lineHeight: font.lineHeight,
        }}
      >
        <text
          ref={measureRef}
          xmlSpace="preserve"
          style={{
            visibility: "hidden",
            opacity: 0,
            pointerEvents: "none",
            cursor: cursorStyle,
          }}
        >
          {processedText}
        </text>
        <defs>
          <path ref={pathRef} id={pathId} d={pathD} fill="none" stroke="transparent" />
          {fade && (
            <>
              <linearGradient id={fadeGradientId} x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" stopColor="white" stopOpacity="0" />
                <stop offset={fadeStart} stopColor="white" stopOpacity="1" />
                <stop offset={fadeEnd} stopColor="white" stopOpacity="1" />
                <stop offset="100%" stopColor="white" stopOpacity="0" />
              </linearGradient>
              <mask id={fadeMaskId}>
                <rect width="100%" height="100%" fill={`url(#${fadeGradientId})`} />
              </mask>
            </>
          )}
        </defs>
        {ready && (
          <text
            fontWeight={font.fontWeight as number}
            xmlSpace="preserve"
            mask={fade ? `url(#${fadeMaskId})` : undefined}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerCancel={handlePointerUp}
            style={{ cursor: cursorStyle }}
          >
            <textPath href={`#${pathId}`} xmlSpace="preserve">
              {Array.from({ length: calculatedRepeats }).map((_, i) => (
                <tspan
                  key={i}
                  x={i * spacing}
                  ref={(el) => {
                    tspansRef.current[i] = el;
                  }}
                >
                  {processedText}
                </tspan>
              ))}
            </textPath>
          </text>
        )}
      </svg>
    </div>
  );
}
