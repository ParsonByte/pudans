import { type UserGroupBinding, apiClient } from "@pudans-dev/api-client";

import { resolveApiError } from "@/lib/api-error";

import { userGroupRoleBinding, userGroupRoleRef, userGroupSubject } from "./user-group-model";

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

export async function createGroupRoleBinding(
  name: string,
  groupName: string,
  roleName: string,
): Promise<UserGroupBinding> {
  const response = await apiClient.userGroupBinding.createUserGroupBinding({
    userGroupBinding: userGroupRoleBinding(name, groupName, roleName),
  });

  return response.data;
}

export async function updateGroupRoleBinding(
  binding: UserGroupBinding,
  roleName: string,
): Promise<UserGroupBinding> {
  const response = await apiClient.userGroupBinding.updateUserGroupBinding({
    name: binding.metadata.name,
    userGroupBinding: {
      ...binding,
      roleRef: userGroupRoleRef(roleName),
    },
  });

  return response.data;
}

export async function deleteGroupRoleBinding(name: string): Promise<void> {
  await apiClient.userGroupBinding.deleteUserGroupBinding({ name });
}

export function resolveUserGroupApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "用户组不存在或已被删除。",
    conflict: "用户组名称已存在。",
  });
}

export function resolveUserGroupBindingApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "用户组绑定不存在或已被删除。",
    conflict: "用户组绑定名称已存在。",
  });
}
