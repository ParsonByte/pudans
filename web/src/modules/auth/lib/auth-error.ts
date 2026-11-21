import { isAxiosError } from "axios";

export function resolveAuthError(
  error: unknown,
  statusMessages: Partial<Record<number, string>>,
  fallback: string,
): string {
  if (isAxiosError(error)) {
    const status = error.response?.status;
    const message = status === undefined ? undefined : statusMessages[status];

    if (message) {
      return message;
    }
    if (!error.response) {
      return "无法连接服务器，请稍后重试。";
    }
  }

  return fallback;
}
