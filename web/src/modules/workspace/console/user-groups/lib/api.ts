import { type UserGroupBinding, apiClient } from "@pudans-dev/api-client";

import { resolveApiError } from "@/lib/api-error";

import { userGroupSubject } from "./user-group-model";

const BINDING_PAGE_SIZE = 1000;

async function fetchAllBindings(fieldSelector: string): Promise<UserGroupBinding[]> {
  const bindings: UserGroupBinding[] = [];

  for (let page = 1; ; page += 1) {
    const response = await apiClient.userGroupBinding.listUserGroupBinding({
      page,
      size: BINDING_PAGE_SIZE,
      fieldSelector: [fieldSelector],
    });
    const items = response.data.items ?? [];
    bindings.push(...items);
    if (items.length === 0 || bindings.length >= (response.data.total ?? bindings.length)) {
      break;
    }
  }

  return bindings;
}

export function fetchMembershipBindings(groupName: string): Promise<UserGroupBinding[]> {
  return fetchAllBindings(`roleRef.name=${groupName}`);
}

export function fetchGroupRoleBindings(groupName: string): Promise<UserGroupBinding[]> {
  return fetchAllBindings(`subjects=${userGroupSubject(groupName)}`);
}

export function resolveUserGroupApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "用户组不存在或已被删除。",
    conflict: "用户组名称已存在。",
  });
}
