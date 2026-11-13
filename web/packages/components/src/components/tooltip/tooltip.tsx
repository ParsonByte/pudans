import { Tooltip as MuiTooltip, type TooltipProps as MuiTooltipProps } from "@mui/material";

export interface TooltipProps extends Omit<MuiTooltipProps, "arrow" | "slotProps"> {
  /** Legacy MUI Tooltip default: no arrow. */
  arrow?: boolean;
  /** Hover/focus enter delay in milliseconds. Legacy App.tsx sets enterDelay 500. */
  delay?: number;
}

/**
 * Legacy MUI Tooltip: all surface tokens (label size, padding, overlay
 * background, shape radius) come from the MUI theme; the
 * only project override in the legacy App.tsx was enterDelay 500 (applied
 * globally by @pudans/shared/theme), mirrored here through the delay prop.
 */
export function Tooltip({ arrow = false, delay = 500, children, ...props }: TooltipProps) {
  return (
    <MuiTooltip {...props} arrow={arrow} enterDelay={delay}>
      {children}
    </MuiTooltip>
  );
}
