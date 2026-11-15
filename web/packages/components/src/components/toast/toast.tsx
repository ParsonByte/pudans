import { forwardRef, useCallback, useEffect, useMemo, useRef, type ReactNode } from "react";
import { Box, type SnackbarOrigin } from "@mui/material";
import { styled } from "@mui/material/styles";
import {
  MaterialDesignContent,
  SnackbarProvider,
  useSnackbar,
  type CustomContentProps,
  type SnackbarKey,
} from "notistack";

export type ToastSeverity = "info" | "success" | "warning" | "error";

export interface ToastOptions {
  message: ReactNode;
  severity?: ToastSeverity;
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
  /** Legacy notistack default: 3 stacked toasts. */
  maxSnack?: number;
  /** Legacy App.tsx SnackbarProvider anchor: bottom-left. */
  anchorOrigin?: SnackbarOrigin;
  /** Legacy notistack default: 5000 ms. */
  autoHideDuration?: number;
}

/**
 * notistack clones a custom snackbar and requires it to forward its ref to the
 * DOM node. A plain function component drops the ref and crashes the provider
 * on the first toast.
 */
const LegacyContentBase = forwardRef<HTMLDivElement, CustomContentProps>((props, ref) => (
  <MaterialDesignContent {...props} ref={ref} />
));
LegacyContentBase.displayName = "StyledSnackbarContent";

/**
 * Legacy App.tsx StyledMaterialDesignContent: 12px radius and palette-toned
 * success / error / warning backgrounds. "default" keeps the notistack surface
 * colour (legacy default variant); "info" is intentionally not overridden so it
 * keeps the notistack default, exactly like the legacy provider.
 */
const StyledMaterialDesignContent = styled(LegacyContentBase)(({ theme }) => ({
  "&.notistack-MuiContent": {
    borderRadius: `${theme.shape.borderRadius}px`,
  },
  "&.notistack-MuiContent-success": {
    backgroundColor: theme.palette.success.main,
  },
  "&.notistack-MuiContent-error": {
    backgroundColor: theme.palette.error.main,
  },
  "&.notistack-MuiContent-warning": {
    backgroundColor: theme.palette.warning.main,
  },
}));

function toastMessage(message: ReactNode, title: ReactNode): ReactNode {
  if (!title) {
    return message;
  }
  return (
    <Box>
      <Box component="div" sx={{ fontWeight: (theme) => theme.typography.fontWeightMedium, mb: 0.5 }}>
        {title}
      </Box>
      <Box component="div">{message}</Box>
    </Box>
  );
}

const toastComponents = {
  success: StyledMaterialDesignContent,
  error: StyledMaterialDesignContent,
  warning: StyledMaterialDesignContent,
  default: StyledMaterialDesignContent,
};

/** Legacy App.tsx SnackbarProvider: bottom-left, maxSnack 3, 5000 ms. */
export function ToastProvider({
  children,
  maxSnack = 3,
  anchorOrigin = { vertical: "bottom", horizontal: "left" },
  autoHideDuration = 5000,
}: ToastProviderProps) {
  return (
    <SnackbarProvider
      maxSnack={maxSnack}
      anchorOrigin={anchorOrigin}
      autoHideDuration={autoHideDuration}
      Components={toastComponents}
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
      const key = enqueueSnackbar(toastMessage(options.message, options.title), {
        variant: options.severity ?? "default",
        autoHideDuration: options.duration,
        action: options.action,
      });
      return String(key);
    },
    [enqueueSnackbar],
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
  severity?: ToastSeverity;
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
  duration = 5000,
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

    const key = enqueueSnackbar(toastMessage(message, title), {
      variant: severity,
      autoHideDuration: duration,
      action,
      className,
    });
    keyRef.current = key;

    return () => {
      if (keyRef.current !== null) {
        closeSnackbar(keyRef.current);
        keyRef.current = null;
      }
    };
  }, [action, className, closeSnackbar, duration, enqueueSnackbar, message, open, severity, title]);

  useEffect(() => {
    if (!open || onClose === undefined) {
      return undefined;
    }
    const timer = window.setTimeout(() => onClose(), duration);
    return () => window.clearTimeout(timer);
  }, [duration, onClose, open]);

  return null;
}

/** Standalone controlled toast. Prefer ToastProvider + useToast for app queues. */
export function Toast({
  anchorOrigin = { vertical: "bottom", horizontal: "left" },
  ...props
}: ToastProps) {
  return (
    <SnackbarProvider
      maxSnack={1}
      anchorOrigin={anchorOrigin}
      autoHideDuration={null}
      Components={toastComponents}
      preventDuplicate
    >
      <ToastBridge {...props} />
    </SnackbarProvider>
  );
}