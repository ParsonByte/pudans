import {
  type Metadata,
  type RoleBinding,
  type Subject,
  apiClient,
  axiosInstance,
} from "@pudans-dev/api-client";

import { resolveApiError, resolveApiErrorWithDetail } from "@/lib/api-error";

import { RBAC_API_GROUP } from "./binding-model";
import { ROLE_API_VERSION, ROLE_KIND } from "./role-model";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

const ROLE_BINDING_KIND = "RoleBinding";

const LIST_PAGE_SIZE = 500;

const TEMP_LIST_PAGE_SIZE = 200;

type RoleBindingSubjectInput = {
  kind: "User" | "UserGroup";
  name: string;
};

type TempRoleBindingSpec = {
  username?: string;
  roleNames?: string[];
  expiresAt?: string;
  reason?: string;
  ticketId?: string;
  requestedBy?: string;
  approvedBy?: string;
  approvedAt?: string;
  status?: string;
  approvalAuditId?: string;
  revokeAuditId?: string;
  maxDurationSeconds?: number;
};

export type TempRoleBindingRecord = {
  apiVersion: string;
  kind: string;
  metadata: Metadata;
  spec: TempRoleBindingSpec;
};

type TempRoleBindingList = {
  items?: TempRoleBindingRecord[];
  total?: number;
};

type TempRoleBindingInput = {
  username: string;
  roleNames: string[];
  expiresAt: string;
  reason?: string;
  ticketId?: string;
};

function toSubject(subject: RoleBindingSubjectInput): Subject {
  return {
    apiGroup: RBAC_API_GROUP,
    kind: subject.kind,
    name: subject.name,
  };
}

function toSubjects(subjects: readonly RoleBindingSubjectInput[]): Subject[] {
  return subjects.map(toSubject);
}

export async function fetchAllRoleBindings(): Promise<RoleBinding[]> {
  const bindings: RoleBinding[] = [];

  for (let page = 1; ; page += 1) {
    const response = await apiClient.roleBinding.listRoleBinding({
      page,
      size: LIST_PAGE_SIZE,
    });
    const items = response.data.items ?? [];
    bindings.push(...items);
    if (items.length === 0 || bindings.length >= (response.data.total ?? bindings.length)) {
      break;
    }
  }

  return bindings;
}

export async function createRoleBinding(
  name: string,
  roleName: string,
  subjects: readonly RoleBindingSubjectInput[],
): Promise<RoleBinding> {
  const response = await apiClient.roleBinding.createRoleBinding({
    roleBinding: {
      apiVersion: ROLE_API_VERSION,
      kind: ROLE_BINDING_KIND,
      metadata: { name },
      roleRef: { apiGroup: RBAC_API_GROUP, kind: ROLE_KIND, name: roleName },
      subjects: toSubjects(subjects),
    },
  });

  return response.data;
}

export async function updateRoleBinding(
  binding: RoleBinding,
  roleName: string,
  subjects: readonly RoleBindingSubjectInput[],
): Promise<RoleBinding> {
  const response = await apiClient.roleBinding.updateRoleBinding({
    name: binding.metadata.name,
    roleBinding: {
      ...binding,
      roleRef: { apiGroup: RBAC_API_GROUP, kind: ROLE_KIND, name: roleName },
      subjects: toSubjects(subjects),
    },
  });

  return response.data;
}

export async function deleteRoleBinding(name: string): Promise<void> {
  await apiClient.roleBinding.deleteRoleBinding({ name });
}

export async function fetchAllTempRoleBindings(): Promise<TempRoleBindingRecord[]> {
  const bindings: TempRoleBindingRecord[] = [];

  for (let page = 1; ; page += 1) {
    const response = await axiosInstance.get<TempRoleBindingList>(
      `${API_PREFIX}/temprolebindings`,
      { params: { page, size: TEMP_LIST_PAGE_SIZE } },
    );
    const items = response.data.items ?? [];
    bindings.push(...items);
    if (items.length === 0 || bindings.length >= (response.data.total ?? bindings.length)) {
      break;
    }
  }

  return bindings;
}

export async function applyTempRoleBinding(
  input: TempRoleBindingInput,
): Promise<TempRoleBindingRecord> {
  const response = await axiosInstance.post<TempRoleBindingRecord>(
    `${API_PREFIX}/temprolebindings`,
    input,
  );

  return response.data;
}

export async function updateTempRoleBinding(
  binding: TempRoleBindingRecord,
  input: TempRoleBindingInput,
): Promise<TempRoleBindingRecord> {
  const response = await axiosInstance.put<TempRoleBindingRecord>(
    `${API_PREFIX}/temprolebindings/${encodeURIComponent(binding.metadata.name)}`,
    {
      ...binding,
      spec: {
        ...binding.spec,
        username: input.username,
        roleNames: input.roleNames,
        expiresAt: input.expiresAt,
        reason: input.reason,
        ticketId: input.ticketId,
      },
    },
  );

  return response.data;
}

export async function approveTempRoleBinding(name: string): Promise<TempRoleBindingRecord> {
  const response = await axiosInstance.post<TempRoleBindingRecord>(
    `${API_PREFIX}/temprolebindings/${encodeURIComponent(name)}/approve`,
  );

  return response.data;
}

export async function revokeTempRoleBinding(name: string): Promise<TempRoleBindingRecord> {
  const response = await axiosInstance.post<TempRoleBindingRecord>(
    `${API_PREFIX}/temprolebindings/${encodeURIComponent(name)}/revoke`,
  );

  return response.data;
}

export async function deleteTempRoleBinding(name: string): Promise<void> {
  await axiosInstance.delete(`${API_PREFIX}/temprolebindings/${encodeURIComponent(name)}`);
}

export function resolveRoleApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "角色不存在或已被删除。",
    conflict: "角色名称已存在。",
  });
}

export function resolveRoleBindingApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "角色绑定不存在或已被删除。",
    conflict: "角色绑定名称已存在。",
  });
}

export function resolveTempRoleBindingApiError(error: unknown): string {
  return resolveApiErrorWithDetail(error, {
    notFound: "临时角色绑定不存在或已被删除。",
    conflict: "临时角色绑定名称已存在。",
  });
}
