import type { PublicKeyResponse } from "./generated";

export type { PublicKeyResponse };

/** GET /login/status */
export interface LoginStatus {
  setupRequired: boolean;
  postLoginUrl: string;
}

/** GET /login/providers */
export interface LoginProvider {
  name: string;
  displayName: string;
  logo?: string | null;
  authenticationUrl: string;
  helpPage?: string | null;
}

/** GET /login/session */
export interface SessionView {
  authenticated: boolean;
  username?: string | null;
  displayName?: string | null;
  avatar?: string | null;
  redirectUrl: string;
  consoleAccessDisallowed?: boolean;
  uiPermissions?: string[];
}

/** POST /login */
export interface LoginRequest {
  username: string;
  encryptedPassword: string;
  rememberMe?: boolean;
}

export type LoginOutcome =
  | { kind: "success" }
  | { kind: "challenge"; location: string }
  | { kind: "error"; code: string };

/** POST /recovery/send-code */
export interface SendRecoveryCodePayload {
  email: string;
}

/** POST /recovery/reset-password */
export interface ResetPasswordPayload {
  email: string;
  code: string;
  newPassword: string;
}
