import { forwardRef, type HTMLAttributes, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./alert.module.css";

export type AlertSeverity = "info" | "success" | "warning" | "error";

export interface AlertProps extends Omit<HTMLAttributes<HTMLDivElement>, "title"> {
  severity?: AlertSeverity;
  title?: ReactNode;
  action?: ReactNode;
  onClose?: () => void;
  closeLabel?: string;
}

const severityIcons = {
  info: "mdi:information-outline",
  success: "mdi:check-circle-outline",
  warning: "mdi:alert-outline",
  error: "mdi:alert-circle-outline",
} as const;

/** Inline status message with inline-only outline icons and a semantic tone. */
export const Alert = forwardRef<HTMLDivElement, AlertProps>(function Alert(
  {
    severity = "info",
    title,
    action,
    onClose,
    closeLabel = "Dismiss",
    className,
    children,
    ...props
  },
  ref,
) {
  return (
    <div ref={ref} role="alert" className={cn(styles.root, styles[severity], className)} {...props}>
      <Icon name={severityIcons[severity]} size={20} className={styles.icon} />
      <div className={styles.content}>
        {title ? <div className={styles.title}>{title}</div> : null}
        {children ? <div className={styles.description}>{children}</div> : null}
      </div>
      {action ? <div className={styles.action}>{action}</div> : null}
      {onClose ? (
        <button type="button" className={styles.close} onClick={onClose} aria-label={closeLabel}>
          <Icon name="mdi:close-outline" size={16} />
        </button>
      ) : null}
    </div>
  );
});

Alert.displayName = "Alert";
