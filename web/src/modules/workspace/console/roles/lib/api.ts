import { resolveApiError } from "@/lib/api-error";

export function resolveRoleApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "角色不存在或已被删除。",
    conflict: "角色名称已存在。",
  });
}
