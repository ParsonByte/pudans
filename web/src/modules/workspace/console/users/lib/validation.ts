import { z } from "zod";

const USERNAME_PATTERN = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$/;

const PASSWORD_PATTERN = /^[A-Za-z0-9!@#$%^&*.?]+$/;

export const usernameField = z
  .string()
  .trim()
  .min(1, "请输入用户名。")
  .max(63, "用户名不能超过 63 个字符。")
  .regex(USERNAME_PATTERN, "用户名只能包含小写字母、数字、- 和 .。");

export const passwordField = z
  .string()
  .min(8, "密码至少需要 8 位。")
  .max(257, "密码不能超过 257 位。")
  .regex(PASSWORD_PATTERN, "密码只能包含字母、数字和 !@#$%^&*.?。");
