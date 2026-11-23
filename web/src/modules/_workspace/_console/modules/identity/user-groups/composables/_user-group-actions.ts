"use client";

import type { UserGroup } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { type QueryClient, useMutation, useQueryClient } from "@tanstack/react-query";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";

type UserGroupInvalidation = {
    affectsPermissions?: boolean;
    affectsUsers?: boolean;
};

export type AddUserGroupMembersResult = {
    added: string[];
    failures: { message: string; name: string }[];
};

function invalidateUserGroupData(
    queryClient: QueryClient,
    { affectsPermissions = false, affectsUsers = false }: UserGroupInvalidation = {},
) {
    void queryClient.invalidateQueries({ queryKey: ["console", "user-groups"] });
    void queryClient.invalidateQueries({ queryKey: ["console", "dashboard"] });

    if (affectsUsers) {
        void queryClient.invalidateQueries({ queryKey: ["console", "users"] });
    }
    if (affectsPermissions) {
        void queryClient.invalidateQueries({ queryKey: ["console", "session"] });
        void queryClient.invalidateQueries({ queryKey: ["navigation"] });
    }
}

export function useCreateUserGroup() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (userGroup: UserGroup) => (await apiClient.userGroup.createUserGroup({ userGroup })).data,
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (group) => {
            prompt.success("用户组已创建。");
            invalidateUserGroupData(queryClient);
            return group;
        },
    });
}

export function useUpdateUserGroup() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({
            name,
            userGroup,
        }: {
            affectsPermissions?: boolean;
            name: string;
            successMessage?: string;
            userGroup: UserGroup;
        }) => (await apiClient.userGroup.updateUserGroup({ name, userGroup })).data,
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (group, variables) => {
            prompt.success(variables.successMessage ?? "用户组已保存。");
            invalidateUserGroupData(queryClient, {
                affectsPermissions: variables.affectsPermissions,
                affectsUsers: true,
            });
            return group;
        },
    });
}

export function useDeleteUserGroup() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (name: string) => apiClient.userGroup.deleteUserGroup({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("用户组已删除。");
            invalidateUserGroupData(queryClient, { affectsPermissions: true, affectsUsers: true });
        },
    });
}

export function useAddUserGroupMembers() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({
            groupName,
            usernames,
        }: {
            groupName: string;
            usernames: string[];
        }): Promise<AddUserGroupMembersResult> => {
            const results = await Promise.allSettled(
                usernames.map(async (username) => {
                    await apiClient.user.assignUserGroup({
                        assignGroupRequest: { userGroup: groupName },
                        name: username,
                    });

                    return username;
                }),
            );
            const added: string[] = [];
            const failures: AddUserGroupMembersResult["failures"] = [];

            results.forEach((result, index) => {
                const name = usernames[index] ?? "未知用户";
                if (result.status === "fulfilled") {
                    added.push(result.value);
                    return;
                }

                failures.push({ message: consoleErrorMessage(result.reason), name });
            });

            return { added, failures };
        },
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (result) => {
            if (result.added.length > 0) {
                invalidateUserGroupData(queryClient, { affectsPermissions: true, affectsUsers: true });
            }
        },
    });
}

export function useRemoveUserGroupMember() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ username }: { groupName: string; username: string }) =>
            apiClient.user.removeUserGroup({ name: username }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (_, { username }) => {
            prompt.success(`已将 ${username} 移出用户组。`);
            invalidateUserGroupData(queryClient, { affectsPermissions: true, affectsUsers: true });
        },
    });
}
