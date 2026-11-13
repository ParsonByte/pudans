import { http } from "../../entry/api-client";
import type { ListRolesParams, Role, RoleList } from "../models/role";

const CONSOLE_API_BASE = "/apis/console.api.pudans.tolim.cn/v1alpha1";

/** GET /console/roles */
export function listRoles(params: ListRolesParams = {}): Promise<RoleList> {
  return http
    .get<RoleList>(`${CONSOLE_API_BASE}/roles`, { params })
    .then((response) => response.data);
}

/** GET /console/roles/{name} */
export function getRole(name: string): Promise<Role> {
  return http
    .get<Role>(`${CONSOLE_API_BASE}/roles/${encodeURIComponent(name)}`)
    .then((response) => response.data);
}

/** POST /console/roles */
export function createRole(role: Role): Promise<Role> {
  return http
    .post<Role>(`${CONSOLE_API_BASE}/roles`, role)
    .then((response) => response.data);
}

/** PUT /console/roles/{name} */
export function updateRole(name: string, role: Role): Promise<Role> {
  return http
    .put<Role>(`${CONSOLE_API_BASE}/roles/${encodeURIComponent(name)}`, role)
    .then((response) => response.data);
}

/** DELETE /console/roles/{name} */
export function deleteRole(name: string): Promise<void> {
  return http
    .delete<void>(`${CONSOLE_API_BASE}/roles/${encodeURIComponent(name)}`)
    .then(() => undefined);
}
