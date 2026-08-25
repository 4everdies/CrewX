"use client";

import { motion, useMotionValue, useSpring } from "framer-motion";
import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";

const RANGE_PER_POINT = 9;
const MAX_PULL = 0.34;

interface BorderOptions {
  color: string;
  width: number;
}

export interface MagneticHoverButtonProps {
  label?: string;
  children?: ReactNode;
  link?: string;
  newTab?: boolean;
  font?: CSSProperties;
  fill?: string;
  textColor?: string;
  sweepColor?: string;
  sweepTextColor?: string;
  radius?: number;
  magnet?: number;
  paddingX?: number;
  paddingY?: number;
  transition?: Record<string, unknown>;
  border?: boolean;
  borderOptions?: BorderOptions;
  style?: CSSProperties;
  className?: string;
  onClick?: () => void;
}

export default function MagneticHoverButton({
  label = "Magnetic Hover",
  children,
  link = "",
  newTab = false,
  font = {
    fontFamily: "Inter, system-ui, sans-serif",
    fontWeight: 600,
    fontSize: 15,
    lineHeight: "1em",
    letterSpacing: "-0.01em",
    textAlign: "left",
  },
  fill = "#FFFFFF",
  textColor = "#000000",
  sweepColor = "#EF4444",
  sweepTextColor = "#FFFFFF",
  paddingX = 36,
  paddingY = 16,
  radius = 100,
  magnet = 10,
  transition = {
    type: "tween",
    ease: "easeInOut",
    duration: 0.35,
  },
  border = true,
  borderOptions = { color: "#FFFFFF", width: 1 },
  style,
  className,
  onClick,
}: MagneticHoverButtonProps) {
  const ref = useRef<HTMLAnchorElement>(null);
  const [hover, setHover] = useState(false);
  const [origin, setOrigin] = useState({ x: 0, y: 0, d: 0 });
  const hoverRef = useRef(false);
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const sx = useSpring(x, { stiffness: 260, damping: 18, mass: 0.4 });
  const sy = useSpring(y, { stiffness: 260, damping: 18, mass: 0.4 });

  const borderColor = borderOptions?.color ?? "#FFFFFF";
  const borderWidth = border ? borderOptions?.width ?? 0 : 0;

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const node: HTMLAnchorElement = el;

    const pull = (magnet / 20) * MAX_PULL;
    const reach = magnet * RANGE_PER_POINT;

    function onMove(event: PointerEvent) {
      const rect = node.getBoundingClientRect();
      const cx = rect.left + rect.width / 2 - sx.get();
      const cy = rect.top + rect.height / 2 - sy.get();

      const dx = event.clientX - cx;
      const dy = event.clientY - cy;

      const inside =
        event.clientX >= rect.left &&
        event.clientX <= rect.right &&
        event.clientY >= rect.top &&
        event.clientY <= rect.bottom;

      const edgeX = Math.max(0, Math.abs(dx) - rect.width / 2);
      const edgeY = Math.max(0, Math.abs(dy) - rect.height / 2);
      const gap = Math.hypot(edgeX, edgeY);

      if (inside !== hoverRef.current) {
        const lx = Math.max(0, Math.min(rect.width, event.clientX - rect.left));
        const ly = Math.max(0, Math.min(rect.height, event.clientY - rect.top));
        const d = 2 * Math.hypot(rect.width, rect.height);
        setOrigin({ x: lx, y: ly, d });
        hoverRef.current = inside;
        setHover(inside);
      }

      if (gap > reach) {
        x.set(0);
        y.set(0);
        return;
      }
      const falloff = reach === 0 ? 0 : 1 - gap / reach;
      x.set(dx * pull * falloff);
      y.set(dy * pull * falloff);
    }

    function onLeave() {
      x.set(0);
      y.set(0);
      hoverRef.current = false;
      setHover(false);
    }

    window.addEventListener("pointermove", onMove);
    document.addEventListener("pointerleave", onLeave);
    return () => {
      window.removeEventListener("pointermove", onMove);
      document.removeEventListener("pointerleave", onLeave);
    };
  }, [magnet, x, y, sx, sy]);

  return (
    <motion.a
      ref={ref}
      href={link || undefined}
      target={link && newTab ? "_blank" : undefined}
      rel={link && newTab ? "noopener noreferrer" : undefined}
      onClick={onClick}
      className={className}
      style={{
        position: "relative",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        boxSizing: "border-box",
        padding: `${paddingY}px ${paddingX}px`,
        borderRadius: radius,
        background: fill,
        border: borderWidth > 0 ? `${borderWidth}px solid ${borderColor}` : "none",
        cursor: "pointer",
        overflow: "hidden",
        textDecoration: "none",
        whiteSpace: "nowrap",
        x: sx,
        y: sy,
        boxShadow: hover
          ? "0 16px 40px rgba(120,145,255,0.28)"
          : "0 8px 22px rgba(0,0,0,0.14)",
        ...font,
        ...style,
      }}
    >
      <motion.span
        aria-hidden
        initial={false}
        animate={{ scale: hover ? 1 : 0 }}
        transition={transition}
        style={{
          position: "absolute",
          top: origin.y,
          left: origin.x,
          width: origin.d,
          height: origin.d,
          marginLeft: -origin.d / 2,
          marginTop: -origin.d / 2,
          borderRadius: "50%",
          background: sweepColor,
          transformOrigin: "center",
          pointerEvents: "none",
        }}
      />
      <motion.span
        initial={false}
        animate={{ color: hover ? sweepTextColor : textColor }}
        transition={transition}
        style={{
          position: "relative",
          zIndex: 1,
          display: "inline-flex",
          alignItems: "center",
          gap: 10,
        }}
      >
        {children ?? label}
      </motion.span>
    </motion.a>
  );
}
