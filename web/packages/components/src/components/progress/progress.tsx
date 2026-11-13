import { forwardRef, type HTMLAttributes, type ReactNode } from "react";
import Box from "@mui/material/Box";
import MuiCircularProgress from "@mui/material/CircularProgress";
import MuiLinearProgress from "@mui/material/LinearProgress";
import Typography from "@mui/material/Typography";
import type { Theme } from "@mui/material/styles";
import { cn } from "@pudans/shared";

export type ProgressTone = "brand" | "success" | "warning" | "danger" | "neutral";
export type ProgressSize = "sm" | "md" | "lg";

export interface ProgressProps extends Omit<HTMLAttributes<HTMLDivElement>, "children"> {
  /** 0-100. Omit (or pass undefined) for an indeterminate state. */
  value?: number;
  variant?: "linear" | "circular";
  tone?: ProgressTone;
  size?: ProgressSize;
  label?: ReactNode;
  showValue?: boolean;
  /** Announced to assistive technology, e.g. "Uploading files". */
  ariaLabel?: string;
}

const LINEAR_HEIGHT: Record<ProgressSize, number> = { sm: 0.5, md: 1, lg: 1.5 };

function clamp(value: number | undefined): number | undefined {
  if (value === undefined || Number.isNaN(value)) {
    return undefined;
  }
  return Math.min(100, Math.max(0, value));
}

function toneColor(theme: Theme, tone: ProgressTone, circular = false): string {
  switch (tone) {
    case "success":
      return theme.palette.success.main;
    case "warning":
      return theme.palette.warning.main;
    case "danger":
      return theme.palette.error.main;
    case "neutral":
      return theme.palette.grey[500];
    default:
      // BorderLinearProgress used dedicated light/dark blues; the closest
      // theme tokens are the brand-450 / brand-400 ramps in @pudans/shared/theme.
      return circular
        ? theme.palette.primary.main
        : theme.palette.mode === "light"
          ? "rgb(var(--pd-brand-450))"
          : "rgb(var(--pd-brand-400))";
  }
}

/**
 * Merged legacy BorderLinearProgress (8px high, 5px radius, grey[200]/[800]
 * track) and FacebookCircularProgress (40px double ring, thickness 4, rounded
 * stroke cap) built on MUI LinearProgress/CircularProgress.
 */
export const Progress = forwardRef<HTMLDivElement, ProgressProps>(function Progress(
  {
    value,
    variant = "linear",
    tone = "brand",
    size = "md",
    label,
    showValue = false,
    ariaLabel,
    className,
    ...props
  },
  ref,
) {
  const safeValue = clamp(value);
  const indeterminate = safeValue === undefined;
  const rounded = safeValue === undefined ? undefined : Math.round(safeValue);

  return (
    <Box
      ref={ref}
      className={cn(className)}
      {...props}
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 1,
        width: variant === "circular" ? "auto" : "100%",
      }}
    >
      {label || showValue ? (
        <Box sx={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 1 }}>
          <Typography variant="caption" color="text.secondary">
            {label}
          </Typography>
          {showValue && !indeterminate ? (
            <Typography variant="caption" color="text.secondary">
              {rounded}%
            </Typography>
          ) : null}
        </Box>
      ) : null}

      {variant === "linear" ? (
        <MuiLinearProgress
          variant={indeterminate ? "indeterminate" : "determinate"}
          value={rounded}
          aria-label={ariaLabel}
          sx={(theme) => ({
            width: "100%",
            height: theme.spacing(LINEAR_HEIGHT[size]),
            borderRadius: theme.spacing(0.625),
            backgroundColor: theme.palette.grey[theme.palette.mode === "light" ? 200 : 800],
            "& .MuiLinearProgress-bar": {
              borderRadius: theme.spacing(0.625),
              backgroundColor: toneColor(theme, tone),
            },
          })}
        />
      ) : (
        <Box sx={{ position: "relative", display: "inline-flex", width: "fit-content" }}>
          <MuiCircularProgress
            variant="determinate"
            value={100}
            size={size === "sm" ? 20 : size === "md" ? 40 : 56}
            thickness={4}
            aria-label={ariaLabel}
            sx={(theme) => ({
              color: theme.palette.grey[theme.palette.mode === "light" ? 200 : 800],
            })}
          />
          <MuiCircularProgress
            variant={indeterminate ? "indeterminate" : "determinate"}
            value={rounded}
            size={size === "sm" ? 20 : size === "md" ? 40 : 56}
            thickness={4}
            aria-label={ariaLabel}
            sx={(theme) => ({
              color: toneColor(theme, tone, true),
              position: "absolute",
              left: 0,
              "& .MuiCircularProgress-circle": { strokeLinecap: "round" },
            })}
          />
        </Box>
      )}
    </Box>
  );
});

Progress.displayName = "Progress";

export interface LinearProgressProps extends Omit<ProgressProps, "variant"> {}
export interface CircularProgressProps extends Omit<ProgressProps, "variant"> {}

export const LinearProgress = forwardRef<HTMLDivElement, LinearProgressProps>(
  function LinearProgress(props, ref) {
    return <Progress ref={ref} variant="linear" {...props} />;
  },
);

export const CircularProgress = forwardRef<HTMLDivElement, CircularProgressProps>(
  function CircularProgress(props, ref) {
    return <Progress ref={ref} variant="circular" {...props} />;
  },
);