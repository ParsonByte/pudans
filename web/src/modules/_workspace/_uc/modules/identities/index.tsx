"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

import type { ListedAuthProvider, OidcProviderVo, UserConnection } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { LinkIcon, RefreshIcon } from "tdesign-icons-react";
import { Avatar, Button, Empty, Skeleton, Tag, Typography } from "tdesign-react";

import { SectionCard, confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { formatDateTime } from "@/modules/_workspace/_console/composables/_format";

import {
    UC_AUTH_PROVIDERS_QUERY_KEY,
    UC_CONNECTIONS_QUERY_KEY,
    useAuthProvidersQuery,
    useConnectionsQuery,
    useOidcProvidersQuery,
} from "../../composables/_queries";
import {
    type PendingOidcBind,
    clearPendingOidcBind,
    readPendingOidcBind,
    savePendingOidcBind,
} from "./composables/_oidc-bind";

type IdentityProviderBinding = "none" | "oidc" | "url";

type IdentityProviderCard = {
    binding: IdentityProviderBinding;
    bindingUrl?: string;
    bound: boolean;
    connection?: UserConnection;
    description?: string;
    displayName: string;
    key: string;
    logo?: string;
    registrationId: string;
};

type OidcBindInput = {
    code: string;
    pending: PendingOidcBind;
    state: string;
};

function externalConnections(connections: UserConnection[]): UserConnection[] {
    return connections.filter((connection) => connection.spec?.registrationId !== "passkey");
}

function buildProviderCards(
    providers: ListedAuthProvider[],
    oidcProviders: OidcProviderVo[],
    connections: UserConnection[],
): IdentityProviderCard[] {
    const oidcByRegistration = new Map(
        oidcProviders
            .filter((provider) => Boolean(provider.registrationId))
            .map((provider) => [provider.registrationId as string, provider]),
    );
    const connectionByRegistration = new Map(
        externalConnections(connections)
            .filter((connection) => Boolean(connection.spec?.registrationId))
            .map((connection) => [connection.spec.registrationId, connection]),
    );
    const providerNames = new Set(providers.map((provider) => provider.name));
    const cards: IdentityProviderCard[] = [];

    for (const provider of providers) {
        const oidcProvider = oidcByRegistration.get(provider.name);
        const connection = connectionByRegistration.get(provider.name);
        const bound = Boolean(provider.isBound || connection);

        if (provider.authType === "FORM" || (provider.enabled === false && !bound && !oidcProvider)) {
            continue;
        }

        cards.push({
            binding: bound
                ? "none"
                : provider.supportsBinding && provider.bindingUrl
                  ? "url"
                  : oidcProvider
                    ? "oidc"
                    : "none",
            bindingUrl: provider.bindingUrl,
            bound,
            connection,
            description: provider.description || oidcProvider?.issuerUri,
            displayName: provider.displayName,
            key: provider.name,
            logo: provider.logo,
            registrationId: provider.name,
        });
    }

    for (const oidcProvider of oidcProviders) {
        const registrationId = oidcProvider.registrationId;

        if (!registrationId || providerNames.has(registrationId)) {
            continue;
        }

        const connection = connectionByRegistration.get(registrationId);

        cards.push({
            binding: connection ? "none" : "oidc",
            bound: Boolean(connection),
            connection,
            description: oidcProvider.issuerUri,
            displayName: oidcProvider.displayName || registrationId,
            key: `oidc:${registrationId}`,
            registrationId,
        });
    }

    return cards.sort((left, right) => {
        return (
            Number(right.bound) - Number(left.bound) || left.displayName.localeCompare(right.displayName, "zh-Hans-CN")
        );
    });
}

function providerInitial(displayName: string): string {
    return Array.from(displayName.trim())[0]?.toUpperCase() ?? "?";
}

export function IdentitiesView() {
    const pathname = usePathname();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const router = useRouter();
    const searchParams = useSearchParams();
    const callbackHandled = useRef(false);
    const [bindingProvider, setBindingProvider] = useState<string>();
    const providersQuery = useAuthProvidersQuery();
    const oidcProvidersQuery = useOidcProvidersQuery();
    const connectionsQuery = useConnectionsQuery();
    const cards = useMemo(
        () => buildProviderCards(providersQuery.data ?? [], oidcProvidersQuery.data ?? [], connectionsQuery.data ?? []),
        [connectionsQuery.data, oidcProvidersQuery.data, providersQuery.data],
    );
    const extraConnections = useMemo(
        () =>
            externalConnections(connectionsQuery.data ?? []).filter(
                (connection) => !cards.some((card) => card.registrationId === connection.spec?.registrationId),
            ),
        [cards, connectionsQuery.data],
    );
    const refreshIdentityQueries = async () => {
        await Promise.all([
            queryClient.invalidateQueries({ queryKey: UC_CONNECTIONS_QUERY_KEY }),
            queryClient.invalidateQueries({ queryKey: UC_AUTH_PROVIDERS_QUERY_KEY }),
        ]);
    };
    const refetchIdentityQueries = () =>
        Promise.all([providersQuery.refetch(), oidcProvidersQuery.refetch(), connectionsQuery.refetch()]);
    const { mutateAsync: bindOidcAsync } = useMutation({
        mutationFn: async ({ code, pending, state }: OidcBindInput) =>
            apiClient.userConnection.bindOidcIdentity({
                oidcBindRequest: {
                    code,
                    redirectUri: pending.redirectUri,
                    registrationId: pending.registrationId,
                    state,
                },
            }),
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error, "身份绑定失败，请重新尝试。") });
        },
        onSuccess: async () => {
            await refreshIdentityQueries();
            prompt.success({ content: "身份已绑定。" });
        },
    });
    const unbindConnection = useMutation({
        mutationFn: async (name: string) => apiClient.userConnection.unbindConnection({ name }),
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: async () => {
            await refreshIdentityQueries();
            prompt.success({ content: "身份已解绑。" });
        },
    });

    useEffect(() => {
        if (callbackHandled.current) {
            return;
        }

        const code = searchParams.get("code");
        const state = searchParams.get("state");

        if (!code || !state) {
            return;
        }

        callbackHandled.current = true;
        const nextParams = new URLSearchParams(searchParams.toString());
        nextParams.delete("code");
        nextParams.delete("state");
        const nextUrl = `${pathname}${nextParams.size > 0 ? `?${nextParams.toString()}` : ""}`;
        const pending = readPendingOidcBind();
        clearPendingOidcBind();

        if (!pending || pending.state !== state) {
            prompt.error({ content: "身份绑定状态已失效，请重新发起绑定。" });
            router.replace(nextUrl);
            return;
        }

        void bindOidcAsync({ code, pending, state })
            .catch(() => undefined)
            .finally(() => router.replace(nextUrl));
    }, [bindOidcAsync, pathname, prompt, router, searchParams]);

    const startOidcBind = async (registrationId: string) => {
        setBindingProvider(registrationId);

        try {
            const redirectUri = window.location.href;
            const response = await apiClient.userConnection.getOidcAuthorizeUrl({
                redirectUri,
                registrationId,
            });
            const authorizationUrl = response.data.authorizationUrl;
            const state = response.data.state;

            if (!authorizationUrl || !state) {
                throw new Error("身份提供方未返回授权地址。");
            }

            savePendingOidcBind({ redirectUri, registrationId, state });
            window.location.assign(authorizationUrl);
        } catch (error) {
            setBindingProvider(undefined);
            prompt.error({ content: consoleErrorMessage(error, "无法发起身份绑定。") });
        }
    };
    const startBind = (provider: IdentityProviderCard) => {
        if (provider.binding === "url" && provider.bindingUrl) {
            window.location.assign(provider.bindingUrl);
            return;
        }

        if (provider.binding === "oidc") {
            void startOidcBind(provider.registrationId);
        }
    };
    const confirmUnbind = (connection: UserConnection | undefined, displayName: string) => {
        const name = connection?.metadata?.name;

        if (!name) {
            return;
        }

        confirmGlobalDialog({
            confirmText: "解绑",
            content: "解绑后将不能使用该身份登录。请确认账号仍保留其他可用登录方式。",
            danger: true,
            onConfirm: () => void unbindConnection.mutateAsync(name),
            title: `解绑「${displayName}」`,
        });
    };
    const loadError = providersQuery.error ?? oidcProvidersQuery.error ?? connectionsQuery.error;
    const loading = providersQuery.isPending || oidcProvidersQuery.isPending || connectionsQuery.isPending;

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <SectionCard
                extra={
                    <Button
                        icon={<RefreshIcon />}
                        onClick={() => void refetchIdentityQueries()}
                        size="small"
                        variant="text"
                    >
                        刷新
                    </Button>
                }
                title="身份与登录方式"
            >
                <Typography.Paragraph className="!mt-0 !text-[var(--td-text-color-secondary)]">
                    绑定可信身份提供方后，可以直接使用对应账号登录。飞书资料由组织目录同步，解绑前请确保仍有其他登录方式。
                </Typography.Paragraph>
                {loadError ? (
                    <Empty
                        action={<Button onClick={() => void refetchIdentityQueries()}>重试</Button>}
                        description={consoleErrorMessage(loadError)}
                        title="身份提供商加载失败"
                        type="fail"
                    />
                ) : null}
                {loading ? <Skeleton theme="paragraph" /> : null}
                {!loading && !loadError ? (
                    cards.length > 0 ? (
                        <div className="grid gap-3 lg:grid-cols-2">
                            {cards.map((provider) => {
                                const canUnbind = provider.bound && Boolean(provider.connection?.metadata?.name);
                                const canBind = !provider.bound && provider.binding !== "none";
                                const binding = bindingProvider === provider.registrationId;

                                return (
                                    <div
                                        className="flex min-w-0 flex-col gap-4 rounded-xl border border-[var(--td-component-border)] p-4 sm:flex-row sm:items-center sm:justify-between"
                                        key={provider.key}
                                    >
                                        <div className="flex min-w-0 items-start gap-3">
                                            <Avatar image={provider.logo || undefined} shape="circle" size="40px">
                                                {providerInitial(provider.displayName)}
                                            </Avatar>
                                            <div className="min-w-0">
                                                <div className="flex flex-wrap items-center gap-2">
                                                    <Typography.Text className="truncate" strong>
                                                        {provider.displayName}
                                                    </Typography.Text>
                                                    <Tag theme={provider.bound ? "success" : "default"} variant="light">
                                                        {provider.bound ? "已绑定" : "未绑定"}
                                                    </Tag>
                                                </div>
                                                <Typography.Paragraph className="!mt-1 !mb-0 line-clamp-2 !text-xs !text-[var(--td-text-color-secondary)]">
                                                    {provider.description || "用于登录此账号的外部身份。"}
                                                </Typography.Paragraph>
                                                {provider.connection ? (
                                                    <Typography.Text className="mt-1 block text-xs" theme="secondary">
                                                        绑定于{" "}
                                                        {formatDateTime(
                                                            provider.connection.metadata?.creationTimestamp,
                                                        )}
                                                    </Typography.Text>
                                                ) : null}
                                            </div>
                                        </div>
                                        <div className="flex shrink-0 items-center gap-2 sm:justify-end">
                                            {provider.bound ? (
                                                <Button
                                                    disabled={!canUnbind || unbindConnection.isPending}
                                                    onClick={() =>
                                                        confirmUnbind(provider.connection, provider.displayName)
                                                    }
                                                    size="small"
                                                    theme="danger"
                                                    variant="outline"
                                                >
                                                    解绑
                                                </Button>
                                            ) : null}
                                            {canBind ? (
                                                <Button
                                                    loading={binding}
                                                    onClick={() => startBind(provider)}
                                                    size="small"
                                                    theme="primary"
                                                >
                                                    绑定
                                                </Button>
                                            ) : null}
                                            {!provider.bound && !canBind ? (
                                                <Button disabled size="small" variant="outline">
                                                    暂不可绑定
                                                </Button>
                                            ) : null}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        <Empty description="当前没有可用的身份提供商" />
                    )
                ) : null}
            </SectionCard>

            {extraConnections.length > 0 ? (
                <SectionCard title="其他身份连接">
                    <div className="flex flex-col divide-y divide-[var(--td-component-border)]">
                        {extraConnections.map((connection) => {
                            const displayName =
                                connection.spec?.displayName || connection.spec?.registrationId || "身份连接";

                            return (
                                <div
                                    className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0"
                                    key={connection.metadata?.name ?? connection.spec?.providerUserId}
                                >
                                    <div className="flex min-w-0 items-center gap-3">
                                        <LinkIcon className="shrink-0 text-[var(--td-text-color-secondary)]" />
                                        <div className="min-w-0">
                                            <Typography.Text className="truncate" strong>
                                                {displayName}
                                            </Typography.Text>
                                            <Typography.Text className="mt-0.5 block text-xs" theme="secondary">
                                                {connection.spec?.registrationId}
                                            </Typography.Text>
                                        </div>
                                    </div>
                                    <Button
                                        onClick={() => confirmUnbind(connection, displayName)}
                                        size="small"
                                        theme="danger"
                                        variant="text"
                                    >
                                        解绑
                                    </Button>
                                </div>
                            );
                        })}
                    </div>
                </SectionCard>
            ) : null}
        </div>
    );
}
