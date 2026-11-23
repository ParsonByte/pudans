"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { type FeishuUserDraft, toFeishuUserItem } from "./_feishu-users";
import { invalidateUserData } from "./_user-actions";

export function useCreateFeishuUser() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (user: FeishuUserDraft) =>
            (await apiClient.user.createUser({ createUserRequest: toFeishuUserItem(user) })).data,
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => invalidateUserData(queryClient, { feishuContacts: true }),
    });
}

export function useBatchCreateFeishuUsers() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (users: FeishuUserDraft[]) =>
            (
                await apiClient.user.batchCreateUsers({
                    batchCreateRequest: { users: users.map(toFeishuUserItem) },
                })
            ).data,
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            invalidateUserData(queryClient, { feishuContacts: true });
        },
    });
}
