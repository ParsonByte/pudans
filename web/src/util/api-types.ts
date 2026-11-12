/** Problem detail body produced by the appkernel error handler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  requestId?: string;
}

/** Public session snapshot returned by the auth API. */
export interface SessionView {
  authenticated: boolean;
  username?: string | null;
  displayName?: string | null;
  avatar?: string | null;
  /** Configured destination after a successful sign-in. */
  redirectUrl: string;
}

/** OAuth2 provider exposed on the sign-in page. */
export interface LoginProvider {
  name: string;
  displayName: string;
  logo?: string | null;
  authenticationUrl: string;
  helpPage?: string | null;
}

/** Instance state required by the sign-in page. */
export interface LoginStatus {
  setupRequired: boolean;
  postLoginUrl: string;
}

export interface PublicKeyResponse {
  base64Format: string;
}

export interface RecoverySendCodeRequest {
  email: string;
}

export interface RecoveryResetPasswordRequest {
  email: string;
  code: string;
  newPassword: string;
}

/** Outcome of the form login request, following server redirects. */
export type LoginOutcome =
  | { kind: "success" }
  | { kind: "challenge"; location: string }
  | { kind: "error"; code: string };
