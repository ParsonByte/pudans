import { http } from "../../entry/api-client";
import type {
  ChangeMyPasswordRequest,
  CreateUserRequest,
  ListUsersParams,
  PasswordRequest,
  UpdateProfileRequest,
  UpdateUserRequest,
  User,
  UserList,
  UserPermission,
  UserPermissionVo,
  UserView,
} from "../models/user";

const CONSOLE_API_BASE = "/apis/console.api.pudans.tolim.cn/v1alpha1";
const UC_API_BASE = "/apis/uc.api.pudans.tolim.cn/v1alpha1";

function listParams(params: ListUsersParams = {}): ListUsersParams {
  return params;
}

/** GET /console/users */
export function listUsers(params: ListUsersParams = {}): Promise<UserList> {
  return http
    .get<UserList>(`${CONSOLE_API_BASE}/users`, { params: listParams(params) })
    .then((response) => response.data);
}

/** GET /console/users/{name} */
export function getUser(name: string): Promise<User> {
  return http
    .get<User>(`${CONSOLE_API_BASE}/users/${encodeURIComponent(name)}`)
    .then((response) => response.data);
}

/** POST /console/users */
export function createUser(payload: CreateUserRequest): Promise<User> {
  return http
    .post<User>(`${CONSOLE_API_BASE}/users`, payload)
    .then((response) => response.data);
}

/** PUT /console/users/{name} */
export function updateUser(name: string, payload: UpdateUserRequest): Promise<User> {
  return http
    .put<User>(`${CONSOLE_API_BASE}/users/${encodeURIComponent(name)}`, payload)
    .then((response) => response.data);
}

/** DELETE /console/users/{name} */
export function deleteUser(name: string): Promise<void> {
  return http
    .delete<void>(`${CONSOLE_API_BASE}/users/${encodeURIComponent(name)}`)
    .then(() => undefined);
}

/** GET /console/users/-/permissions */
export function getMyPermissions(): Promise<UserPermission> {
  return http
    .get<UserPermission>(`${CONSOLE_API_BASE}/users/-/permissions`)
    .then((response) => response.data);
}

/** GET /console/users/{name}/permissions */
export function getUserPermissions(name: string): Promise<UserPermission> {
  return http
    .get<UserPermission>(`${CONSOLE_API_BASE}/users/${encodeURIComponent(name)}/permissions`)
    .then((response) => response.data);
}

/** GET /uc/users/- */
export function getMyUserDetail(): Promise<UserView> {
  return http.get<UserView>(`${UC_API_BASE}/users/-`).then((response) => response.data);
}

/** GET /uc/users/-/permissions */
export function getMyUiPermissions(): Promise<UserPermissionVo> {
  return http
    .get<UserPermissionVo>(`${UC_API_BASE}/users/-/permissions`)
    .then((response) => response.data);
}

/** PUT /console/users/- */
export function updateCurrentUser(payload: UpdateProfileRequest): Promise<User> {
  return http
    .put<User>(`${CONSOLE_API_BASE}/users/-`, payload)
    .then((response) => response.data);
}

/** PUT /console/users/-/password */
export function changeMyPassword(payload: ChangeMyPasswordRequest | PasswordRequest): Promise<User> {
  return http
    .put<User>(`${CONSOLE_API_BASE}/users/-/password`, payload)
    .then((response) => response.data);
}
