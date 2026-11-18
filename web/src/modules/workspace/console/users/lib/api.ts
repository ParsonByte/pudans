import { axiosInstance } from "@pudans-dev/api-client";

import { resolveApiError } from "@/lib/api-error";

export type CreateLocalUserRequest = {
  username: string;
  displayName?: string;
  email?: string;
  password: string;
  avatar?: string;
  userGroup?: string;
};

type CreatedUser = {
  metadata?: {
    name?: string;
  };
};

export type UserListItem = {
  name?: string;
  displayName?: string;
  email?: string;
  phone?: string;
  registeredAt?: string;
  disabled?: boolean;
  userGroup?: string;
  userGroupDisplayName?: string;
};

export type UserConnectionItem = {
  name?: string;
  registrationId?: string;
  providerUserId?: string;
  displayName?: string;
  updatedAt?: string;
  createdAt?: string;
};

export type UserActivityPoint = {
  date?: string;
  count?: number;
};

export type UserPage = {
  page?: number;
  size?: number;
  total?: number;
  sort?: string;
  items?: UserListItem[];
};

type UserListFilters = {
  keyword?: string;
  status?: string;
  userGroup?: string;
  sort?: string;
};

type FetchUsersParams = UserListFilters & {
  page: number;
  size: number;
};

type LocalBatchCreateFailure = {
  index: number;
  username?: string;
  message: string;
};

export type LocalBatchCreateResult = {
  created: number;
  failed: number;
  failures: LocalBatchCreateFailure[];
};

export async function fetchUsers(params: FetchUsersParams): Promise<UserPage> {
  const response = await axiosInstance.get<UserPage>("/apis/api.pudans.tolim.cn/v1alpha1/users", {
    params,
  });

  return response.data;
}

export async function fetchAllUsers(filters: UserListFilters = {}): Promise<UserListItem[]> {
  const size = 1000;
  const users: UserListItem[] = [];

  for (let page = 1; ; page += 1) {
    const result = await fetchUsers({ ...filters, page, size });
    const items = result.items ?? [];
    users.push(...items);
    if (items.length === 0 || users.length >= (result.total ?? users.length)) {
      break;
    }
  }

  return users;
}

export async function createLocalUser(body: CreateLocalUserRequest): Promise<string | undefined> {
  const response = await axiosInstance.post<CreatedUser>(
    "/apis/api.pudans.tolim.cn/v1alpha1/users/-/local",
    body,
  );

  return response.data.metadata?.name;
}

export async function createLocalUsers(
  users: CreateLocalUserRequest[],
): Promise<LocalBatchCreateResult> {
  const response = await axiosInstance.post<LocalBatchCreateResult>(
    "/apis/api.pudans.tolim.cn/v1alpha1/users/-/local/batch",
    { users },
  );

  return response.data;
}

export async function fetchUserConnections(name: string): Promise<UserConnectionItem[]> {
  const response = await axiosInstance.get<UserConnectionItem[]>(
    `/apis/api.pudans.tolim.cn/v1alpha1/users/${encodeURIComponent(name)}/connections`,
  );

  return response.data;
}

export async function fetchUserActivity(name: string, days = 14): Promise<UserActivityPoint[]> {
  const response = await axiosInstance.get<UserActivityPoint[]>(
    `/apis/api.pudans.tolim.cn/v1alpha1/users/${encodeURIComponent(name)}/activity`,
    { params: { days } },
  );

  return response.data;
}

export function resolveUserApiError(error: unknown): string {
  return resolveApiError(error, {
    notFound: "用户不存在或已被删除。",
    conflict: "用户名已存在。",
  });
}
