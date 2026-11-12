import { AUTH_ENDPOINTS } from "../constants";
import { ApiError, problemCodeFrom } from "./errors";
import type {
  LoginOutcome,
  LoginProvider,
  LoginStatus,
  ProblemDetail,
  PublicKeyResponse,
  RecoveryResetPasswordRequest,
  SessionView,
} from "./api-types";

const JSON_HEADERS = {
  Accept: "application/json",
  "Content-Type": "application/json",
  "X-Requested-With": "XMLHttpRequest",
} as const;

async function parseBody(response: Response): Promise<unknown> {
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.includes("json")) {
    return undefined;
  }
  try {
    return await response.json();
  } catch {
    return undefined;
  }
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      credentials: "include",
      ...init,
    });
  } catch (cause) {
    throw new ApiError("Network request failed", { code: "network-error", details: cause });
  }

  const body = await parseBody(response);
  if (!response.ok) {
    const problem = body as ProblemDetail | undefined;
    throw new ApiError(problem?.detail || problem?.title || response.statusText, {
      status: response.status,
      code: problemCodeFrom(body) ?? `http-${response.status}`,
      details: body,
    });
  }
  return body as T;
}

export const authApi = {
  getPublicKey(): Promise<PublicKeyResponse> {
    return request<PublicKeyResponse>(AUTH_ENDPOINTS.publicKey, { method: "GET" });
  },

  getSession(): Promise<SessionView> {
    return request<SessionView>(AUTH_ENDPOINTS.session, { method: "GET" });
  },

  getProviders(): Promise<LoginProvider[]> {
    return request<LoginProvider[]>(AUTH_ENDPOINTS.providers, { method: "GET" });
  },

  getStatus(): Promise<LoginStatus> {
    return request<LoginStatus>(AUTH_ENDPOINTS.status, { method: "GET" });
  },

  async submitLogin(username: string, encryptedPassword: string): Promise<LoginOutcome> {
    let response: Response;
    try {
      response = await fetch(AUTH_ENDPOINTS.login, {
        method: "POST",
        credentials: "include",
        redirect: "follow",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "X-Requested-With": "XMLHttpRequest",
        },
        body: new URLSearchParams({
          username,
          password: encryptedPassword,
          "remember-me": "on",
        }),
      });
    } catch (cause) {
      throw new ApiError("Network request failed", { code: "network-error", details: cause });
    }

    const finalUrl = new URL(response.url, window.location.origin);
    if (finalUrl.pathname.startsWith("/challenges/")) {
      return { kind: "challenge", location: finalUrl.pathname + finalUrl.search };
    }
    const errorCode = finalUrl.searchParams.get("error");
    if (errorCode) {
      return { kind: "error", code: errorCode };
    }
    if (!response.ok) {
      return { kind: "error", code: "unknown" };
    }
    return { kind: "success" };
  },

  async logout(): Promise<void> {
    await request<void>(AUTH_ENDPOINTS.logout, {
      method: "POST",
      headers: { Accept: "application/json", "X-Requested-With": "XMLHttpRequest" },
    });
  },

  sendRecoveryCode(email: string): Promise<void> {
    return request<void>(AUTH_ENDPOINTS.recoverySendCode, {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify({ email }),
    });
  },

  resetPassword(payload: RecoveryResetPasswordRequest): Promise<void> {
    return request<void>(AUTH_ENDPOINTS.recoveryResetPassword, {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    });
  },
};
