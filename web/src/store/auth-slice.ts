import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { apiClient, type UserView } from "@pudans/api-client";

const CURRENT_USER_PATH = "/apis/uc.api.pudans.tolim.cn/v1alpha1/users/-";
const UI_PERMISSIONS_PATH = "/apis/console.api.pudans.tolim.cn/v1alpha1/users/-/ui-permissions";

export const ALL_PERMISSIONS = "*";

/** Console ui permission tags; holding any of them proves console access. */
export const CONSOLE_UI_PERMISSIONS = new Set([
  "dashboard:view",
  "users:manage",
  "roles:manage",
  "audit:view",
  "risk:manage",
  "webhooks:manage",
  "settings:manage",
  "auth-providers:manage",
  "plugins:manage",
  "ops:view",
]);

export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface CurrentUser {
  name: string;
  displayName: string;
  email?: string;
  avatar?: string;
}

export interface AuthState {
  status: AuthStatus;
  user: CurrentUser | null;
  permissions: readonly string[];
}

export interface SessionPayload {
  user: CurrentUser;
  permissions: readonly string[];
}

export function toCurrentUser(view: UserView): CurrentUser {
  const name = view.metadata?.name ?? "";
  return {
    name,
    displayName: view.spec?.displayName || name,
    email: view.spec?.email || undefined,
    avatar: view.spec?.avatar || undefined,
  };
}

export function hasAnyPermission(permissions: readonly string[], tags: readonly string[]): boolean {
  if (permissions.includes(ALL_PERMISSIONS)) {
    return true;
  }
  return tags.some((tag) => permissions.includes(tag));
}

export const loadSession = createAsyncThunk<SessionPayload | null>("auth/loadSession", async () => {
  const request = { pudansRetry: false as const };
  const [userResult, permissionsResult] = await Promise.allSettled([
    apiClient.http.get<UserView>(CURRENT_USER_PATH, request),
    apiClient.http.get<readonly string[]>(UI_PERMISSIONS_PATH, request),
  ]);

  if (userResult.status !== "fulfilled") {
    return null;
  }

  return {
    user: toCurrentUser(userResult.value),
    permissions:
      permissionsResult.status === "fulfilled" && Array.isArray(permissionsResult.value)
        ? permissionsResult.value
        : [],
  };
});

const initialState: AuthState = {
  status: "loading",
  user: null,
  permissions: [],
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    signOut(state) {
      state.status = "anonymous";
      state.user = null;
      state.permissions = [];
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadSession.pending, (state) => {
        state.status = "loading";
      })
      .addCase(loadSession.fulfilled, (state, action) => {
        if (action.payload === null) {
          state.status = "anonymous";
          state.user = null;
          state.permissions = [];
          return;
        }
        state.status = "authenticated";
        state.user = action.payload.user;
        state.permissions = [...action.payload.permissions];
      })
      .addCase(loadSession.rejected, (state) => {
        state.status = "anonymous";
        state.user = null;
        state.permissions = [];
      });
  },
});

export const { signOut } = authSlice.actions;
export const authReducer = authSlice.reducer;
export default authReducer;
