import { http } from "../../entry/api-client";
import type { PasskeyOptionsVo, UserConnection } from "../models/passkey";

const AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1";

/** GET /passkeys */
export function listPasskeys(): Promise<UserConnection[]> {
  return http
    .get<UserConnection[]>(`${AUTH_API_BASE}/passkeys`)
    .then((response) => response.data);
}

/** GET /passkeys/registration/options */
export function getPasskeyRegistrationOptions(): Promise<PasskeyOptionsVo> {
  return http
    .get<PasskeyOptionsVo>(`${AUTH_API_BASE}/passkeys/registration/options`)
    .then((response) => response.data);
}

/** POST /passkeys/registration/verify */
export function verifyPasskeyRegistration(body: string): Promise<UserConnection> {
  return http
    .post<UserConnection>(`${AUTH_API_BASE}/passkeys/registration/verify`, body, {
      headers: { "Content-Type": "application/json" },
    })
    .then((response) => response.data);
}
