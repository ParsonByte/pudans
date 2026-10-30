import type { HttpClient } from "./types";

export const RECOVERY_BASE_PATH =
  "/apis/auth.api.pudans.tolim.cn/v1alpha1/recovery";

export interface SendRecoveryCodePayload {
  email: string;
}

export interface ResetPasswordPayload {
  email: string;
  code: string;
  newPassword: string;
}

/** Anonymous password recovery endpoints under the auth.api group. */
export class RecoveryApi {
  constructor(private readonly http: HttpClient) {}

  sendCode(payload: SendRecoveryCodePayload): Promise<void> {
    return this.http.post<void, SendRecoveryCodePayload>(
      `${RECOVERY_BASE_PATH}/send-code`,
      payload,
    );
  }

  resetPassword(payload: ResetPasswordPayload): Promise<void> {
    return this.http.post<void, ResetPasswordPayload>(
      `${RECOVERY_BASE_PATH}/reset-password`,
      payload,
    );
  }
}
