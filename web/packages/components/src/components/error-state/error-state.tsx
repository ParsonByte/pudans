import { Component, type ErrorInfo, type HTMLAttributes, type ReactNode } from "react";
import { Box, Button, Typography, useTheme } from "@mui/material";
import { cn } from "@pudans/utils";

export function resolveErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  if (typeof error === "string") {
    return error;
  }
  if (error && typeof error === "object" && "message" in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string") {
      return message;
    }
  }
  return "Unexpected error";
}

export interface ErrorStateProps extends Omit<HTMLAttributes<HTMLDivElement>, "title"> {
  /** Legacy h2 copy. Defaults to the resolved error message. */
  title?: ReactNode;
  error?: unknown;
  /** Optional retry action (not present in the legacy route fallback). */
  onRetry?: () => void;
  retryLabel?: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
}

function errorText(error: unknown): string {
  if (error instanceof Error) {
    return error.stack ? `${error.toString()}\n\n${error.stack}` : error.toString();
  }
  if (typeof error === "string") {
    return error;
  }
  return resolveErrorMessage(error);
}

/**
 * Legacy Common/ErrorBoundary route fallback rebuilt 1:1: plain 16px padded
 * block, browser-default h1 ":(" in grey, h2 message, and an expandable
 * <details> block with the stringified error + stack in monospace pre/code.
 */
export function ErrorState({
  title,
  error,
  onRetry,
  retryLabel = "Try again",
  description,
  action,
  className,
  ...props
}: ErrorStateProps) {
  const theme = useTheme();
  const message = title ?? description ?? resolveErrorMessage(error);
  return (
    <Box
      className={cn(className)}
      sx={{ padding: theme.spacing(2), color: theme.palette.text.primary }}
      {...props}
    >
      <Typography
        component="h1"
        sx={{
          margin: theme.spacing(0.625, 0),
          fontSize: "2em",
          fontWeight: 700,
          color: theme.palette.grey[500],
        }}
      >
        :(
      </Typography>
      <Typography component="h2" sx={{ margin: theme.spacing(1.875, 0), fontSize: "1.5em", fontWeight: 700 }}>
        {message}
      </Typography>
      {error ? (
        <Box
          component="details"
          sx={{
            "& summary": { cursor: "pointer", color: theme.palette.text.secondary },
            "& pre": {
              margin: 0,
              marginTop: theme.spacing(1),
              fontFamily: "var(--pd-font-mono)",
              fontSize: theme.typography.body2.fontSize,
              whiteSpace: "pre-wrap",
              wordBreak: "break-word",
            },
          }}
        >
          <summary>Error details</summary>
          <pre>
            <code>{errorText(error)}</code>
          </pre>
        </Box>
      ) : null}
      {onRetry ? (
        <Button variant="text" onClick={onRetry} sx={{ marginTop: theme.spacing(1) }}>
          {retryLabel}
        </Button>
      ) : (
        action
      )}
    </Box>
  );
}

export interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode | ((error: Error, reset: () => void) => ReactNode);
  onError?: (error: Error, info: ErrorInfo) => void;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * Class-based error boundary (React only supports boundaries in class
 * components). The default fallback is the legacy ErrorBoundary visual with no
 * injected retry button; pass a fallback render-prop to expose reset.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    this.props.onError?.(error, info);
  }

  reset = (): void => {
    this.setState({ error: null });
  };

  render(): ReactNode {
    const { error } = this.state;
    if (!error) {
      return this.props.children;
    }
    if (typeof this.props.fallback === "function") {
      return this.props.fallback(error, this.reset);
    }
    if (this.props.fallback !== undefined) {
      return this.props.fallback;
    }
    return <ErrorState error={error} />;
  }
}
