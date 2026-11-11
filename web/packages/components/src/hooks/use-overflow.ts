import { useLayoutEffect, useState, type RefObject } from "react";

export interface UseOverflowOptions {
  /** Observe the element size (default true). */
  observe?: boolean;
}

/**
 * Detects whether an element's content overflows horizontally.
 * Migrated from the legacy Common/hooks/useOverflow.
 */
export function useOverflow(
  ref: RefObject<HTMLElement | null>,
  onChange?: (overflowing: boolean) => void,
): boolean {
  const [overflowing, setOverflowing] = useState(false);

  useLayoutEffect(() => {
    const node = ref.current;
    if (!node) {
      return;
    }
    const measure = (): void => {
      const next = node.scrollWidth > node.clientWidth + 1;
      setOverflowing(next);
      onChange?.(next);
    };
    measure();
    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", measure);
      return () => window.removeEventListener("resize", measure);
    }
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    return () => observer.disconnect();
  }, [ref, onChange]);

  return overflowing;
}
