import { type CSSProperties, type ReactNode, useEffect, useRef, useState } from "react";

import { MEMORY_PANEL_HEIGHT_CLASS } from "../lib/model";

const MEMORY_LIST_WIDTH_KEY = "pudans-memory-list-width";

const MEMORY_LIST_WIDTH_DEFAULT = 280;

const MEMORY_LIST_WIDTH_MIN = 220;

const MEMORY_LIST_WIDTH_MAX = 480;

function readStoredWidth(): number {
  if (typeof window === "undefined") {
    return MEMORY_LIST_WIDTH_DEFAULT;
  }
  const stored = Number(window.localStorage.getItem(MEMORY_LIST_WIDTH_KEY));
  if (!Number.isFinite(stored) || stored <= 0) {
    return MEMORY_LIST_WIDTH_DEFAULT;
  }
  return Math.min(MEMORY_LIST_WIDTH_MAX, Math.max(MEMORY_LIST_WIDTH_MIN, stored));
}

type MemorySplitLayoutProps = {
  list: ReactNode;
  detail: ReactNode;
};

export function MemorySplitLayout({ list, detail }: MemorySplitLayoutProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(readStoredWidth);
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    window.localStorage.setItem(MEMORY_LIST_WIDTH_KEY, String(width));
  }, [width]);

  useEffect(() => {
    if (!dragging) {
      return;
    }
    function handlePointerMove(event: PointerEvent) {
      const container = containerRef.current;
      if (!container) {
        return;
      }
      const left = container.getBoundingClientRect().left;
      const next = Math.min(
        MEMORY_LIST_WIDTH_MAX,
        Math.max(MEMORY_LIST_WIDTH_MIN, event.clientX - left),
      );
      setWidth(next);
    }
    function handlePointerUp() {
      setDragging(false);
    }
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };
  }, [dragging]);

  return (
    <div
      ref={containerRef}
      className={`flex flex-col gap-4 min-[960px]:flex-row ${dragging ? "select-none" : ""}`}
      style={{ "--memory-list-width": `${width}px` } as CSSProperties}
    >
      <div className={`w-full min-[960px]:w-[var(--memory-list-width)] min-[960px]:shrink-0`}>
        <div className={`${MEMORY_PANEL_HEIGHT_CLASS} overflow-hidden`}>{list}</div>
      </div>
      <div
        role="separator"
        aria-orientation="vertical"
        aria-label="调整记忆列表宽度"
        tabIndex={0}
        onPointerDown={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onKeyDown={(event) => {
          if (event.key === "ArrowLeft") {
            event.preventDefault();
            setWidth((current) => Math.max(MEMORY_LIST_WIDTH_MIN, current - 16));
          }
          if (event.key === "ArrowRight") {
            event.preventDefault();
            setWidth((current) => Math.min(MEMORY_LIST_WIDTH_MAX, current + 16));
          }
        }}
        className="group -mx-2 hidden w-4 shrink-0 cursor-col-resize touch-none items-center justify-center outline-none min-[960px]:flex"
      >
        <span className="h-16 w-px bg-border transition-colors group-hover:bg-primary group-focus-visible:bg-primary" />
      </div>
      <div className="min-w-0 flex-1">
        <div className={`${MEMORY_PANEL_HEIGHT_CLASS} overflow-hidden`}>{detail}</div>
      </div>
    </div>
  );
}
