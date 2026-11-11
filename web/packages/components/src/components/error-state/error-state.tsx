import { Component, type ErrorInfo, type ReactNode } from "react";
import { Button } from "../button";
import { EmptyState, type EmptyStateProps } from "../empty-state";

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

export interface ErrorStateProps extends Omit<EmptyStateProps, "title" | "variant" | "icon"> {
  title?: ReactNode;
  error?: unknown;
  onRetry?: () => void;
  retryLabel?: ReactNode;
}

/**
 * Error presentation migrated from the legacy ErrorBoundary page. Reuses the
 * EmptyState layout and follows the same spacing/typography tokens.
 */
export function ErrorState({
  title = "Something went wrong",
  error,
  onRetry,
  retryLabel = "Try again",
  description,
  action,
  ...props
}: ErrorStateProps) {
  return (
    <EmptyState
      {...props}
      variant="error"
      title={title}
      description={description ?? resolveErrorMessage(error)}
      action={
        onRetry ? (
          <Button variant="outline" onClick={onRetry}>
            {retryLabel}
          </Button>
        ) : (
          action
        )
      }
    />
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
 * components). Renders ErrorState by default and exposes a reset callback.
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
    return <ErrorState error={error} onRetry={this.reset} />;
  }
}
