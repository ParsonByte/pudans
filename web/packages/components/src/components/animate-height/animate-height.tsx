import { forwardRef, useEffect, useRef, useState } from "react";
import ReactAnimateHeight, {
  type AnimateHeightProps as ReactAnimateHeightProps,
  type Height,
} from "react-animate-height";
import { useTheme } from "@mui/material";
import { cn } from "@pudans/utils";
import styles from "./animate-height.module.css";

export interface AnimateHeightProps extends Omit<ReactAnimateHeightProps, "height"> {
  open: boolean;
  /** Expanded height. Defaults to auto-measured content height. */
  height?: Height;
}

/**
 * Disclosure wrapper rebuilt from the legacy Common/AutoHeight: react-animate-
 * height plus a ResizeObserver that turns "auto" into the measured pixel height,
 * animated with the theme standard duration and easeInOut curves.
 */
export const AnimateHeight = forwardRef<HTMLDivElement, AnimateHeightProps>(
  (
    { open, height = "auto", duration, easing, className, contentClassName, children, ...props },
    ref,
  ) => {
    const theme = useTheme();
    const contentRef = useRef<HTMLDivElement | null>(null);
    const [measured, setMeasured] = useState<Height>(height);

    useEffect(() => {
      const element = contentRef.current;
      if (!element || typeof ResizeObserver === "undefined") {
        return;
      }
      const observer = new ResizeObserver(() => {
        setMeasured(element.clientHeight);
      });
      observer.observe(element);
      return () => observer.disconnect();
    }, []);

    const expanded = height === "auto" && measured !== "auto" ? measured : height;

    return (
      <ReactAnimateHeight
        ref={ref}
        height={open ? expanded : 0}
        duration={duration ?? theme.transitions.duration.standard}
        easing={easing ?? theme.transitions.easing.easeInOut}
        className={cn(styles.root, className)}
        contentClassName={cn("auto-content", styles.content, contentClassName)}
        contentRef={contentRef}
        disableDisplayNone
        aria-hidden={!open}
        {...props}
      >
        {children}
      </ReactAnimateHeight>
    );
  },
);

AnimateHeight.displayName = "AnimateHeight";
