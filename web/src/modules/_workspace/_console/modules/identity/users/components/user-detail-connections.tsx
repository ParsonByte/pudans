"use client";

import { useMemo } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Alert, Empty, Flex, Skeleton, Tag, Typography, theme } from "antd";
import { Link2 } from "lucide-react";

import { SectionCard } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatDateTime } from "../../../../composables/_format";

type UserDetailConnectionsProps = {
    name: string;
};

const BUILTIN_PROVIDER_LABELS: Record<string, string> = {
    feishu: "飞书",
    local: "本地账号",
    passkey: "通行密钥",
};

function builtinProviderLabel(registrationId?: string): string | undefined {
    return registrationId ? BUILTIN_PROVIDER_LABELS[registrationId.toLowerCase()] : undefined;
}

export function UserDetailConnections({ name }: UserDetailConnectionsProps) {
    const { token } = theme.useToken();
    const connectionsQuery = useQuery({
        queryKey: ["console", "users", "connections", name],
        queryFn: async () => (await apiClient.user.listUserConnections({ name })).data,
    });
    const providersQuery = useQuery({
        enabled:
            connectionsQuery.isSuccess &&
            (connectionsQuery.data ?? []).some((connection) => {
                const registrationId = connection.registrationId;

                return Boolean(registrationId && !builtinProviderLabel(registrationId));
            }),
        queryKey: ["console", "auth-providers", "labels"],
        queryFn: async () => (await apiClient.authProvider.listAuthProviders()).data,
    });
    const providerLabels = useMemo(
        () => new Map((providersQuery.data ?? []).map((provider) => [provider.name, provider.displayName])),
        [providersQuery.data],
    );
    const connections = connectionsQuery.data ?? [];

    return (
        <SectionCard
            className="!h-auto"
            extra={
                <Typography.Text type="secondary">
                    {connectionsQuery.isPending ? "加载中" : `${connections.length} 个连接`}
                </Typography.Text>
            }
            title="身份连接"
        >
            {connectionsQuery.error ? (
                <Alert
                    action={<Typography.Link onClick={() => void connectionsQuery.refetch()}>重试</Typography.Link>}
                    message="身份连接加载失败"
                    description={consoleErrorMessage(connectionsQuery.error)}
                    showIcon
                    type="error"
                />
            ) : null}
            {connectionsQuery.isPending ? <Skeleton active paragraph={{ rows: 4 }} /> : null}
            {connectionsQuery.isSuccess ? (
                connections.length > 0 ? (
                    <Flex gap={10} vertical>
                        {connections.map((connection, index) => {
                            const registrationId = connection.registrationId;
                            const providerLabel =
                                builtinProviderLabel(registrationId) ??
                                (registrationId ? providerLabels.get(registrationId) : undefined) ??
                                "其他身份提供方";
                            const connectionLabel =
                                connection.displayName || (providerLabel === "飞书" ? "飞书账号" : "身份连接");

                            return (
                                <div
                                    className="rounded-xl border p-3"
                                    key={connection.name ?? connection.providerUserId ?? index}
                                    style={{ borderColor: token.colorBorderSecondary }}
                                >
                                    <Flex align="center" gap={8} justify="space-between">
                                        <Tag
                                            color="blue"
                                            title={providerLabel === "其他身份提供方" ? registrationId : undefined}
                                        >
                                            {providerLabel}
                                        </Tag>
                                        <Typography.Text className="whitespace-nowrap" type="secondary">
                                            {formatDateTime(connection.createdAt)}
                                        </Typography.Text>
                                    </Flex>
                                    <div className="mt-2 flex min-w-0 items-center gap-2">
                                        <Link2 className="shrink-0 text-neutral-400" size={14} />
                                        <Typography.Text
                                            className="min-w-0 flex-1"
                                            ellipsis={{ tooltip: connectionLabel }}
                                            strong
                                        >
                                            {connectionLabel}
                                        </Typography.Text>
                                    </div>
                                    <div
                                        className="mt-2 rounded-lg px-2.5 py-2"
                                        style={{ backgroundColor: token.colorFillQuaternary }}
                                    >
                                        <Typography.Text className="mb-1 block text-[11px]" type="secondary">
                                            提供方用户编号
                                        </Typography.Text>
                                        <CopyableIdentifier value={connection.providerUserId} />
                                    </div>
                                </div>
                            );
                        })}
                    </Flex>
                ) : (
                    <Empty description="暂无身份连接" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )
            ) : null}
        </SectionCard>
    );
}
