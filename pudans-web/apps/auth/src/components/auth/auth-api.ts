import type { LoginResponse } from "@pudans-dev/api-client";

const CONSOLE_URL = process.env.NEXT_PUBLIC_CONSOLE_URL ?? "http://localhost:3003";

const ERROR_MESSAGES: Record<string, string> = {
  authentication_failed: "账号或密码错误",
  password_invalid_error: "新密码不符合密码策略",
  otp_channel_invalid: "请选择有效的验证方式",
  otp_login_request_invalid: "请填写完整的登录信息",
  otp_send_request_invalid: "请输入有效的邮箱或手机号",
  password_reset_request_invalid: "请填写完整的重置信息",
};

type ErrorBody = {
  error?: string;
  error_description?: string;
};

export function apiErrorMessage(error: unknown, fallback: string) {
  if (typeof error !== "object" || error === null) {
    return fallback;
  }
  const response = (error as { response?: { data?: ErrorBody } }).response;
  const code = response?.data?.error;
  if (code && ERROR_MESSAGES[code]) {
    return ERROR_MESSAGES[code];
  }
  return fallback;
}

export function finishLogin(response?: LoginResponse) {
  if (typeof window === "undefined") {
    return;
  }
  const redirectUrl = response?.redirectUrl?.trim();
  if (redirectUrl) {
    window.location.assign(redirectUrl);
    return;
  }
  const requested = new URLSearchParams(window.location.search).get("redirect");
  if (requested?.startsWith("/")) {
    window.location.assign(requested);
    return;
  }
  window.location.assign(CONSOLE_URL);
}