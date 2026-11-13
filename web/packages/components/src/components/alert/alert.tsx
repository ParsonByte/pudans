import { forwardRef, type ReactNode } from "react";
import MuiAlert, { type AlertProps as MuiAlertProps } from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { cn } from "@pudans/shared";

export type AlertSeverity = "info" | "success" | "warning" | "error";

export interface AlertProps extends Omit<MuiAlertProps, "severity" | "action" | "onClose" | "title"> {
  severity?: AlertSeverity;
  title?: ReactNode;
  action?: ReactNode;
  onClose?: () => void;
  closeLabel?: string;
}

/**
 * Legacy-facing wrapper around MUI Alert (standard variant). Typography,
 * padding, icon slot and warning icon mapping all come from the legacy theme
 * (App.tsx MuiAlert iconMapping), so no local styling is applied.
 */
export const Alert = forwardRef<HTMLDivElement, AlertProps>(function Alert(
  { severity = "info", title, action, onClose, closeLabel = "Dismiss", className, children, ...props },
  ref,
) {
  return (
    <MuiAlert
      ref={ref}
      severity={severity}
      action={action}
      onClose={onClose ? () => onClose() : undefined}
      closeText={closeLabel}
      className={cn(className)}
      {...props}
    >
      {title ? <AlertTitle>{title}</AlertTitle> : null}
      {children}
    </MuiAlert>
  );
});

Alert.displayName = "Alert";
