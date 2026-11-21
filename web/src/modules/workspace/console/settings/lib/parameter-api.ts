import { apiClient, axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

type ProblemDetail = {
  detail?: string;
  errors?: string[];
};

export type AuthenticationConnectionTestRequest = {
  type: "feishu" | "oauth2" | "oidc";
  registrationId?: string;
  issuerUri?: string;
  authorizationUri?: string;
  tokenUri?: string;
  userInfoUri?: string;
  clientId?: string;
  clientSecret?: string;
  appId?: string;
  appSecret?: string;
  scopes?: string;
};

export type AuthenticationConnectionTestResult = {
  success: boolean;
  message: string;
};

export async function testAuthenticationConnection(
  body: AuthenticationConnectionTestRequest,
): Promise<AuthenticationConnectionTestResult> {
  const response = await axiosInstance.post<AuthenticationConnectionTestResult>(
    "/apis/api.pudans.tolim.cn/v1alpha1/parameter-settings/authentication/-/test",
    body,
  );

  return response.data;
}

export async function fetchParameterGroup(group: string): Promise<Record<string, unknown>> {
  const response = await apiClient.parameterSetting.getParameterSettingByGroup({ group });

  return parseParameterGroup(response.data);
}

export async function updateParameterGroup(
  group: string,
  body: Record<string, unknown>,
): Promise<{ restartRequired: boolean }> {
  const response = await apiClient.parameterSetting.updateParameterSettingByGroup({
    group,
    body: JSON.stringify(body),
  });

  return {
    restartRequired: String(response.headers["x-pudans-restart-required"]) === "true",
  };
}

export async function fetchSystemConfigGroup(group: string): Promise<Record<string, unknown>> {
  const response = await axiosInstance.get<unknown>(
    `/apis/api.pudans.tolim.cn/v1alpha1/systemconfigs/${encodeURIComponent(group)}`,
  );

  return parseParameterGroup(response.data);
}

export async function updateSystemConfigGroup(
  group: string,
  body: Record<string, unknown>,
): Promise<{ restartRequired: boolean }> {
  const response = await axiosInstance.put(
    `/apis/api.pudans.tolim.cn/v1alpha1/systemconfigs/${encodeURIComponent(group)}`,
    body,
  );

  return {
    restartRequired: String(response.headers["x-pudans-restart-required"]) === "true",
  };
}

export function resolveParameterSettingError(error: unknown): string {
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
      return "没有权限修改这些参数。";
    }
    if (status === 404) {
      return "参数设置组不存在。";
    }
    if (status === 503) {
      return error.response?.data?.detail || "服务暂时不可用，请稍后重试。";
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

function parseParameterGroup(value: unknown): Record<string, unknown> {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }

  if (typeof value === "string" && value.trim()) {
    const parsed = JSON.parse(value) as unknown;

    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
  }

  return {};
}
