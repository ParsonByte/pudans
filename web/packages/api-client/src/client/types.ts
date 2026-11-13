import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import type { PudansApiError } from "./errors";

export type HttpMethod = "get" | "post" | "put" | "patch" | "delete" | "head" | "options";

export interface RetryOptions {
  /** Number of retries after the first attempt. Set to 0 to disable. */
  retries?: number;
  baseDelayMs?: number;
  maxDelayMs?: number;
  retryOn?: number[];
  methods?: HttpMethod[];
  respectRetryAfter?: boolean;
}

export interface PudansRequestConfig<D = unknown> extends AxiosRequestConfig<D> {
  /** Overrides the client retry policy for this request. false disables retries. */
  pudansRetry?: RetryOptions | false;
  /** Skip Authorization header injection for this request. */
  skipAuth?: boolean;
  /** Reuse a caller-generated request id for distributed tracing. */
  pudansRequestId?: string;
  /** Internal retry counter. Do not set from application code. */
  pudansRetryCount?: number;
}

export interface HttpClientOptions {
  baseURL?: string;
  timeout?: number;
  withCredentials?: boolean;
  headers?: Record<string, string>;
  retry?: RetryOptions | false;
  getAccessToken?: () => string | null | undefined | Promise<string | null | undefined>;
  onUnauthorized?: (error: PudansApiError) => void;
  onForbidden?: (error: PudansApiError) => void;
  onRequest?: (config: InternalAxiosRequestConfig) => void;
  onResponse?: (response: AxiosResponse) => void;
  onError?: (error: PudansApiError) => void;
}

export interface HttpClient {
  readonly instance: import("axios").AxiosInstance;
  request: <T = unknown, D = unknown>(config: PudansRequestConfig<D>) => Promise<T>;
  get: <T = unknown>(url: string, config?: PudansRequestConfig) => Promise<T>;
  post: <T = unknown, D = unknown>(
    url: string,
    data?: D,
    config?: PudansRequestConfig<D>,
  ) => Promise<T>;
  put: <T = unknown, D = unknown>(
    url: string,
    data?: D,
    config?: PudansRequestConfig<D>,
  ) => Promise<T>;
  patch: <T = unknown, D = unknown>(
    url: string,
    data?: D,
    config?: PudansRequestConfig<D>,
  ) => Promise<T>;
  delete: <T = unknown>(url: string, config?: PudansRequestConfig) => Promise<T>;
}

export interface CrudClient<T, TCreate = Partial<T>, TUpdate = Partial<T>, TId = string | number> {
  list: <L = T[]>(config?: PudansRequestConfig) => Promise<L>;
  get: (id: TId, config?: PudansRequestConfig) => Promise<T>;
  create: (payload: TCreate, config?: PudansRequestConfig<TCreate>) => Promise<T>;
  update: (id: TId, payload: TUpdate, config?: PudansRequestConfig<TUpdate>) => Promise<T>;
  remove: (id: TId, config?: PudansRequestConfig) => Promise<void>;
}

declare module "axios" {
  interface AxiosRequestConfig<D = any> {
    pudansRetry?: RetryOptions | false;
    skipAuth?: boolean;
    pudansRequestId?: string;
    pudansRetryCount?: number;
  }

  interface InternalAxiosRequestConfig<D = any> {
    pudansRetry?: RetryOptions | false;
    skipAuth?: boolean;
    pudansRequestId?: string;
    pudansRetryCount?: number;
  }
}
