import { forwardRef, type HTMLAttributes, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import styles from "./progress.module.css";

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

function clamp(value: number | undefined): number | undefined {
  if (value === undefined || Number.isNaN(value)) {
    return undefined;
  }
  return Math.min(100, Math.max(0, value));
}

/**
 * Unified progress indicator. Merges the legacy BorderLinearProgress and the
 * double-ring CircularProgress into one token-driven component with a tone
 * palette, sizes and accessible progressbar semantics.
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
    <div
      ref={ref}
      className={cn(styles.root, styles[variant], styles[size], styles[tone], className)}
      {...props}
    >
      {label || showValue ? (
        <div className={styles.header}>
          {label ? <span className={styles.label}>{label}</span> : <span />}
          {showValue && !indeterminate ? <span className={styles.value}>{rounded}%</span> : null}
        </div>
      ) : null}

      {variant === "linear" ? (
        <div
          className={styles.track}
          role="progressbar"
          aria-label={ariaLabel}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={indeterminate ? undefined : rounded}
        >
          <div
            className={cn(styles.bar, indeterminate && styles.indeterminate)}
            style={indeterminate ? undefined : { width: rounded + "%" }}
          />
        </div>
      ) : (
        <svg
          className={styles.ring}
          viewBox="0 0 48 48"
          role="progressbar"
          aria-label={ariaLabel}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={indeterminate ? undefined : rounded}
        >
          <circle className={styles.ringTrack} cx="24" cy="24" r="20" />
          <circle
            className={cn(styles.ringBar, indeterminate && styles.ringIndeterminate)}
            cx="24"
            cy="24"
            r="20"
            pathLength={100}
            strokeDasharray={indeterminate ? "28 72" : rounded + " 100"}
          />
        </svg>
      )}
    </div>
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
