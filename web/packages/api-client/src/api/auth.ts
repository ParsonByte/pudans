import { http } from "../../entry/api-client";
import type {
  LoginOutcome,
  LoginProvider,
  LoginRequest,
  LoginStatus,
  PublicKeyResponse,
  ResetPasswordPayload,
  SendRecoveryCodePayload,
  SessionView,
} from "../models/auth";

const AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1";

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

/** GET /login/status */
export function getLoginStatus(): Promise<LoginStatus> {
  return http.get<LoginStatus>(`${AUTH_API_BASE}/login/status`).then((response) => response.data);
}

/** GET /login/providers */
export function listLoginProviders(): Promise<LoginProvider[]> {
  return http
    .get<LoginProvider[]>(`${AUTH_API_BASE}/login/providers`)
    .then((response) => response.data);
}

/** GET /login/public-key */
export function getLoginPublicKey(): Promise<PublicKeyResponse> {
  return http
    .get<PublicKeyResponse>(`${AUTH_API_BASE}/login/public-key`)
    .then((response) => response.data);
}

/** GET /login/session */
export function getLoginSession(): Promise<SessionView> {
  return http.get<SessionView>(`${AUTH_API_BASE}/login/session`).then((response) => response.data);
}

/**
 * POST /login (form-urlencoded). The backend answers with a 302 to the
 * post-login destination or back to /auth with an error code, so the final
 * redirect URL is the source of truth for success and failure.
 */
export async function login(payload: LoginRequest): Promise<LoginOutcome> {
  const body = new URLSearchParams({
    username: payload.username,
    password: payload.encryptedPassword,
    ...(payload.rememberMe === false ? {} : { "remember-me": "on" }),
  });

  const response = await http.post("/login", body, {
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

  if (response.status >= 200 && response.status < 300) {
    return { kind: "success" };
  }
  return { kind: "error", code: errorCodeFromProblem(response.data) };
}

/** POST /logout */
export function logout(): Promise<void> {
  return http.post<void>("/logout").then(() => undefined);
}

/** POST /recovery/send-code */
export function sendRecoveryCode(payload: SendRecoveryCodePayload): Promise<void> {
  return http
    .post<void>(`${AUTH_API_BASE}/recovery/send-code`, payload)
    .then(() => undefined);
}

/** POST /recovery/reset-password */
export function resetPassword(payload: ResetPasswordPayload): Promise<void> {
  return http
    .post<void>(`${AUTH_API_BASE}/recovery/reset-password`, payload)
    .then(() => undefined);
}
