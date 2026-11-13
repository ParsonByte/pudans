import type {
  AssignGroupRequest,
  BatchCreateRequest,
  BatchCreateResponse,
  ChangeMyPasswordRequest,
  CreateUserRequest,
  CreateUserRequestPayload,
  JsonPatchInner,
  PasswordRequest,
  UpdateProfileRequest,
  UpdateUserRequest,
  User,
  UserGroup,
  UserGroupBinding,
  UserGroupBindingList,
  UserGroupList,
  UserList,
  UserPermission,
  UserPermissionVo,
  UserView,
} from "./generated";

export type {
  AssignGroupRequest,
  BatchCreateRequest,
  BatchCreateResponse,
  ChangeMyPasswordRequest,
  CreateUserRequest,
  CreateUserRequestPayload,
  JsonPatchInner,
  PasswordRequest,
  UpdateProfileRequest,
  UpdateUserRequest,
  User,
  UserGroup,
  UserGroupBinding,
  UserGroupBindingList,
  UserGroupList,
  UserList,
  UserPermission,
  UserPermissionVo,
  UserView,
};

export interface ListUsersParams {
  page?: number;
  size?: number;
  labelSelector?: string[];
  fieldSelector?: string[];
  sort?: string[];
}

export interface ListUserGroupsParams {
  page?: number;
  size?: number;
  labelSelector?: string[];
  fieldSelector?: string[];
  sort?: string[];
}
