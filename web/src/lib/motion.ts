import type { Variants } from "motion/react";

export const MOTION_DURATION = {
  fast: 0.12,
  base: 0.24,
  slow: 0.4,
} as const;

export const MOTION_EASE: [number, number, number, number] = [0.22, 1, 0.36, 1];

export const pageVariants: Variants = {
  initial: { opacity: 0, y: 8 },
  animate: {
    opacity: 1,
    y: 0,
    transition: { duration: MOTION_DURATION.base, ease: MOTION_EASE },
  },
};
