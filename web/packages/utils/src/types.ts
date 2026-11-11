export type Nullable<T> = T | null;
export type Maybe<T> = T | null | undefined;
export type Awaitable<T> = T | Promise<T>;

export type DeepPartial<T> = T extends (...args: never[]) => unknown
  ? T
  : T extends readonly (infer U)[]
    ? DeepPartial<U>[]
    : T extends object
      ? { [K in keyof T]?: DeepPartial<T[K]> }
      : T;

/** Envelope used by pudans HTTP APIs. */
export interface ApiEnvelope<T> {
  data: T;
  code?: string;
  message?: string;
  requestId?: string;
}

/** RFC 7807-ish problem payload returned by the backend. */
export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  requestId?: string;
  errors?: Record<string, string[]>;
}

export interface Pagination {
  page: number;
  pageSize: number;
  total: number;
}

export interface PageResult<T> {
  items: T[];
  pagination: Pagination;
}

export type AsyncState<T, E = unknown> =
  | { status: "idle" }
  | { status: "loading"; previous?: T }
  | { status: "success"; data: T }
  | { status: "error"; error: E };

export interface SelectOption<T = string> {
  label: string;
  value: T;
  disabled?: boolean;
  description?: string;
}

export type ColorModePreference = "light" | "dark" | "system";
export type ResolvedColorMode = "light" | "dark";

export type ValueOf<T> = T[keyof T];
export type DistributiveOmit<T, K extends keyof T> = T extends unknown ? Omit<T, K> : never;
