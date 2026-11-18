import { isAxiosError } from "axios";

type ProblemDetail = {
  detail?: string;
  errors?: string[];
};

export function resolveRoleApiError(error: unknown): string {
  if (isAxiosError<ProblemDetail>(error)) {
    const status = error.response?.status;
    const firstError = error.response?.data?.errors?.find((item) => item.trim());

    if (firstError) {
      return firstError;
    }
    if (status === 400) {
      return error.response?.data?.detail || "请检查填写内容后重试。";
    }
    if (status === 401) {
      return "登录状态已失效，请重新登录。";
    }
    if (status === 403) {
      return "没有权限执行该操作。";
    }
    if (status === 404) {
      return "角色不存在或已被删除。";
    }
    if (status === 409) {
      return error.response?.data?.detail || "角色名称已存在。";
    }
    if (status === 429) {
      return "操作过于频繁，请稍后重试。";
    }
    if (status && status >= 500) {
      return "服务器暂时不可用，请稍后重试。";
    }
    if (!error.response) {
      return "无法连接服务器，请稍后重试。";
    }
  }

  return "操作失败，请稍后重试。";
}
