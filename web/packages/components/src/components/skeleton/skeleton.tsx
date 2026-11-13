import { Skeleton as MuiSkeleton, type SkeletonProps as MuiSkeletonProps } from "@mui/material";
import { cn } from "@pudans/shared";

export type SkeletonVariant = "text" | "circular" | "rectangular" | "rounded";

export interface SkeletonProps extends Omit<MuiSkeletonProps, "variant"> {
  variant?: SkeletonVariant;
}

/**
 * Plain MUI Skeleton. Background, wave animation (App.tsx
 * MuiSkeleton.defaultProps.animation) and variant radii are all inherited from
 * the theme; no local colour overrides remain.
 */
export function Skeleton({ variant = "text", className, ...props }: SkeletonProps) {
  return <MuiSkeleton variant={variant} className={cn(className)} {...props} />;
}
