"use client";

import { apiClient } from "@pudans-dev/api-client";
import { type QueryClient, useMutation, useQueryClient } from "@tanstack/react-query";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { FEISHU_CONTACTS_QUERY_KEY } from "./_feishu-contacts";

type UserStatusTarget = {
    disabled: boolean;
    name: string;
};

type UserUpdateTarget = {
    bio?: string;
    canGroup: boolean;
    canUpdate: boolean;
    currentGroup?: string;
    loginName?: string;
    name: string;
    userGroup?: string;
};

type UserDataScopes = {
    feishuContacts?: boolean;
    userGroups?: boolean;
};

export function invalidateUserData(queryClient: QueryClient, scopes: UserDataScopes = {}) {
    void queryClient.invalidateQueries({ queryKey: ["console", "users"] });

    if (scopes.userGroups) {
        void queryClient.invalidateQueries({ queryKey: ["console", "user-groups"] });
    }

    if (scopes.feishuContacts) {
        void queryClient.invalidateQueries({ queryKey: FEISHU_CONTACTS_QUERY_KEY });
    }
}

export function useSetUserStatus() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ disabled, name }: UserStatusTarget) => {
            if (disabled) {
                await apiClient.user.disableUser({ username: name });
                return;
            }

            await apiClient.user.enableUser({ username: name });
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (_, { disabled }) => {
            prompt.success(disabled ? "用户已禁用。" : "用户已启用。");
            invalidateUserData(queryClient);
        },
    });
}

export function useDeleteUser() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (name: string) => apiClient.user.deleteUser({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("用户已删除。");
            invalidateUserData(queryClient, { feishuContacts: true });
        },
    });
}

export function useUpdateUser() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({
            bio,
            canGroup,
            canUpdate,
            currentGroup,
            loginName,
            name,
            userGroup,
        }: UserUpdateTarget) => {
            if (canUpdate) {
                await apiClient.user.updateUser({
                    name,
                    updateProfileRequest: { bio, loginName },
                });
            }

            const previousGroup = currentGroup ?? "";
            const nextGroup = userGroup ?? "";
            const groupChanged = canGroup && nextGroup !== previousGroup;

            if (groupChanged) {
                if (nextGroup) {
                    await apiClient.user.assignUserGroup({
                        assignGroupRequest: { userGroup: nextGroup },
                        name,
                    });
                } else {
                    await apiClient.user.removeUserGroup({ name });
                }
            }

            return { groupChanged };
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: ({ groupChanged }) => invalidateUserData(queryClient, { userGroups: groupChanged }),
    });
}

export function useResetUserPassword() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ name, password }: { name: string; password: string }) =>
            apiClient.user.changeUserPassword({
                name,
                passwordRequest: { password },
            }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            invalidateUserData(queryClient);
        },
    });
}
