/** Public auth API group served by appkernel. */
export const AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1";

export const AUTH_ENDPOINTS = {
  publicKey: `${AUTH_API_BASE}/login/public-key`,
  session: `${AUTH_API_BASE}/login/session`,
  providers: `${AUTH_API_BASE}/login/providers`,
  status: `${AUTH_API_BASE}/login/status`,
  login: "/login",
  logout: "/logout",
  recoverySendCode: `${AUTH_API_BASE}/recovery/send-code`,
  recoveryResetPassword: `${AUTH_API_BASE}/recovery/reset-password`,
} as const;
