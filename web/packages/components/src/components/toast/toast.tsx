import { useCallback, useEffect, useMemo, useRef, type ReactNode } from "react";
import { SnackbarProvider, useSnackbar, type SnackbarKey } from "notistack";
import type { SnackbarOrigin } from "@mui/material";
import { clsx } from "clsx";
import { Alert, type AlertSeverity } from "../alert";
import styles from "./toast.module.css";

export interface ToastOptions {
  message: ReactNode;
  severity?: AlertSeverity;
  title?: ReactNode;
  duration?: number;
  action?: ReactNode;
}

export interface ToastContextValue {
  show: (options: ToastOptions) => string;
  success: (message: ReactNode, options?: Omit<ToastOptions, "message" | "severity">) => string;
  info: (message: ReactNode, options?: Omit<ToastOptions, "message" | "severity">) => string;
  warning: (message: ReactNode, options?: Omit<ToastOptions, "message" | "severity">) => string;
  error: (message: ReactNode, options?: Omit<ToastOptions, "message" | "severity">) => string;
  dismiss: (id: string) => void;
}

export interface ToastProviderProps {
  children: ReactNode;
  /**
   * Maximum visible queue size. notistack automatically queues and dismisses
   * older items after this limit.
   */
  maxSnack?: number;
  anchorOrigin?: SnackbarOrigin;
  autoHideDuration?: number;
}

function createContent(options: ToastOptions, close: (key: SnackbarKey) => void) {
  return (key: SnackbarKey): ReactNode => (
    <Alert
      severity={options.severity ?? "info"}
      title={options.title}
      action={options.action}
      onClose={() => close(key)}
      className={styles.alert}
    >
      {options.message}
    </Alert>
  );
}

/** notistack-backed global toast queue. */
export function ToastProvider({
  children,
  maxSnack = 4,
  anchorOrigin = { vertical: "bottom", horizontal: "right" },
  autoHideDuration = 4000,
}: ToastProviderProps) {
  return (
    <SnackbarProvider
      maxSnack={maxSnack}
      anchorOrigin={anchorOrigin}
      autoHideDuration={autoHideDuration}
      preventDuplicate
    >
      {children}
    </SnackbarProvider>
  );
}

export function useToast(): ToastContextValue {
  const { enqueueSnackbar, closeSnackbar } = useSnackbar();

  const show = useCallback(
    (options: ToastOptions): string => {
      const key = enqueueSnackbar(options.message, {
        variant: options.severity ?? "info",
        autoHideDuration: options.duration ?? 4000,
        content: createContent(options, closeSnackbar),
      });
      return String(key);
    },
    [closeSnackbar, enqueueSnackbar],
  );

  const success = useCallback(
    (message: ReactNode, options?: Omit<ToastOptions, "message" | "severity">) =>
      show({ ...options, message, severity: "success" }),
    [show],
  );
  const info = useCallback(
    (message: ReactNode, options?: Omit<ToastOptions, "message" | "severity">) =>
      show({ ...options, message, severity: "info" }),
    [show],
  );
  const warning = useCallback(
    (message: ReactNode, options?: Omit<ToastOptions, "message" | "severity">) =>
      show({ ...options, message, severity: "warning" }),
    [show],
  );
  const error = useCallback(
    (message: ReactNode, options?: Omit<ToastOptions, "message" | "severity">) =>
      show({ ...options, message, severity: "error" }),
    [show],
  );

  return useMemo(
    () => ({
      show,
      success,
      info,
      warning,
      error,
      dismiss: (id: string) => closeSnackbar(id),
    }),
    [closeSnackbar, error, info, show, success, warning],
  );
}

export interface ToastProps {
  open: boolean;
  message: ReactNode;
  severity?: AlertSeverity;
  title?: ReactNode;
  duration?: number;
  action?: ReactNode;
  onClose?: () => void;
  anchorOrigin?: SnackbarOrigin;
  className?: string;
}

function ToastBridge({
  open,
  message,
  severity = "info",
  title,
  duration = 4000,
  action,
  onClose,
  className,
}: ToastProps) {
  const { enqueueSnackbar, closeSnackbar } = useSnackbar();
  const keyRef = useRef<SnackbarKey | null>(null);

  useEffect(() => {
    if (!open) {
      if (keyRef.current !== null) {
        closeSnackbar(keyRef.current);
        keyRef.current = null;
      }
      return;
    }

    const key = enqueueSnackbar(message, {
      variant: severity,
      autoHideDuration: duration,
      content: (snackbarKey) => (
        <Alert
          severity={severity}
          title={title}
          action={action}
          onClose={() => {
            onClose?.();
            closeSnackbar(snackbarKey);
          }}
          className={clsx(styles.alert, className)}
        >
          {message}
        </Alert>
      ),
      onClose: () => {
        onClose?.();
        keyRef.current = null;
      },
    });
    keyRef.current = key;

    return () => {
      if (keyRef.current !== null) {
        closeSnackbar(keyRef.current);
        keyRef.current = null;
      }
    };
  }, [
    action,
    className,
    closeSnackbar,
    duration,
    enqueueSnackbar,
    message,
    onClose,
    open,
    severity,
    title,
  ]);

  return null;
}

/** Standalone controlled toast. Prefer ToastProvider + useToast for app queues. */
export function Toast({
  anchorOrigin = { vertical: "bottom", horizontal: "right" },
  ...props
}: ToastProps) {
  return (
    <SnackbarProvider
      maxSnack={1}
      anchorOrigin={anchorOrigin}
      autoHideDuration={null}
      preventDuplicate
    >
      <ToastBridge {...props} />
    </SnackbarProvider>
  );
}
