import { useCallback, useEffect, useRef, useState } from "react";

export interface DelayedHoverHandlers {
  hovered: boolean;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onFocus: () => void;
  onBlur: () => void;
}

/**
 * Hover intent with delay. Migrated from the legacy Common/hooks/delayedHover
 * (which was coupled to material-ui-popup-state); now a generic DOM hook.
 */
export function useDelayedHover(
  delayMs = 200,
  onHoverChange?: (hovered: boolean) => void,
): DelayedHoverHandlers {
  const [hovered, setHovered] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const delayRef = useRef(delayMs);
  delayRef.current = delayMs;

  const clear = useCallback(() => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  }, []);

  const schedule = useCallback(
    (next: boolean) => {
      clear();
      timer.current = setTimeout(() => {
        setHovered(next);
        onHoverChange?.(next);
      }, delayRef.current);
    },
    [clear, onHoverChange],
  );

  const show = useCallback(() => schedule(true), [schedule]);
  const hide = useCallback(() => {
    clear();
    setHovered(false);
    onHoverChange?.(false);
  }, [clear, onHoverChange]);

  useEffect(() => clear, [clear]);

  return { hovered, onMouseEnter: show, onMouseLeave: hide, onFocus: show, onBlur: hide };
}
