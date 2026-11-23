"use client";

import { useMemo } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Alert, Flex, Skeleton, Typography, theme } from "antd";
import { ShieldCheck } from "lucide-react";

import { SectionCard } from "@/components/ui";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { roleDisplayName } from "../../composables/_role";

type UserDetailAccessProps = {
    name: string;
};

const ROLE_LOOKUP_PAGE_SIZE = 1000;

export function UserDetailAccess({ name }: UserDetailAccessProps) {
    const { token } = theme.useToken();
    const permissionsQuery = useQuery({
        queryKey: ["console", "users", "permissions", name],
        queryFn: async () => (await apiClient.user.getUserPermissions({ name })).data,
    });
    const roles = permissionsQuery.data?.roles ?? [];
    const rolesQuery = useQuery({
        enabled: roles.length > 0,
        queryKey: ["console", "roles", "labels"],
        queryFn: async () =>
            (
                await apiClient.role.listRole({
                    page: 1,
                    size: ROLE_LOOKUP_PAGE_SIZE,
                })
            ).data.items,
    });
    const roleLabels = useMemo(
        () => new Map((rolesQuery.data ?? []).map((role) => [role.metadata.name, roleDisplayName(role)])),
        [rolesQuery.data],
    );
    const permissionCount = permissionsQuery.data?.permissions?.length ?? 0;
    const summary = permissionsQuery.isPending ? "加载中" : `${roles.length} 个角色 · ${permissionCount} 项权限`;

    return (
        <SectionCard
            className="!h-auto"
            extra={<Typography.Text type="secondary">{summary}</Typography.Text>}
            title="角色"
        >
            {permissionsQuery.isPending ? <Skeleton active paragraph={{ rows: 4 }} /> : null}
            {permissionsQuery.error ? (
                <Alert
                    action={<Typography.Link onClick={() => void permissionsQuery.refetch()}>重试</Typography.Link>}
                    message="角色与权限加载失败"
                    description={consoleErrorMessage(permissionsQuery.error)}
                    showIcon
                    type="error"
                />
            ) : null}
            {permissionsQuery.isSuccess ? (
                roles.length > 0 ? (
                    <Flex gap={8} vertical>
                        {roles.map((role) => {
                            const label = roleLabels.get(role);
                            const displayLabel = label ?? (rolesQuery.isPending ? "加载中" : "未知角色");

                            return (
                                <div
                                    className="flex min-w-0 items-center gap-3 rounded-xl px-3 py-2.5"
                                    key={role}
                                    style={{ backgroundColor: token.colorFillQuaternary }}
                                >
                                    <span
                                        className="grid h-8 w-8 shrink-0 place-items-center rounded-lg"
                                        style={{ backgroundColor: token.colorPrimaryBg, color: token.colorPrimary }}
                                    >
                                        <ShieldCheck size={15} />
                                    </span>
                                    <Typography.Text
                                        className="min-w-0 flex-1"
                                        ellipsis={{ tooltip: label ? undefined : role }}
                                    >
                                        {displayLabel}
                                    </Typography.Text>
                                </div>
                            );
                        })}
                    </Flex>
                ) : (
                    <Typography.Text type="secondary">未分配角色</Typography.Text>
                )
            ) : null}
        </SectionCard>
    );
}
