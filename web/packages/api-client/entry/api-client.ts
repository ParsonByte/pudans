import axios, {
  AxiosHeaders,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import {
  AnnotationSettingV1AlphaUcApi,
  AnnotationSettingV1alpha1Api,
  AuditLogV1alpha1Api,
  AuthProviderV1alpha1Api,
  AuthV1alpha1LoginApi,
  AuthenticationApi,
  ConfigMapV1alpha1Api,
  Configuration,
  DashboardV1alpha1Api,
  DeviceV1alpha1UcApi,
  ExtensionDefinitionV1alpha1Api,
  ExtensionPointDefinitionV1alpha1Api,
  LoginApi,
  PasskeyV1alpha1PublicApi,
  PasskeyV1alpha1UcApi,
  PasswordRecoveryEndpointApi,
  PersonalAccessTokenV1alpha1Api,
  PersonalAccessTokenV1alpha1UcApi,
  PluginV1alpha1Api,
  PluginV1alpha1ConsoleApi,
  RememberMeTokenV1alpha1Api,
  ReverseProxyV1alpha1Api,
  RiskRuleV1alpha1Api,
  RoleBindingV1alpha1Api,
  RoleV1alpha1Api,
  SecretV1alpha1Api,
  SettingV1alpha1Api,
  SystemConfigV1alpha1ConsoleApi,
  SystemV1alpha1PublicApi,
  TempRoleBindingV1alpha1Api,
  TwoFactorAuthV1alpha1UcApi,
  UserConnectionV1alpha1Api,
  UserConnectionV1alpha1UcApi,
  UserGroupBindingV1alpha1Api,
  UserGroupV1alpha1Api,
  UserPreferenceV1alpha1UcApi,
  UserV1alpha1Api,
  UserV1alpha1ConsoleApi,
  UserV1alpha1UcApi,
  WebhookV1alpha1Api,
} from "../src";
import type {
  LoginProviderView,
  LoginStatusView,
  PublicKeyResponse,
  ResetPasswordRequest,
  SendCodeRequest,
  SessionView,
  SetupRequest,
} from "../src";

export const ACCESS_TOKEN_STORAGE_KEY = "pudans:access-token";
export const AUTH_PATH = "/auth";
export const FORBIDDEN_PATH = "/forbidden";

const REQUEST_ID_HEADER = "X-Request-Id";
const AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1";
const PUBLIC_API_BASE = "/apis/public.api.pudans.tolim.cn/v1alpha1";

export interface PudansApiErrorOptions {
  status?: number;
  code?: string;
  details?: unknown;
  requestId?: string;
  isNetworkError?: boolean;
  cause?: unknown;
}

/** Normalized error thrown by every @pudans/api-client request. */
export class PudansApiError extends Error {
  readonly status: number | undefined;
  readonly code: string;
  readonly details: unknown;
  readonly requestId: string | undefined;
  readonly isNetworkError: boolean;

  constructor(message: string, options: PudansApiErrorOptions = {}) {
    super(message, options.cause === undefined ? undefined : { cause: options.cause });
    this.name = "PudansApiError";
    this.status = options.status;
    this.code = options.code ?? "UNKNOWN_ERROR";
    this.details = options.details;
    this.requestId = options.requestId;
    this.isNetworkError = options.isNetworkError ?? false;
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

export function isPudansApiError(error: unknown): error is PudansApiError {
  return error instanceof PudansApiError;
}

export interface LoginRequest {
  username: string;
  encryptedPassword: string;
  rememberMe?: boolean;
}

export type LoginOutcome =
  | { kind: "success" }
  | { kind: "challenge"; location: string }
  | { kind: "error"; code: string };

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
  return ["/auth", "/setup", "/login"].some(
    (prefix) => pathname === prefix || pathname.startsWith(prefix + "/"),
  );
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
    // Cookie auth remains available when storage is blocked.
  }
}

/** Centralized expired-session handling for the 401 response interceptor. */
export function redirectToLogin(): void {
  if (typeof window === "undefined" || isAuthRequestPath(window.location.pathname)) {
    return;
  }
  window.location.assign(`${AUTH_PATH}?redirect_uri=${encodeURIComponent(currentLocation())}`);
}

/** Centralized missing-permission handling for the 403 response interceptor. */
export function redirectToForbidden(): void {
  if (typeof window === "undefined" || window.location.pathname === FORBIDDEN_PATH) {
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

function extractProblemDetails(data: unknown): { message?: string; code?: string; requestId?: string } {
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

/** The single Axios instance shared by every frontend application. */
export const defaultAxiosInstance: AxiosInstance = axios.create({
  baseURL: "",
  withCredentials: true,
  paramsSerializer: { indexes: null },
  headers: {
    Accept: "application/json",
    "X-Requested-With": "XMLHttpRequest",
  },
});

defaultAxiosInstance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  config.headers = AxiosHeaders.from(config.headers);
  config.headers.set(REQUEST_ID_HEADER, createRequestId());
  config.headers.set("X-Requested-With", "XMLHttpRequest");
  const token = readAccessToken();
  if (token) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

let suppressAuthRedirect = false;

defaultAxiosInstance.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const normalized = normalizeApiError(error);
    if (!suppressAuthRedirect && normalized.status === 401) {
      redirectToLogin();
    } else if (!suppressAuthRedirect && normalized.status === 403) {
      redirectToForbidden();
    }
    return Promise.reject(normalized);
  },
);

const configuration = new Configuration({ basePath: "" });

function createApi<T>(
  ApiClass: new (
    configuration?: Configuration,
    basePath?: string,
    axios?: AxiosInstance,
  ) => T,
): T {
  return new ApiClass(configuration, "", defaultAxiosInstance);
}

/**
 * the reference createCoreApiClient pattern: one grouped object of generated
 * resource API classes, all bound to the single configured Axios instance.
 */
export const coreApiClient = {
  auth: {
    authProvider: createApi(AuthProviderV1alpha1Api),
    authentication: createApi(AuthenticationApi),
    login: createApi(LoginApi),
    loginSession: createApi(AuthV1alpha1LoginApi),
    recovery: createApi(PasswordRecoveryEndpointApi),
    userConnection: createApi(UserConnectionV1alpha1Api),
    passkey: createApi(PasskeyV1alpha1UcApi),
    twoFactor: createApi(TwoFactorAuthV1alpha1UcApi),
    device: createApi(DeviceV1alpha1UcApi),
    personalAccessToken: createApi(PersonalAccessTokenV1alpha1UcApi),
    rememberMeToken: createApi(RememberMeTokenV1alpha1Api),
  },
  console: {
    dashboard: createApi(DashboardV1alpha1Api),
    user: createApi(UserV1alpha1Api),
    userConsole: createApi(UserV1alpha1ConsoleApi),
    role: createApi(RoleV1alpha1Api),
    roleBinding: createApi(RoleBindingV1alpha1Api),
    tempRoleBinding: createApi(TempRoleBindingV1alpha1Api),
    auditLog: createApi(AuditLogV1alpha1Api),
    setting: createApi(SettingV1alpha1Api),
    systemConfig: createApi(SystemConfigV1alpha1ConsoleApi),
    configMap: createApi(ConfigMapV1alpha1Api),
    riskRule: createApi(RiskRuleV1alpha1Api),
    webhook: createApi(WebhookV1alpha1Api),
    userGroup: createApi(UserGroupV1alpha1Api),
    userGroupBinding: createApi(UserGroupBindingV1alpha1Api),
    secret: createApi(SecretV1alpha1Api),
    annotationSetting: createApi(AnnotationSettingV1alpha1Api),
    plugin: createApi(PluginV1alpha1Api),
    pluginConsole: createApi(PluginV1alpha1ConsoleApi),
    extensionDefinition: createApi(ExtensionDefinitionV1alpha1Api),
    extensionPointDefinition: createApi(ExtensionPointDefinitionV1alpha1Api),
    reverseProxy: createApi(ReverseProxyV1alpha1Api),
    personalAccessToken: createApi(PersonalAccessTokenV1alpha1Api),
  },
  uc: {
    user: createApi(UserV1alpha1UcApi),
    setting: createApi(SettingV1alpha1Api),
    device: createApi(DeviceV1alpha1UcApi),
    passkey: createApi(PasskeyV1alpha1UcApi),
    twoFactor: createApi(TwoFactorAuthV1alpha1UcApi),
    connection: createApi(UserConnectionV1alpha1UcApi),
    annotationSetting: createApi(AnnotationSettingV1AlphaUcApi),
    userPreference: createApi(UserPreferenceV1alpha1UcApi),
    personalAccessToken: createApi(PersonalAccessTokenV1alpha1UcApi),
  },
  public: {
    system: createApi(SystemV1alpha1PublicApi),
    passkey: createApi(PasskeyV1alpha1PublicApi),
  },
};

/** GET /login/status */
export function getLoginStatus(): Promise<LoginStatusView> {
  return coreApiClient.auth.loginSession.getLoginStatus().then((response) => response.data);
}

/** GET /login/providers */
export function listLoginProviders(): Promise<LoginProviderView[]> {
  return coreApiClient.auth.loginSession.listLoginProviders().then((response) => response.data);
}

/** GET /login/public-key */
export function getLoginPublicKey(): Promise<PublicKeyResponse> {
  return coreApiClient.auth.login.getPublicKey().then((response) => response.data);
}

/** GET /login/session */
export function getLoginSession(): Promise<SessionView> {
  return coreApiClient.auth.loginSession.getLoginSession().then((response) => response.data);
}

/** POST /login (form-urlencoded, redirect based). */
export async function login(payload: LoginRequest): Promise<LoginOutcome> {
  const body = new URLSearchParams({
    username: payload.username,
    password: payload.encryptedPassword,
    ...(payload.rememberMe === false ? {} : { "remember-me": "on" }),
  });
  const response = await defaultAxiosInstance.post("/login", body, {
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "*/*",
    },
    validateStatus: () => true,
  });
  const responseUrl = (response.request as { responseURL?: string } | undefined)?.responseURL;
  if (responseUrl && typeof window !== "undefined") {
    const finalUrl = new URL(responseUrl, window.location.origin);
    if (finalUrl.pathname.startsWith("/challenges/")) {
      return { kind: "challenge", location: finalUrl.pathname + finalUrl.search };
    }
    const errorCode = finalUrl.searchParams.get("error");
    if (errorCode) {
      return { kind: "error", code: errorCode };
    }
  }
  return response.status >= 200 && response.status < 300
    ? { kind: "success" }
    : { kind: "error", code: "unknown" };
}

/** POST /logout. The caller owns the post-logout destination. */
export async function logout(): Promise<void> {
  suppressAuthRedirect = true;
  try {
    await defaultAxiosInstance.post<void>("/logout");
  } finally {
    suppressAuthRedirect = false;
  }
}

/** POST /recovery/send-code */
export function sendRecoveryCode(payload: SendCodeRequest): Promise<void> {
  return coreApiClient.auth.recovery
    .sendCode({ sendCodeRequest: payload })
    .then(() => undefined);
}

/** POST /recovery/reset-password */
export function resetPassword(payload: ResetPasswordRequest): Promise<void> {
  return coreApiClient.auth.recovery
    .resetPassword({ resetPasswordRequest: payload })
    .then(() => undefined);
}

/** POST /system/setup (form-urlencoded first-run initialization). */
export function setupSystem(payload: SetupRequest): Promise<void> {
  const body = new URLSearchParams({
    username: payload.username,
    password: payload.password,
    siteTitle: payload.siteTitle,
    externalUrl: payload.externalUrl,
  });
  if (payload.email) {
    body.set("email", payload.email);
  }
  if (payload.language) {
    body.set("language", payload.language);
  }
  return defaultAxiosInstance
    .post<void>(`${PUBLIC_API_BASE}/system/setup`, body, {
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    })
    .then(() => undefined);
}

export { AUTH_API_BASE, PUBLIC_API_BASE };
