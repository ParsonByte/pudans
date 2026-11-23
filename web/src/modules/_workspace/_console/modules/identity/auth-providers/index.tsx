"use client";

import { useMemo, useState } from "react";

import type { AuthProvider } from "@pudans-dev/api-client";
import { AddIcon, LoginIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Skeleton } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { usePermission } from "@/hooks/utils/use-permission";

import { ConsoleAddCard } from "../../../components/add-card";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCountOrDash } from "../../../composables/_format";
import { useConsoleSession } from "../../../composables/_session";
import { IdentityLoadState } from "../components/identity-load-state";
import { IdentityPageOverview } from "../components/identity-page-overview";
import { LegacyProviderEditorModal } from "./components/legacy-provider-editor-modal";
import { ProviderCard } from "./components/provider-card";
import { ProviderEditorModal } from "./components/provider-editor-modal";
import { ProviderTypePicker } from "./components/provider-type-picker";
import {
    AUTH_PROVIDER_PERMISSIONS,
    type IdentityProviderCard,
    type IdentityProviderKind,
    buildIdentityProviderCards,
    removeProviderFromSettings,
    setProviderEnabled,
    useAuthProviderRecordsQuery,
    useAuthenticationSettingsQuery,
    useDeleteAuthProvider,
    useListedAuthProvidersQuery,
    useToggleAuthProvider,
    useUpdateAuthenticationSettings,
} from "./composables/_auth-providers";

export function AuthProvidersView() {
    const sessionQuery = useConsoleSession();
    const authProvidersQuery = useAuthProviderRecordsQuery();
    const listedProvidersQuery = useListedAuthProvidersQuery();
    const authenticationQuery = useAuthenticationSettingsQuery();
    const updateSettings = useUpdateAuthenticationSettings();
    const toggleAuthProvider = useToggleAuthProvider();
    const deleteAuthProvider = useDeleteAuthProvider();
    const { can } = usePermission({ permissions: sessionQuery.data?.uiPermissions }, AUTH_PROVIDER_PERMISSIONS.view);
    const canManage = can(AUTH_PROVIDER_PERMISSIONS.manage);
    const [pickerOpen, setPickerOpen] = useState(false);
    const [draftKind, setDraftKind] = useState<IdentityProviderKind>();
    const [editingProvider, setEditingProvider] = useState<IdentityProviderCard>();
    const [legacyProvider, setLegacyProvider] = useState<AuthProvider>();

    const authentication = useMemo(() => authenticationQuery.data ?? {}, [authenticationQuery.data]);
    const providers = useMemo(
        () =>
            buildIdentityProviderCards(authProvidersQuery.data ?? [], listedProvidersQuery.data ?? [], authentication),
        [authProvidersQuery.data, listedProvidersQuery.data, authentication],
    );
    const pending = updateSettings.isPending || toggleAuthProvider.isPending || deleteAuthProvider.isPending;
    const hasFeishu = providers.some((provider) => provider.kind === "feishu");
    const enabledCount = providers.filter((provider) => provider.configured && provider.enabled).length;
    const configuredCount = providers.filter((provider) => provider.configured).length;
    const queryError = authProvidersQuery.error ?? listedProvidersQuery.error ?? authenticationQuery.error;
    const queryPending =
        authProvidersQuery.isPending || listedProvidersQuery.isPending || authenticationQuery.isPending;
    const queryFetching =
        authProvidersQuery.isFetching || listedProvidersQuery.isFetching || authenticationQuery.isFetching;
    const overview = queryPending
        ? undefined
        : {
              configured: configuredCount,
              disabled: Math.max(0, configuredCount - enabledCount),
              enabled: enabledCount,
              total: providers.length,
          };

    const refresh = () => {
        void authProvidersQuery.refetch();
        void listedProvidersQuery.refetch();
        void authenticationQuery.refetch();
    };
    const openCreatePicker = () => {
        setPickerOpen(true);
        setDraftKind(undefined);
        setEditingProvider(undefined);
    };
    const selectProviderKind = (kind: IdentityProviderKind) => {
        setPickerOpen(false);
        setEditingProvider(undefined);
        setDraftKind(kind);
    };
    const editProvider = (provider: IdentityProviderCard) => {
        if (provider.source === "auth-provider" && provider.authProvider) {
            setLegacyProvider(provider.authProvider);
            return;
        }

        setEditingProvider(provider);
        setDraftKind(provider.kind);
    };
    const closeEditor = () => {
        setDraftKind(undefined);
        setEditingProvider(undefined);
    };
    const toggleProvider = (provider: IdentityProviderCard, enabled: boolean) => {
        if (provider.source === "auth-provider") {
            toggleAuthProvider.mutate({ enabled, name: provider.name });
            return;
        }

        updateSettings.mutate({
            authentication: setProviderEnabled(authentication, provider, enabled),
            successMessage: enabled ? "身份提供商已启用。" : "身份提供商已停用。",
        });
    };
    const confirmDelete = (provider: IdentityProviderCard) => {
        confirmGlobalDialog({
            confirmText: "删除",
            content:
                provider.source === "auth-provider"
                    ? `确定删除身份提供商 ${provider.displayName} 吗？删除后无法恢复。`
                    : `确定删除身份提供商 ${provider.displayName} 吗？登录页将不再展示该登录方式。`,
            danger: true,
            onConfirm: () => {
                if (provider.source === "auth-provider") {
                    void deleteAuthProvider.mutateAsync(provider.name);
                    return;
                }

                void updateSettings.mutateAsync({
                    authentication: removeProviderFromSettings(authentication, provider),
                    successMessage: "身份提供商已删除。",
                });
            },
            title: "删除身份提供商",
        });
    };

    if (sessionQuery.isPending) {
        return <Skeleton theme="paragraph" />;
    }

    if (sessionQuery.error) {
        return (
            <IdentityLoadState
                action={
                    <Button onClick={() => void sessionQuery.refetch()} theme="primary">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(sessionQuery.error)}
                title="无法读取当前会话权限"
            />
        );
    }

    if (!can(AUTH_PROVIDER_PERMISSIONS.view)) {
        return <IdentityLoadState description="当前账号没有查看身份提供商的权限。" title="无权查看" />;
    }

    if (queryError) {
        return (
            <IdentityLoadState
                action={
                    <Button icon={<RefreshIcon />} onClick={refresh} theme="primary">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(queryError)}
                title="身份提供商加载失败"
            />
        );
    }

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <IdentityPageOverview
                    actions={
                        <>
                            {canManage ? (
                                <Button icon={<AddIcon />} onClick={openCreatePicker} theme="primary">
                                    添加身份提供商
                                </Button>
                            ) : null}
                            <Button icon={<RefreshIcon />} loading={queryFetching || pending} onClick={refresh}>
                                刷新
                            </Button>
                        </>
                    }
                    icon={<LoginIcon />}
                    metrics={[
                        { label: "登录方式", value: formatCountOrDash(overview?.total) },
                        { label: "已启用", value: formatCountOrDash(overview?.enabled) },
                        { label: "已配置", value: formatCountOrDash(overview?.configured) },
                        { label: "已停用", value: formatCountOrDash(overview?.disabled) },
                    ]}
                    title="第三方登录"
                />

                {queryPending ? (
                    <ProviderGridSkeleton />
                ) : providers.length === 0 && !canManage ? (
                    <Empty description="尚未配置第三方登录方式" />
                ) : (
                    <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
                        {providers.map((provider) => (
                            <ProviderCard
                                canManage={canManage}
                                key={provider.key}
                                onDelete={
                                    canManage && provider.kind !== "feishu" && !provider.environmentManaged
                                        ? () => confirmDelete(provider)
                                        : undefined
                                }
                                onEdit={
                                    canManage && !provider.environmentManaged ? () => editProvider(provider) : undefined
                                }
                                onToggle={(enabled) => toggleProvider(provider, enabled)}
                                pending={pending}
                                provider={provider}
                            />
                        ))}
                        {canManage ? <ConsoleAddCard onClick={openCreatePicker} title="添加身份提供商" /> : null}
                    </div>
                )}
            </div>

            <ProviderTypePicker
                feishuConfigured={hasFeishu}
                onClose={() => setPickerOpen(false)}
                onSelect={selectProviderKind}
                open={pickerOpen}
            />
            {draftKind ? (
                <ProviderEditorModal
                    authentication={authentication}
                    key={`${draftKind}:${editingProvider?.key ?? "create"}`}
                    kind={draftKind}
                    onClose={closeEditor}
                    provider={editingProvider}
                />
            ) : null}
            {legacyProvider ? (
                <LegacyProviderEditorModal
                    key={legacyProvider.metadata.name}
                    onClose={() => setLegacyProvider(undefined)}
                    provider={legacyProvider}
                />
            ) : null}
        </>
    );
}

function ProviderGridSkeleton() {
    return (
        <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
                <Card className="min-h-56" key={index}>
                    <Skeleton theme="avatar-text" />
                </Card>
            ))}
        </div>
    );
}
