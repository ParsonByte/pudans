import { Skeleton as MuiSkeleton, type SkeletonProps as MuiSkeletonProps } from "@mui/material";
import { cn } from "../../utils/cn";
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
      className={cn(styles.root, styles[variant], className)}
      {...props}
    />
  );
}
