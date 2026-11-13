import axios, {
  AxiosHeaders,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import { PudansApiError } from "../src/models/errors";

/**
 * The one and only Axios instance used by every Pudans frontend application.
 *
 * Halo keeps its interceptors in the api-client package and lets the apps
 * consume the configured instance. All cross-cutting behaviour lives here:
 * request ids, credentials, authorization headers, 401/403 redirects and error
 * normalization. Pages and routers never inspect HTTP status codes themselves.
 */
export const API_BASE_URL = "";

export const ACCESS_TOKEN_STORAGE_KEY = "pudans:access-token";

export const AUTH_PATH = "/auth/";
export const FORBIDDEN_PATH = "/forbidden";

const REQUEST_ID_HEADER = "X-Request-Id";
const AUTH_PATH_PREFIXES = ["/auth", "/setup", "/login"];

function createRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `req_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
}

function currentLocation(): string {
  if (typeof window === "undefined") {
    return "/";
  }
  return `${window.location.pathname}${window.location.search}${window.location.hash}`;
}

function isAuthRequestPath(pathname: string): boolean {
  return AUTH_PATH_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(prefix + "/"));
}

export function readAccessToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    return window.localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setAccessToken(token: string | null): void {
  if (typeof window === "undefined") {
    return;
  }
  try {
    if (token) {
      window.localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
    } else {
      window.localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
    }
  } catch {
    // Storage can be unavailable in private browsing; cookie auth still works.
  }
}

/** Centralized expired-session handling for the 401 response interceptor. */
export function redirectToLogin(): void {
  if (typeof window === "undefined") {
    return;
  }
  if (isAuthRequestPath(window.location.pathname)) {
    return;
  }
  const redirectUri = encodeURIComponent(currentLocation());
  window.location.assign(`${AUTH_PATH}?redirect_uri=${redirectUri}`);
}

/** Centralized missing-permission handling for the 403 response interceptor. */
export function redirectToForbidden(): void {
  if (typeof window === "undefined") {
    return;
  }
  if (window.location.pathname === FORBIDDEN_PATH) {
    return;
  }
  window.location.assign(FORBIDDEN_PATH);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readHeader(headers: AxiosResponse["headers"] | undefined, name: string): string | undefined {
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

/** Normalizes every transport failure into one public error shape. */
export function normalizeApiError(error: unknown): PudansApiError {
  if (error instanceof PudansApiError) {
    return error;
  }

  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const problem = extractProblemDetails(error.response?.data);
    const requestId =
      problem.requestId ??
      (error.config?.headers?.[REQUEST_ID_HEADER] as string | undefined) ??
      readHeader(error.response?.headers, "x-request-id");

    return new PudansApiError(problem.message ?? error.message ?? "Request failed", {
      status,
      code: problem.code ?? error.code ?? (status ? `HTTP_${status}` : "NETWORK_ERROR"),
      details: error.response?.data,
      requestId,
      isNetworkError: error.response === undefined,
      cause: error,
    });
  }

  if (error instanceof Error) {
    return new PudansApiError(error.message, { cause: error });
  }

  return new PudansApiError("Unknown request error", { details: error });
}

export const http: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20_000,
  withCredentials: true,
  paramsSerializer: { indexes: null },
  headers: {
    Accept: "application/json",
    "X-Requested-With": "XMLHttpRequest",
  },
});

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  config.headers = AxiosHeaders.from(config.headers);
  const requestId = createRequestId();
  config.headers.set(REQUEST_ID_HEADER, requestId);
  config.headers.set("X-Requested-With", "XMLHttpRequest");

  const token = readAccessToken();
  if (token) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }

  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const normalized = normalizeApiError(error);

    if (normalized.status === 401) {
      redirectToLogin();
    } else if (normalized.status === 403) {
      redirectToForbidden();
    }

    return Promise.reject(normalized);
  },
);

export function isRequestCancelled(error: unknown): boolean {
  return axios.isCancel(error);
}
