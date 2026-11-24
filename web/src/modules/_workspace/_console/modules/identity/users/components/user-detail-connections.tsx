"use client";

import { useMemo } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { LinkIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Skeleton, Tag, Typography } from "tdesign-react";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatDateTime } from "../../../../composables/_format";
import { useListedAuthProvidersQuery } from "../../composables/_auth-provider-directory";

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
    const connectionsQuery = useQuery({
        queryKey: ["console", "users", "connections", name],
        queryFn: async () => (await apiClient.user.listUserConnections({ name })).data,
    });
    const providersQuery = useListedAuthProvidersQuery(
        connectionsQuery.isSuccess &&
            (connectionsQuery.data ?? []).some((connection) => {
                const registrationId = connection.registrationId;

                return Boolean(registrationId && !builtinProviderLabel(registrationId));
            }),
    );
    const providerLabels = useMemo(
        () => new Map((providersQuery.data ?? []).map((provider) => [provider.name, provider.displayName])),
        [providersQuery.data],
    );
    const connections = connectionsQuery.data ?? [];

    return (
        <Card
            actions={
                <span className="text-xs text-[var(--td-text-color-secondary)]">
                    {connectionsQuery.isPending ? "加载中" : `${connections.length} 个连接`}
                </span>
            }
            bordered={false}
            shadow={false}
            title="身份连接"
        >
            {connectionsQuery.error ? (
                <Empty
                    action={
                        <Button onClick={() => void connectionsQuery.refetch()} size="small">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(connectionsQuery.error)}
                    title="身份连接加载失败"
                    type="fail"
                />
            ) : null}
            {connectionsQuery.isPending ? <Skeleton theme="paragraph" /> : null}
            {connectionsQuery.isSuccess ? (
                connections.length > 0 ? (
                    <div className="flex flex-col gap-4">
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
                                    className="flex min-w-0 items-start gap-3"
                                    key={connection.name ?? connection.providerUserId ?? index}
                                >
                                    <LinkIcon
                                        className="mt-0.5 shrink-0 text-[var(--td-text-color-secondary)]"
                                        size={20}
                                    />
                                    <div className="min-w-0 flex-1">
                                        <div className="truncate text-sm font-medium">{connectionLabel}</div>
                                        <div className="mt-1 flex min-w-0 items-center gap-2">
                                            <Tag
                                                theme="primary"
                                                title={providerLabel === "其他身份提供方" ? registrationId : undefined}
                                                variant="light"
                                            >
                                                {providerLabel}
                                            </Tag>
                                            <Typography.Text className="truncate text-xs" theme="secondary">
                                                {formatDateTime(connection.createdAt)}
                                            </Typography.Text>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <Empty description="暂无身份连接" />
                )
            ) : null}
        </Card>
    );
}
