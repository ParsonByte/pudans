import { forwardRef, type HTMLAttributes, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./empty-state.module.css";

export type EmptyStateVariant =
  "empty" | "not-found" | "error" | "permission" | "success" | "file-drop";
export type EmptyStateSize = "sm" | "md" | "lg";

const VARIANT_ICON: Record<EmptyStateVariant, string> = {
  empty: "cr:PackageOpen",
  "not-found": "cr:DismissCircleFilled",
  error: "cr:Warning",
  permission: "cr:ShieldLock",
  success: "cr:CheckmarkCircle",
  "file-drop": "cr:CloudArrowIUp",
};

export interface EmptyStateProps extends Omit<HTMLAttributes<HTMLDivElement>, "title"> {
  title: ReactNode;
  description?: ReactNode;
  /** Either a rendered node or an icon name such as "cr:PackageOpen". */
  icon?: ReactNode | string;
  variant?: EmptyStateVariant;
  size?: EmptyStateSize;
  action?: ReactNode;
  secondaryAction?: ReactNode;
  bordered?: boolean;
  compact?: boolean;
}

function renderIcon(icon: ReactNode | string, variant: EmptyStateVariant, size: EmptyStateSize) {
  if (typeof icon !== "string") {
    return icon;
  }
  const dimension = size === "sm" ? 24 : size === "lg" ? 40 : 32;
  return <Icon name={icon || VARIANT_ICON[variant]} size={dimension} />;
}

/**
 * Unified empty / not-found / error / permission state.
 * Migrated from the legacy Cloudreve Nothing, EmptyFileList and NoMatch
 * components and generalised for every pudans surface.
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
  return (
    <div
      ref={ref}
      role={variant === "error" ? "alert" : "status"}
      className={cn(
        styles.root,
        styles[variant],
        styles[size],
        bordered && styles.bordered,
        compact && styles.compact,
        className,
      )}
      {...props}
    >
      <span className={styles.icon} aria-hidden="true">
        {renderIcon(icon, variant, size)}
      </span>
      <div className={styles.content}>
        <p className={styles.title}>{title}</p>
        {description ? <p className={styles.description}>{description}</p> : null}
      </div>
      {action || secondaryAction ? (
        <div className={styles.actions}>
          {action}
          {secondaryAction}
        </div>
      ) : null}
    </div>
  );
});

EmptyState.displayName = "EmptyState";
