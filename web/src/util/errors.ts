import type { ProblemDetail } from "./api-types";

/** Normalized API failure carrying the stable machine code from the backend. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: unknown;

  constructor(message: string, options: { status?: number; code?: string; details?: unknown } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = options.status ?? 0;
    this.code = options.code ?? "unknown";
    this.details = options.details;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function problemCodeFrom(data: unknown): string | undefined {
  if (typeof data !== "object" || data === null) {
    return undefined;
  }
  const problem = data as ProblemDetail;
  return typeof problem.code === "string" && problem.code.trim() ? problem.code : undefined;
}
