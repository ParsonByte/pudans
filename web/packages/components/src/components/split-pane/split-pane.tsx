import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { cn } from "../../utils/cn";
import styles from "./split-pane.module.css";

export interface SplitPaneProps {
  children: [ReactNode, ReactNode];
  direction?: "horizontal" | "vertical";
  initialSize?: number;
  minSize?: number;
  maxSize?: number;
  /** Persist the pane size under this localStorage key. */
  storageKey?: string;
  className?: string;
  ariaLabel?: string;
}

function readStoredSize(key: string | undefined, fallback: number): number {
  if (!key || typeof window === "undefined") {
    return fallback;
  }
  try {
    const raw = window.localStorage.getItem(key);
    const parsed = raw === null ? Number.NaN : Number(raw);
    return Number.isFinite(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

/**
 * Resizable two-pane layout migrated from the legacy Frame SplitHandle
 * (sidebar drag handle). Pointer based, keyboard accessible and optionally
 * persisted per surface.
 */
export function SplitPane({
  children,
  direction = "horizontal",
  initialSize = 320,
  minSize = 200,
  maxSize,
  storageKey,
  className,
  ariaLabel = "Resize pane",
}: SplitPaneProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState<number>(() => readStoredSize(storageKey, initialSize));
  const [dragging, setDragging] = useState(false);
  const horizontal = direction === "horizontal";

  const resolveMax = useCallback((): number => {
    if (maxSize !== undefined) {
      return maxSize;
    }
    const container = containerRef.current;
    if (!container) {
      return Number.POSITIVE_INFINITY;
    }
    return (horizontal ? container.clientWidth : container.clientHeight) - 160;
  }, [horizontal, maxSize]);

  const clamp = useCallback(
    (value: number): number => Math.min(Math.max(value, minSize), Math.max(minSize, resolveMax())),
    [minSize, resolveMax],
  );

  useEffect(() => {
    if (!storageKey || typeof window === "undefined") {
      return;
    }
    try {
      window.localStorage.setItem(storageKey, String(Math.round(size)));
    } catch {
      // persistence is best-effort
    }
  }, [size, storageKey]);

  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>): void => {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLDivElement>): void => {
    if (!dragging || !containerRef.current) {
      return;
    }
    const rect = containerRef.current.getBoundingClientRect();
    const next = horizontal ? event.clientX - rect.left : event.clientY - rect.top;
    setSize(clamp(next));
  };

  const handlePointerUp = (event: React.PointerEvent<HTMLDivElement>): void => {
    event.currentTarget.releasePointerCapture(event.pointerId);
    setDragging(false);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>): void => {
    const step = event.shiftKey ? 48 : 16;
    const delta = horizontal
      ? event.key === "ArrowLeft"
        ? -step
        : event.key === "ArrowRight"
          ? step
          : 0
      : event.key === "ArrowUp"
        ? -step
        : event.key === "ArrowDown"
          ? step
          : 0;
    if (delta !== 0) {
      event.preventDefault();
      setSize((current) => clamp(current + delta));
      return;
    }
    if (event.key === "Home") {
      event.preventDefault();
      setSize(clamp(minSize));
    }
    if (event.key === "End") {
      event.preventDefault();
      setSize(clamp(resolveMax()));
    }
  };

  const paneStyle = horizontal ? { width: size, flexBasis: size } : { height: size };

  return (
    <div
      ref={containerRef}
      className={cn(
        styles.root,
        horizontal ? styles.horizontal : styles.vertical,
        dragging && styles.dragging,
        className,
      )}
    >
      <div className={styles.pane} style={paneStyle}>
        {children[0]}
      </div>
      <div
        role="separator"
        aria-orientation={horizontal ? "vertical" : "horizontal"}
        aria-label={ariaLabel}
        aria-valuenow={Math.round(size)}
        aria-valuemin={minSize}
        aria-valuemax={maxSize}
        tabIndex={0}
        className={styles.divider}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onKeyDown={handleKeyDown}
      >
        <span className={styles.grip} aria-hidden="true" />
      </div>
      <div className={cn(styles.pane, styles.secondary)}>{children[1]}</div>
    </div>
  );
}
