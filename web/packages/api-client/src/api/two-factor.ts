import { http } from "../../entry/api-client";
import type {
  PasswordRequest,
  TotpAuthLinkResponse,
  TotpRequest,
  TwoFactorAuthSettings,
} from "../models/two-factor";

const AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1";

const BASE = `${AUTH_API_BASE}/authentications/two-factor`;

/** GET /authentications/two-factor/settings */
export function getTwoFactorSettings(): Promise<TwoFactorAuthSettings> {
  return http.get<TwoFactorAuthSettings>(`${BASE}/settings`).then((response) => response.data);
}

/** PUT /authentications/two-factor/settings/enabled */
export function enableTwoFactor(payload: PasswordRequest): Promise<TwoFactorAuthSettings> {
  return http
    .put<TwoFactorAuthSettings>(`${BASE}/settings/enabled`, payload)
    .then((response) => response.data);
}

/** PUT /authentications/two-factor/settings/disabled */
export function disableTwoFactor(payload: PasswordRequest): Promise<TwoFactorAuthSettings> {
  return http
    .put<TwoFactorAuthSettings>(`${BASE}/settings/disabled`, payload)
    .then((response) => response.data);
}

/** POST /authentications/two-factor/totp */
export function configureTotp(payload: TotpRequest): Promise<TwoFactorAuthSettings> {
  return http
    .post<TwoFactorAuthSettings>(`${BASE}/totp`, payload)
    .then((response) => response.data);
}

/** DELETE /authentications/two-factor/totp */
export function deleteTotp(payload: PasswordRequest): Promise<TwoFactorAuthSettings> {
  return http
    .delete<TwoFactorAuthSettings>(`${BASE}/totp`, { data: payload })
    .then((response) => response.data);
}

/** GET /authentications/two-factor/totp/auth-link */
export function getTotpAuthLink(): Promise<TotpAuthLinkResponse> {
  return http
    .get<TotpAuthLinkResponse>(`${BASE}/totp/auth-link`)
    .then((response) => response.data);
}
