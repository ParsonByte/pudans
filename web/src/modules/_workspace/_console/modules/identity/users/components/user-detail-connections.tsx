"use client";

import { useMemo } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { LinkIcon } from "tdesign-icons-react";
import { Button, Empty, Skeleton, Tag, Typography } from "tdesign-react";

import { SectionCard, useDesignTokens } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
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
    const tokens = useDesignTokens();
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
        <SectionCard
            className="!h-auto"
            extra={
                <Typography.Text theme="secondary">
                    {connectionsQuery.isPending ? "加载中" : `${connections.length} 个连接`}
                </Typography.Text>
            }
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
                    <div className="flex flex-col gap-2.5">
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
                                    style={{ borderColor: tokens.colorBorderSecondary }}
                                >
                                    <div className="flex items-center justify-between gap-2">
                                        <Tag
                                            theme="primary"
                                            title={providerLabel === "其他身份提供方" ? registrationId : undefined}
                                            variant="light"
                                        >
                                            {providerLabel}
                                        </Tag>
                                        <Typography.Text className="whitespace-nowrap" theme="secondary">
                                            {formatDateTime(connection.createdAt)}
                                        </Typography.Text>
                                    </div>
                                    <div className="mt-2 flex min-w-0 items-center gap-2">
                                        <LinkIcon className="shrink-0" style={{ color: tokens.colorTextTertiary }} />
                                        <Typography.Text className="min-w-0 flex-1" ellipsis strong>
                                            {connectionLabel}
                                        </Typography.Text>
                                    </div>
                                    <div
                                        className="mt-2 rounded-lg px-2.5 py-2"
                                        style={{ backgroundColor: tokens.colorFillQuaternary }}
                                    >
                                        <Typography.Text className="mb-1 block text-[11px]" theme="secondary">
                                            提供方用户编号
                                        </Typography.Text>
                                        <CopyableIdentifier value={connection.providerUserId} />
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <Empty description="暂无身份连接" />
                )
            ) : null}
        </SectionCard>
    );
}
