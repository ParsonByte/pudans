export interface PudansApiErrorOptions {
  status?: number;
  code?: string;
  details?: unknown;
  requestId?: string;
  isNetworkError?: boolean;
  retriable?: boolean;
  cause?: unknown;
}

/** Normalized error thrown by every @pudans/api-client request. */
export class PudansApiError extends Error {
  readonly status: number | undefined;
  readonly code: string;
  readonly details: unknown;
  readonly requestId: string | undefined;
  readonly isNetworkError: boolean;
  readonly retriable: boolean;

  constructor(message: string, options: PudansApiErrorOptions = {}) {
    super(message, options.cause === undefined ? undefined : { cause: options.cause });
    this.name = "PudansApiError";
    this.status = options.status;
    this.code = options.code ?? "UNKNOWN_ERROR";
    this.details = options.details;
    this.requestId = options.requestId;
    this.isNetworkError = options.isNetworkError ?? false;
    this.retriable = options.retriable ?? false;
    Object.setPrototypeOf(this, new.target.prototype);
  }

  toJSON(): Record<string, unknown> {
    return {
      name: this.name,
      message: this.message,
      status: this.status,
      code: this.code,
      requestId: this.requestId,
      isNetworkError: this.isNetworkError,
      retriable: this.retriable,
      details: this.details,
    };
  }
}

export function isPudansApiError(error: unknown): error is PudansApiError {
  return error instanceof PudansApiError;
}

export function getErrorMessage(error: unknown, fallback = "Request failed"): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return typeof error === "string" && error.trim() ? error : fallback;
}
