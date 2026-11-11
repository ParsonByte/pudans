import type { ReactNode } from "react";
import {
  Fade as MuiFade,
  Grow as MuiGrow,
  Slide as MuiSlide,
  type FadeProps,
  type GrowProps,
  type SlideProps,
} from "@mui/material";
import { cn } from "../../utils/cn";
import { motionDurations } from "../../styles/tokens";
import styles from "./motion.module.css";

export interface MotionTimeout {
  enter: number;
  exit: number;
}

export const fadeTimeout: MotionTimeout = {
  enter: motionDurations.entering,
  exit: motionDurations.leaving,
};

export const growTimeout: MotionTimeout = {
  enter: motionDurations.base,
  exit: motionDurations.leaving,
};

export function Fade({ timeout = fadeTimeout, ...props }: FadeProps) {
  return <MuiFade timeout={timeout} {...props} />;
}

export function Grow({ timeout = growTimeout, ...props }: GrowProps) {
  return <MuiGrow timeout={timeout} {...props} />;
}

export function Slide(props: SlideProps) {
  return <MuiSlide timeout={fadeTimeout} {...props} />;
}

export interface AnimatedSwapProps {
  active: boolean;
  activeContent: ReactNode;
  inactiveContent: ReactNode;
  size?: number;
  className?: string;
}

/**
 * Cross-fade between two states (migrated from the legacy NavIconTransition).
 * Both slots are positioned absolutely so the swap never reflows the toolbar.
 */
export function AnimatedSwap({
  active,
  activeContent,
  inactiveContent,
  size = 24,
  className,
}: AnimatedSwapProps) {
  return (
    <span className={cn(styles.swap, className)} style={{ width: size, height: size }}>
      <span
        className={cn(styles.slot, styles.fadeLayer, active ? styles.visible : styles.hidden)}
        aria-hidden={!active}
      >
        {activeContent}
      </span>
      <span
        className={cn(styles.slot, styles.fadeLayer, active ? styles.hidden : styles.visible)}
        aria-hidden={active}
      >
        {inactiveContent}
      </span>
    </span>
  );
}

export { motionDurations, motionEasing, transitions } from "../../styles/tokens";
