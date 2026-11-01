import { Tooltip as MuiTooltip, type TooltipProps as MuiTooltipProps } from "@mui/material";
import { cn } from "../../utils/cn";
import styles from "./tooltip.module.css";

export interface TooltipProps extends Omit<MuiTooltipProps, "arrow" | "slotProps"> {
  arrow?: boolean;
  /** Hover/focus delay in milliseconds. */
  delay?: number;
}

/**
 * Delay + arrow tooltip. The tooltip surface uses semantic text/surface tokens,
 * so it automatically inverts in dark mode.
 */
export function Tooltip({
  arrow = true,
  delay = 500,
  className,
  children,
  ...props
}: TooltipProps) {
  return (
    <MuiTooltip
      {...props}
      title={props.title}
      arrow={arrow}
      enterDelay={delay}
      enterNextDelay={delay}
      slotProps={{
        tooltip: { className: cn(styles.tooltip, className) },
        arrow: { className: styles.arrow },
      }}
    >
      {children}
    </MuiTooltip>
  );
}
