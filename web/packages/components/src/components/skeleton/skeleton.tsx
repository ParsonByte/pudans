import { Skeleton as MuiSkeleton, type SkeletonProps as MuiSkeletonProps } from "@mui/material";
import { clsx } from "clsx";
import styles from "./skeleton.module.css";

export type SkeletonVariant = "text" | "circular" | "rectangular" | "rounded";

export interface SkeletonProps extends Omit<MuiSkeletonProps, "variant"> {
  variant?: SkeletonVariant;
}

/** Loading placeholder that uses the neutral surface tokens. */
export function Skeleton({ variant = "text", className, ...props }: SkeletonProps) {
  return (
    <MuiSkeleton
      variant={variant}
      className={clsx(styles.root, styles[variant], className)}
      {...props}
    />
  );
}
