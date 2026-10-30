import { forwardRef } from "react";
import ReactAnimateHeight, {
  type AnimateHeightProps as ReactAnimateHeightProps,
  type Height,
} from "react-animate-height";
import { cn } from "../../utils/cn";
import styles from "./animate-height.module.css";

export interface AnimateHeightProps extends Omit<ReactAnimateHeightProps, "height"> {
  open: boolean;
  /** Expanded height. Defaults to "auto"; pass a number for virtualized lists. */
  height?: Height;
}

/**
 * Controlled disclosure primitive used by accordions and collapsible panels.
 * The underlying library animates between 0 and the measured/auto height.
 */
export const AnimateHeight = forwardRef<HTMLDivElement, AnimateHeightProps>(
  (
    {
      open,
      height = "auto",
      duration = 220,
      easing = "cubic-bezier(0.4, 0, 0.2, 1)",
      className,
      contentClassName,
      ...props
    },
    ref,
  ) => (
    <ReactAnimateHeight
      ref={ref}
      height={open ? height : 0}
      duration={duration}
      easing={easing}
      className={cn(styles.root, className)}
      contentClassName={cn(styles.content, contentClassName)}
      aria-hidden={!open}
      {...props}
    />
  ),
);

AnimateHeight.displayName = "AnimateHeight";
