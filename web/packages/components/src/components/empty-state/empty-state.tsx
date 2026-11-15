import { forwardRef, type HTMLAttributes, type ReactNode } from "react";
import { Box, Typography, useTheme } from "@mui/material";
import { cn } from "@pudans/shared";
import { Icon } from "../icon";

export type EmptyStateVariant =
  "empty" | "not-found" | "error" | "permission" | "success" | "file-drop";
export type EmptyStateSize = "sm" | "md" | "lg";

const VARIANT_ICON: Record<EmptyStateVariant, string> = {
  empty: "pd:PackageOpen",
  "not-found": "pd:PackageOpen",
  error: "pd:Warning",
  permission: "pd:ShieldLock",
  success: "pd:CheckmarkCircle",
  "file-drop": "pd:CloudArrowIUp",
};

/** Legacy Nothing() default was size=1 → 100px glyph / 50px vertical margin. */
const SIZE_SCALE: Record<EmptyStateSize, number> = { sm: 0.6, md: 1, lg: 1.25 };

export interface EmptyStateProps extends Omit<HTMLAttributes<HTMLDivElement>, "title"> {
  title: ReactNode;
  description?: ReactNode;
  /** Either a rendered node or an icon name such as "pd:PackageOpen". */
  icon?: ReactNode | string;
  variant?: EmptyStateVariant;
  size?: EmptyStateSize;
  action?: ReactNode;
  secondaryAction?: ReactNode;
  /** Compatibility flag: legacy details-card outline (not part of Nothing). */
  bordered?: boolean;
  /** Compatibility flag: EmptyFileList wrapper (margin 0, padding 16px). */
  compact?: boolean;
}

function renderIcon(
  icon: ReactNode | string | undefined,
  variant: EmptyStateVariant,
  scale: number,
  size: EmptyStateSize,
) {
  if (typeof icon !== "string" && icon !== undefined) {
    return icon;
  }
  const glyph = size === "sm" ? 40 : size === "lg" ? 120 : 100;
  return <Icon name={icon || VARIANT_ICON[variant]} size={glyph * scale} />;
}

/**
 * Empty / not-found state rebuilt 1:1 from the legacy Common/Nothing:
 * 100px PackageOpen at palette.action.disabled, h5 24px/500 title and body2
 * secondary copy, centered with the legacy 50px auto margin + 20px top
 * padding. EmptyFileList contributes the full-height flex-center wrapper.
 */
export const EmptyState = forwardRef<HTMLDivElement, EmptyStateProps>(function EmptyState(
  {
    title,
    description,
    icon,
    variant = "empty",
    size = "md",
    action,
    secondaryAction,
    bordered = false,
    compact = false,
    className,
    ...props
  },
  ref,
) {
  const theme = useTheme();
  const scale = SIZE_SCALE[size];
  const tone =
    variant === "error"
      ? theme.palette.error.main
      : variant === "permission"
        ? theme.palette.warning.main
        : variant === "success"
          ? theme.palette.success.main
          : theme.palette.action.disabled;

  return (
    <Box
      ref={ref}
      role={variant === "error" ? "alert" : "status"}
      className={cn(className)}
      sx={{
        display: compact ? "flex" : "block",
        flexDirection: compact ? "column" : undefined,
        alignItems: compact ? "center" : undefined,
        justifyContent: compact ? "center" : undefined,
        ...(compact
          ? { margin: 0, padding: theme.spacing(2) }
          : {
              margin: `${theme.spacing(6.25 * scale)} auto`,
              paddingTop: theme.spacing(2.5),
            }),
        color: tone,
        textAlign: "center",
        ...(bordered
          ? {
              border: `var(--pd-border-width-thin) dashed ${theme.palette.divider}`,
              borderRadius: `${theme.shape.borderRadius}px`,
            }
          : {}),
      }}
      {...props}
    >
      <Box component="span" sx={{ display: "inline-flex", color: "inherit" }} aria-hidden="true">
        {renderIcon(icon, variant, scale, size)}
      </Box>
      <Typography variant="h5" sx={{ fontWeight: 500, color: "inherit" }}>
        {title}
      </Typography>
      {description ? (
        <Typography variant="body2" sx={{ color: "inherit" }}>
          {description}
        </Typography>
      ) : null}
      {action || secondaryAction ? (
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            justifyContent: "center",
            gap: theme.spacing(2),
            marginTop: theme.spacing(2),
          }}
        >
          {action}
          {secondaryAction}
        </Box>
      ) : null}
    </Box>
  );
});

EmptyState.displayName = "EmptyState";
