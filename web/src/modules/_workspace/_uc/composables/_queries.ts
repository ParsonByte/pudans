"use client";

import type {
    ListedAuthProvider,
    OidcProviderVo,
    TwoFactorAuthSettings,
    UserConnection,
    UserDevice,
    UserPermissionVo,
    UserView,
} from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

export const UC_PROFILE_QUERY_KEY = ["uc", "profile"] as const;
const UC_PERMISSIONS_QUERY_KEY = ["uc", "permissions"] as const;
export const UC_TWO_FACTOR_QUERY_KEY = ["uc", "two-factor"] as const;
export const UC_PASSKEYS_QUERY_KEY = ["uc", "passkeys"] as const;
export const UC_DEVICES_QUERY_KEY = ["uc", "devices"] as const;
export const UC_AUTH_PROVIDERS_QUERY_KEY = ["uc", "auth-providers"] as const;
const UC_OIDC_PROVIDERS_QUERY_KEY = ["uc", "oidc-providers"] as const;
export const UC_CONNECTIONS_QUERY_KEY = ["uc", "connections"] as const;

function listResponse<T>(value: unknown): T[] {
    return Array.isArray(value) ? (value as T[]) : [];
}

export function useSelfProfileQuery() {
    return useQuery({
        queryKey: UC_PROFILE_QUERY_KEY,
        queryFn: async () => (await apiClient.user.getMySelfUserDetail()).data as UserView,
    });
}

export function useSelfPermissionsQuery() {
    return useQuery({
        queryKey: UC_PERMISSIONS_QUERY_KEY,
        queryFn: async () => (await apiClient.user.getMySelfPermissions()).data as UserPermissionVo,
    });
}

export function useTwoFactorSettingsQuery() {
    return useQuery({
        queryKey: UC_TWO_FACTOR_QUERY_KEY,
        queryFn: async () =>
            (await apiClient.twoFactorAuth.getTwoFactorAuthenticationSettings()).data as TwoFactorAuthSettings,
    });
}

export function usePasskeysQuery() {
    return useQuery({
        queryKey: UC_PASSKEYS_QUERY_KEY,
        queryFn: async () => (await apiClient.passkey.listPasskeys()).data as UserConnection[],
    });
}

export function useDevicesQuery() {
    return useQuery({
        queryKey: UC_DEVICES_QUERY_KEY,
        queryFn: async () => listResponse<UserDevice>((await apiClient.device.listDevices()).data),
    });
}

export function useAuthProvidersQuery() {
    return useQuery({
        queryKey: UC_AUTH_PROVIDERS_QUERY_KEY,
        queryFn: async () => (await apiClient.authProvider.listAuthProviders()).data as ListedAuthProvider[],
    });
}

export function useOidcProvidersQuery() {
    return useQuery({
        queryKey: UC_OIDC_PROVIDERS_QUERY_KEY,
        queryFn: async () => (await apiClient.userConnection.listOidcProviders()).data as OidcProviderVo[],
    });
}

export function useConnectionsQuery() {
    return useQuery({
        queryKey: UC_CONNECTIONS_QUERY_KEY,
        queryFn: async () => listResponse<UserConnection>((await apiClient.userConnection.listMyConnections()).data),
    });
}
