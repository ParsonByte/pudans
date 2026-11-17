import { isAxiosError } from "axios";
import { z } from "zod";

const USERNAME_PATTERN = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$/;
const PASSWORD_PATTERN = /^[A-Za-z0-9!@#$%^&*.?]+$/;
const EXTERNAL_URL_PATTERN = /^https?:\/\//i;
const DEFAULT_SITE_TITLE = "Pudans";

interface SetupProblemDetail {
  errors?: string[];
}

type SetupErrorResult = {
  message: string;
  setupCompleted: boolean;
};

export const setupSchema = z
  .object({
    siteTitle: z.string().trim().min(1, "请输入站点名称。").max(80, "站点名称不能超过 80 个字符。"),
    externalUrl: z
      .string()
      .trim()
      .min(1, "请输入站点地址。")
      .refine(
        (value) => EXTERNAL_URL_PATTERN.test(value) && z.url().safeParse(value).success,
        "请输入以 http:// 或 https:// 开头的有效地址。",
      ),
    username: z
      .string()
      .trim()
      .min(4, "用户名至少需要 4 个字符。")
      .max(63, "用户名不能超过 63 个字符。")
      .regex(
        USERNAME_PATTERN,
        "用户名只能包含小写字母、数字、点或中划线，并以字母或数字开头和结尾。",
      ),
    email: z
      .string()
      .trim()
      .refine(
        (value) => value === "" || z.email().safeParse(value).success,
        "请输入有效的邮箱地址。",
      ),
    password: z
      .string()
      .min(8, "密码至少需要 8 个字符。")
      .max(257, "密码不能超过 257 个字符。")
      .regex(PASSWORD_PATTERN, "密码只能包含大小写字母、数字及 !@#$%^&*.?。"),
    confirmPassword: z.string().min(1, "请再次输入密码。"),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: "两次输入的密码不一致。",
    path: ["confirmPassword"],
  });

export type SetupFormValues = z.infer<typeof setupSchema>;

export function createSetupDefaults(): SetupFormValues {
  const origin = typeof window === "undefined" ? "" : window.location.origin;

  return {
    siteTitle: DEFAULT_SITE_TITLE,
    externalUrl: EXTERNAL_URL_PATTERN.test(origin) ? origin : "",
    username: "",
    email: "",
    password: "",
    confirmPassword: "",
  };
}

export function resolveSetupError(error: unknown): SetupErrorResult {
  if (isAxiosError<SetupProblemDetail>(error)) {
    const status = error.response?.status;

    if (status === 409) {
      return { message: "系统已完成初始化，请直接登录。", setupCompleted: true };
    }

    if (status === 400) {
      const firstError = error.response?.data?.errors?.find((item) => item.trim());
      return {
        message: firstError ?? "请检查填写内容后重试。",
        setupCompleted: false,
      };
    }

    if (error.code === "ECONNABORTED") {
      return { message: "初始化超时，请稍后重试。", setupCompleted: false };
    }

    if (!error.response) {
      return { message: "无法连接服务器，请稍后重试。", setupCompleted: false };
    }

    if (error.response.status >= 500) {
      return { message: "服务器暂时不可用，请稍后重试。", setupCompleted: false };
    }
  }

  return { message: "初始化失败，请稍后重试。", setupCompleted: false };
}
