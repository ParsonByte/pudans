import type { PublicKeyResponse } from "../models";
import type {
  ResetPasswordPayload,
  SendRecoveryCodePayload,
} from "./recovery-api";
import type { HttpClient, PudansRequestConfig } from "./types";

export const AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1";

export interface LoginStatus {
  setupRequired: boolean;
  postLoginUrl: string;
}

export interface LoginProvider {
  name: string;
  displayName: string;
  logo?: string | null;
  authenticationUrl: string;
  helpPage?: string | null;
}

export interface SessionView {
  authenticated: boolean;
  username?: string | null;
  displayName?: string | null;
  avatar?: string | null;
  redirectUrl: string;
  consoleAccessDisallowed?: boolean;
  uiPermissions?: string[];
}

export interface AuthSetupPayload {
  username: string;
  password: string;
  email?: string;
  siteTitle: string;
  siteUrl: string;
  language?: string;
}

export type LoginOutcome =
  | { kind: "success" }
  | { kind: "challenge"; location: string }
  | { kind: "error"; code: string };

export const AUTH_SETUP_PATH = "/apis/public.api.pudans.tolim.cn/v1alpha1/system/setup";

function errorCodeFromProblem(data: unknown): string {
  if (typeof data !== "object" || data === null) {
    return "unknown";
  }
  const problem = data as { code?: unknown; type?: unknown };
  if (typeof problem.code === "string" && problem.code.trim()) {
    return problem.code;
  }
  if (typeof problem.type === "string" && problem.type.includes("invalid-credential")) {
    return "invalid-credential";
  }
  return "unknown";
}

/**
 * Auth domain client. All requests flow through the shared Axios instance so
 * the request id, retry policy and centralized 401/403 handling apply here too.
 */
export class AuthApiClient {
  constructor(private readonly http: HttpClient) {}

  getStatus(config?: PudansRequestConfig): Promise<LoginStatus> {
    return this.http.get<LoginStatus>(`${AUTH_API_BASE}/login/status`, config);
  }

  getProviders(config?: PudansRequestConfig): Promise<LoginProvider[]> {
    return this.http.get<LoginProvider[]>(`${AUTH_API_BASE}/login/providers`, config);
  }

  getPublicKey(config?: PudansRequestConfig): Promise<PublicKeyResponse> {
    return this.http.get<PublicKeyResponse>(`${AUTH_API_BASE}/login/public-key`, config);
  }

  getSession(config?: PudansRequestConfig): Promise<SessionView> {
    return this.http.get<SessionView>(`${AUTH_API_BASE}/login/session`, config);
  }

  async login(username: string, encryptedPassword: string, rememberMe = true): Promise<LoginOutcome> {
    const body = new URLSearchParams({
      username,
      password: encryptedPassword,
      ...(rememberMe ? { "remember-me": "on" } : {}),
    });
    const response = await this.http.instance.post("/login", body, {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "X-Requested-With": "XMLHttpRequest",
        Accept: "*/*",
      },
      withCredentials: true,
      validateStatus: () => true,
    });

    const responseUrl = response.request?.responseURL;
    if (responseUrl) {
      const finalUrl = new URL(responseUrl, window.location.origin);
      if (finalUrl.pathname.startsWith("/challenges/")) {
        return { kind: "challenge", location: finalUrl.pathname + finalUrl.search };
      }
      const errorCode = finalUrl.searchParams.get("error");
      if (errorCode) {
        return { kind: "error", code: errorCode };
      }
    }

    if (response.status >= 200 && response.status < 300) {
      return { kind: "success" };
    }
    return { kind: "error", code: errorCodeFromProblem(response.data) };
  }

  logout(config?: PudansRequestConfig): Promise<void> {
    return this.http.post<void>("/logout", undefined, {
      ...config,
      headers: {
        Accept: "application/json",
        "X-Requested-With": "XMLHttpRequest",
        ...config?.headers,
      },
    });
  }

  sendRecoveryCode(
    payload: SendRecoveryCodePayload,
    config?: PudansRequestConfig<SendRecoveryCodePayload>,
  ): Promise<void> {
    return this.http.post<void, SendRecoveryCodePayload>(
      `${AUTH_API_BASE}/recovery/send-code`,
      payload,
      config,
    );
  }

  resetPassword(
    payload: ResetPasswordPayload,
    config?: PudansRequestConfig<ResetPasswordPayload>,
  ): Promise<void> {
    return this.http.post<void, ResetPasswordPayload>(
      `${AUTH_API_BASE}/recovery/reset-password`,
      payload,
      config,
    );
  }

  setupSystem(payload: AuthSetupPayload, config?: PudansRequestConfig): Promise<void> {
    const body = new URLSearchParams({
      username: payload.username,
      password: payload.password,
      siteTitle: payload.siteTitle,
      externalUrl: payload.siteUrl,
    });
    if (payload.email) {
      body.set("email", payload.email);
    }
    if (payload.language) {
      body.set("language", payload.language);
    }
    return this.http.post<void>(AUTH_SETUP_PATH, body, {
      ...config,
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        ...config?.headers,
      },
    });
  }
}
