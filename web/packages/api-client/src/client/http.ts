import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import { PudansApiError } from "./errors";
import type {
  CrudClient,
  HttpClient,
  HttpClientOptions,
  HttpMethod,
  PudansRequestConfig,
  RetryOptions,
} from "./types";

const DEFAULT_RETRY_POLICY: Required<RetryOptions> = {
  retries: 2,
  baseDelayMs: 300,
  maxDelayMs: 4_000,
  retryOn: [408, 425, 429, 500, 502, 503, 504],
  methods: ["get", "head", "options"],
  respectRetryAfter: true,
};

const RETRYABLE_ERROR_CODES = new Set([
  "ECONNABORTED",
  "ECONNRESET",
  "ENETUNREACH",
  "ETIMEDOUT",
  "ERR_NETWORK",
]);

function createRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `req_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readHeader(
  headers: AxiosResponse["headers"] | undefined,
  name: string,
): string | undefined {
  if (!headers) {
    return undefined;
  }
  const value = headers[name] ?? headers[name.toLowerCase()];
  if (Array.isArray(value)) {
    return value[0];
  }
  return typeof value === "string" ? value : value === undefined ? undefined : String(value);
}

interface ProblemDetails {
  message?: string;
  code?: string;
  requestId?: string;
}

function extractProblemDetails(data: unknown): ProblemDetails {
  if (typeof data === "string") {
    return { message: data };
  }
  if (!isRecord(data)) {
    return {};
  }

  const message = [data.detail, data.message, data.title].find(
    (value): value is string => typeof value === "string" && value.trim().length > 0,
  );

  return {
    message,
    code: typeof data.code === "string" ? data.code : undefined,
    requestId: typeof data.requestId === "string" ? data.requestId : undefined,
  };
}

function isRetryableStatus(status: number | undefined, retryOn: number[]): boolean {
  return status !== undefined && retryOn.includes(status);
}

function normalizeError(error: unknown): PudansApiError {
  if (error instanceof PudansApiError) {
    return error;
  }

  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const problem = extractProblemDetails(error.response?.data);
    const requestId =
      error.config?.pudansRequestId ??
      problem.requestId ??
      readHeader(error.response?.headers, "x-request-id");
    const isNetworkError = error.response === undefined;
    const retriable =
      RETRYABLE_ERROR_CODES.has(error.code ?? "") ||
      isNetworkError ||
      isRetryableStatus(status, DEFAULT_RETRY_POLICY.retryOn);

    return new PudansApiError(problem.message ?? error.message ?? "Request failed", {
      status,
      code: problem.code ?? error.code ?? (status ? `HTTP_${status}` : "NETWORK_ERROR"),
      details: error.response?.data,
      requestId,
      isNetworkError,
      retriable,
      cause: error,
    });
  }

  if (error instanceof Error) {
    return new PudansApiError(error.message, { cause: error });
  }

  return new PudansApiError("Unknown request error", { details: error });
}

function resolveRetryPolicy(
  config: InternalAxiosRequestConfig,
  fallback: RetryOptions | false | undefined,
): RetryOptions | null {
  if (config.pudansRetry === false || fallback === false) {
    return null;
  }
  return {
    ...DEFAULT_RETRY_POLICY,
    ...(fallback ?? {}),
    ...(config.pudansRetry ?? {}),
  };
}

function shouldRetry(
  error: AxiosError,
  config: InternalAxiosRequestConfig,
  retryPolicy: RetryOptions | null,
): boolean {
  if (!retryPolicy) {
    return false;
  }
  if (config.signal?.aborted || error.code === "ERR_CANCELED") {
    return false;
  }

  const retries = retryPolicy.retries ?? 0;
  if ((config.pudansRetryCount ?? 0) >= retries) {
    return false;
  }

  const method = (config.method ?? "get").toLowerCase() as HttpMethod;
  if (!(retryPolicy.methods ?? DEFAULT_RETRY_POLICY.methods).includes(method)) {
    return false;
  }

  const status = error.response?.status;
  if (status === undefined) {
    return true;
  }
  return isRetryableStatus(status, retryPolicy.retryOn ?? DEFAULT_RETRY_POLICY.retryOn);
}

function parseRetryAfter(headers: AxiosResponse["headers"] | undefined): number | undefined {
  const value = readHeader(headers, "retry-after");
  if (!value) {
    return undefined;
  }

  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) {
    return seconds * 1_000;
  }

  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) {
    return undefined;
  }
  return Math.max(0, timestamp - Date.now());
}

function computeRetryDelay(attempt: number, policy: RetryOptions, error: AxiosError): number {
  const retryAfter = policy.respectRetryAfter
    ? parseRetryAfter(error.response?.headers)
    : undefined;
  if (retryAfter !== undefined) {
    return Math.min(retryAfter, policy.maxDelayMs ?? DEFAULT_RETRY_POLICY.maxDelayMs);
  }

  const exponential = (policy.baseDelayMs ?? 300) * 2 ** Math.max(0, attempt - 1);
  const jitter = Math.floor(Math.random() * 100);
  return Math.min(exponential + jitter, policy.maxDelayMs ?? 4_000);
}

export function createHttpClient(options: HttpClientOptions = {}): HttpClient {
  const instance: AxiosInstance = axios.create({
    baseURL: options.baseURL,
    timeout: options.timeout ?? 20_000,
    withCredentials: options.withCredentials ?? true,
    paramsSerializer: { indexes: null },
    headers: {
      Accept: "application/json",
      "X-Requested-With": "XMLHttpRequest",
      ...options.headers,
    },
  });

  instance.interceptors.request.use(async (config) => {
    const requestId = config.pudansRequestId ?? createRequestId();
    config.pudansRequestId = requestId;
    config.headers = AxiosHeaders.from(config.headers);
    config.headers.set("X-Request-Id", requestId);

    if (!config.skipAuth && options.getAccessToken) {
      const token = await options.getAccessToken();
      if (token) {
        config.headers.set("Authorization", `Bearer ${token}`);
      }
    }

    options.onRequest?.(config);
    return config;
  });

  instance.interceptors.response.use(
    (response) => {
      options.onResponse?.(response);
      return response;
    },
    async (error: unknown) => {
      const axiosError = axios.isAxiosError(error) ? error : undefined;
      const config = axiosError?.config;

      if (axiosError && config) {
        const retryPolicy = resolveRetryPolicy(config, options.retry);
        if (shouldRetry(axiosError, config, retryPolicy) && retryPolicy) {
          const attempt = (config.pudansRetryCount ?? 0) + 1;
          config.pudansRetryCount = attempt;
          await sleep(computeRetryDelay(attempt, retryPolicy, axiosError));
          return instance.request(config);
        }
      }

      const normalized = normalizeError(error);
      if (normalized.status === 401) {
        options.onUnauthorized?.(normalized);
      }
      if (normalized.status === 403) {
        options.onForbidden?.(normalized);
      }
      options.onError?.(normalized);
      throw normalized;
    },
  );

  const request = async <T = unknown, D = unknown>(config: PudansRequestConfig<D>): Promise<T> => {
    const response = await instance.request<T, AxiosResponse<T>, D>(config);
    return response.data;
  };

  return {
    instance,
    request,
    get: <T = unknown>(url: string, config?: PudansRequestConfig): Promise<T> =>
      request<T>({ ...config, method: "GET", url }),
    post: <T = unknown, D = unknown>(
      url: string,
      data?: D,
      config?: PudansRequestConfig<D>,
    ): Promise<T> => request<T, D>({ ...config, method: "POST", url, data }),
    put: <T = unknown, D = unknown>(
      url: string,
      data?: D,
      config?: PudansRequestConfig<D>,
    ): Promise<T> => request<T, D>({ ...config, method: "PUT", url, data }),
    patch: <T = unknown, D = unknown>(
      url: string,
      data?: D,
      config?: PudansRequestConfig<D>,
    ): Promise<T> => request<T, D>({ ...config, method: "PATCH", url, data }),
    delete: <T = unknown>(url: string, config?: PudansRequestConfig): Promise<T> =>
      request<T>({ ...config, method: "DELETE", url }),
  };
}

/**
 * Builds a typed CRUD facade over a REST resource. The shape is intentionally
 * generic so generated or hand-written contracts can share the same client.
 */
export function createCrudClient<
  T,
  TCreate = Partial<T>,
  TUpdate = Partial<T>,
  TId = string | number,
>(http: HttpClient, basePath: string): CrudClient<T, TCreate, TUpdate, TId> {
  const resourceUrl = (id: TId): string =>
    `${basePath.replace(/\/$/, "")}/${encodeURIComponent(String(id))}`;

  return {
    list: <L = T[]>(config?: PudansRequestConfig) => http.get<L>(basePath, config),
    get: (id: TId, config?: PudansRequestConfig) => http.get<T>(resourceUrl(id), config),
    create: (payload: TCreate, config?: PudansRequestConfig<TCreate>) =>
      http.post<T, TCreate>(basePath, payload, config),
    update: (id: TId, payload: TUpdate, config?: PudansRequestConfig<TUpdate>) =>
      http.patch<T, TUpdate>(resourceUrl(id), payload, config),
    remove: (id: TId, config?: PudansRequestConfig) => http.delete<void>(resourceUrl(id), config),
  };
}
