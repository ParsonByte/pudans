"use client";

import type { Role } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { type QueryClient, useMutation, useQueryClient } from "@tanstack/react-query";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";

function invalidateRoleData(queryClient: QueryClient) {
    void queryClient.invalidateQueries({ queryKey: ["console", "roles"] });
    void queryClient.invalidateQueries({ queryKey: ["console", "session"] });
    void queryClient.invalidateQueries({ queryKey: ["navigation"] });
}

export function useCreateRole() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (role: Role) => (await apiClient.role.createRole({ role })).data,
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (role) => {
            prompt.success("角色已创建。");
            invalidateRoleData(queryClient);
            return role;
        },
    });
}

export function useUpdateRole() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ name, role }: { name: string; role: Role; successMessage?: string }) =>
            (await apiClient.role.updateRole({ name, role })).data,
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: (role, variables) => {
            prompt.success(variables.successMessage ?? "角色已保存。");
            invalidateRoleData(queryClient);
            return role;
        },
    });
}

export function useDeleteRole() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (name: string) => apiClient.role.deleteRole({ name }),
        onError: (error) => prompt.error(consoleErrorMessage(error)),
        onSuccess: () => {
            prompt.success("角色已删除。");
            invalidateRoleData(queryClient);
        },
    });
}
