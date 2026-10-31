import { forwardRef, type HTMLAttributes } from "react";
import { clsx } from "clsx";
import styles from "./badge.module.css";

export type BadgeTone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";
export type BadgeVariant = "soft" | "solid" | "outline";
export type BadgeSize = "sm" | "md";

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
  variant?: BadgeVariant;
  size?: BadgeSize;
  dot?: boolean;
}

/** Compact status badge with soft/solid/outline variants. */
export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(function Badge(
  { tone = "neutral", variant = "soft", size = "md", dot = false, className, children, ...props },
  ref,
) {
  return (
    <span
      ref={ref}
      className={clsx(styles.root, styles[tone], styles[variant], styles[size], className)}
      {...props}
    >
      {dot ? <span className={styles.dot} aria-hidden="true" /> : null}
      {children}
    </span>
  );
});

Badge.displayName = "Badge";
