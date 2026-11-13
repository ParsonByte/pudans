import { forwardRef, type HTMLAttributes, type Ref } from "react";
import Box from "@mui/material/Box";
import Chip, { type ChipProps } from "@mui/material/Chip";
import { cn } from "@pudans/shared";

export type BadgeTone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";
export type BadgeVariant = "soft" | "solid" | "outline";
export type BadgeSize = "sm" | "md";

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
  variant?: BadgeVariant;
  size?: BadgeSize;
  dot?: boolean;
}

const CHIP_COLORS: Record<BadgeTone, ChipProps["color"]> = {
  neutral: "default",
  brand: "primary",
  success: "success",
  warning: "warning",
  danger: "error",
  info: "info",
};

const CHIP_SIZES: Record<BadgeSize, ChipProps["size"]> = {
  sm: "small",
  md: "medium",
};

/**
 * Legacy-faithful status chip. The old project used plain MUI Chip (default
 * filled / outlined, 32px medium, 24px small, body2 13px) plus App.tsx
 * SquareChip radius overrides; no custom soft-pill surface is introduced here.
 */
export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(function Badge(
  { tone = "neutral", variant = "soft", size = "md", dot = false, className, children, ...props },
  ref,
) {
  return (
    <Chip
      {...(props as unknown as ChipProps)}
      ref={ref as unknown as Ref<HTMLDivElement>}
      color={CHIP_COLORS[tone]}
      variant={variant === "outline" ? "outlined" : "filled"}
      size={CHIP_SIZES[size]}
      label={children ?? ""}
      icon={
        dot ? (
          <Box
            component="span"
            sx={{
              width: "0.5em",
              height: "0.5em",
              borderRadius: "50%",
              backgroundColor: "currentColor",
            }}
          />
        ) : undefined
      }
      className={cn(className)}
    />
  );
});

Badge.displayName = "Badge";
