import type { ReactNode } from "react";
import {
  Fade as MuiFade,
  Grow as MuiGrow,
  Slide as MuiSlide,
  type FadeProps,
  type GrowProps,
  type SlideProps,
} from "@mui/material";
import { cn } from "@pudans/shared";
import { motionDurations } from "@pudans/shared/theme";
import styles from "./motion.module.css";

export interface MotionTimeout {
  enter: number;
  exit: number;
}

/** MUI Fade/Grow defaults: entering/leaving durations from the legacy baseline. */
export const fadeTimeout: MotionTimeout = {
  enter: motionDurations.entering,
  exit: motionDurations.leaving,
};

export const growTimeout: MotionTimeout = {
  enter: motionDurations.entering,
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
 * Cross-fade between two states, rebuilt from the legacy NavIconTransition:
 * both glyphs are absolutely stacked and swapped with the MUI Fade defaults,
 * so the toolbar never reflows while the icon changes.
 */
export function AnimatedSwap({
  active,
  activeContent,
  inactiveContent,
  size = 24,
  className,
}: AnimatedSwapProps) {
  return (
    <span
      className={cn(styles.swap, className)}
      style={{ width: size, height: size, position: "relative" }}
    >
      <MuiFade in={active} timeout={fadeTimeout} appear>
        <span className={styles.slot} aria-hidden={!active}>
          {activeContent}
        </span>
      </MuiFade>
      <MuiFade in={!active} timeout={fadeTimeout} appear>
        <span className={styles.slot} aria-hidden={active}>
          {inactiveContent}
        </span>
      </MuiFade>
    </span>
  );
}
