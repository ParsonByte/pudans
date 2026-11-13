import type {
  Role,
  RoleBinding,
  RoleBindingList,
  RoleList,
  RoleRef,
} from "./generated";

export type { Role, RoleBinding, RoleBindingList, RoleList, RoleRef };

export interface ListRolesParams {
  page?: number;
  size?: number;
  labelSelector?: string[];
  fieldSelector?: string[];
  sort?: string[];
}
