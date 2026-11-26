"use client";

import { useCallback, useMemo, useState } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { DataBaseIcon } from "tdesign-icons-react";
import { Alert, Button, Loading } from "tdesign-react";

import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import { DansPage } from "../components/page";
import {
    type AiChannel,
    type AiChannelModel,
    type AiChannelTestResult,
    type AiModel,
    type AiProviderDescriptor,
    createAiChannel,
    importAiChannelModels,
    listAiChannelModels,
    listAiModels,
    listAiProviders,
    listWorkspaceAiChannels,
    testAiChannel,
    updateAiChannel,
    updateAiChannelModel,
} from "../composables/_dans";
import { AddProviderDialog } from "./components/add-provider-dialog";
import { ChannelModels } from "./components/channel-models";
import { CustomModelDialog } from "./components/custom-model-dialog";
import { ProviderDetail, type ProviderDraft } from "./components/provider-detail";
import { ProviderOverview } from "./components/provider-overview";
import { type ProviderEntry, ProviderSidebar } from "./components/provider-sidebar";

const CHANNELS_KEY = ["dans", "ai", "channels"] as const;
const MODELS_KEY = ["dans", "ai", "models"] as const;
const ROUTES_KEY = ["dans", "ai", "channel-models"] as const;
const PROVIDERS_KEY = ["dans", "ai", "providers"] as const;

function configText(config: Record<string, unknown> | undefined, key: string): string | undefined {
    const value = config?.[key];
    return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function configuredProviderDescriptors(
    channels: AiChannel[],
    providers: AiProviderDescriptor[],
): AiProviderDescriptor[] {
    const builtIn = new Set(providers.map((provider) => provider.provider.toLowerCase()));
    const seen = new Set<string>();
    const descriptors: AiProviderDescriptor[] = [];

    for (const channel of channels) {
        const provider = channel.provider?.trim();
        if (!provider || builtIn.has(provider.toLowerCase()) || seen.has(provider.toLowerCase())) {
            continue;
        }
        seen.add(provider.toLowerCase());
        const displayName = configText(channel.config, "displayName") ?? provider;
        descriptors.push({
            catalogProviders: [provider],
            defaultBaseUrl: channel.baseUrl,
            defaultProtocol: channel.protocol ?? "chat-completions",
            description: configText(channel.config, "description"),
            displayName,
            logoUrl: configText(channel.config, "logoUrl"),
            protocols: channel.protocol ? [channel.protocol] : undefined,
            provider,
            requiresBaseUrl: true,
        });
    }

    return descriptors.sort((left, right) => left.displayName.localeCompare(right.displayName, "zh-CN"));
}

export function AiChannelsView() {
    const queryClient = useQueryClient();
    const [selectedProvider, setSelectedProvider] = useState<string>();
    const [providerSearch, setProviderSearch] = useState("");
    const [addOpen, setAddOpen] = useState(false);
    const [modelDialog, setModelDialog] = useState<{ model?: AiModel; route?: AiChannelModel }>();
    const [fetchingModels, setFetchingModels] = useState(false);
    const providersQuery = useQuery({
        queryFn: listAiProviders,
        queryKey: PROVIDERS_KEY,
    });
    const channelsQuery = useQuery({
        queryFn: listWorkspaceAiChannels,
        queryKey: CHANNELS_KEY,
    });
    const modelsQuery = useQuery({
        queryFn: listAiModels,
        queryKey: MODELS_KEY,
    });
    const routesQuery = useQuery({
        queryFn: listAiChannelModels,
        queryKey: ROUTES_KEY,
    });
    const { refetch: refetchProviders } = providersQuery;
    const { refetch: refetchChannels } = channelsQuery;
    const { refetch: refetchModels } = modelsQuery;
    const { refetch: refetchRoutes } = routesQuery;
    const providers = useMemo(() => providersQuery.data ?? [], [providersQuery.data]);
    const channels = useMemo(() => channelsQuery.data ?? [], [channelsQuery.data]);
    const models = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
    const routes = useMemo(() => routesQuery.data ?? [], [routesQuery.data]);
    const allProviders = useMemo(
        () => [...providers, ...configuredProviderDescriptors(channels, providers)],
        [channels, providers],
    );
    const entries = useMemo<ProviderEntry[]>(
        () =>
            allProviders.map((descriptor) => ({
                channel: channels.find((channel) => channel.provider === descriptor.provider),
                descriptor,
            })),
        [allProviders, channels],
    );

    const selectedEntry = selectedProvider
        ? entries.find((entry) => entry.descriptor.provider === selectedProvider)
        : undefined;
    const resolvedProvider = selectedEntry?.descriptor.provider;
    const selectedChannel = selectedEntry?.channel;
    const selectedRoutes = useMemo(
        () => routes.filter((route) => route.channelId === selectedChannel?.id),
        [routes, selectedChannel?.id],
    );
    const connectivityOptions = useMemo(
        () =>
            Array.from(
                new Set(
                    selectedRoutes
                        .map((route) => route.upstreamModelKey)
                        .filter((value): value is string => Boolean(value?.trim())),
                ),
            ).map((value) => ({ label: value, value })),
        [selectedRoutes],
    );
    const firstError = providersQuery.error ?? channelsQuery.error ?? modelsQuery.error ?? routesQuery.error;
    const refreshing =
        providersQuery.isFetching || channelsQuery.isFetching || modelsQuery.isFetching || routesQuery.isFetching;

    const refreshAll = useCallback(async () => {
        await Promise.all([refetchProviders(), refetchChannels(), refetchModels(), refetchRoutes()]);
    }, [refetchChannels, refetchModels, refetchProviders, refetchRoutes]);

    const persistChannel = useCallback(
        async (draft: ProviderDraft): Promise<AiChannel> => {
            if (!selectedEntry) {
                throw new Error("请选择服务商。");
            }
            const base = {
                baseUrl: draft.baseUrl,
                protocol: selectedEntry.descriptor.defaultProtocol,
                provider: selectedEntry.descriptor.provider,
                status: draft.enabled ? "active" : "disabled",
            };
            const saved = selectedChannel
                ? await updateAiChannel(selectedChannel.id, {
                      ...base,
                      credentials: draft.apiKey ? { apiKey: draft.apiKey } : undefined,
                  })
                : await createAiChannel({
                      ...base,
                      credentials: draft.apiKey ? { apiKey: draft.apiKey } : undefined,
                  });
            queryClient.setQueryData<AiChannel[]>(CHANNELS_KEY, (current = []) => {
                const existing = current.findIndex((channel) => channel.id === saved.id);
                if (existing < 0) {
                    return [...current, saved];
                }
                return current.map((channel) => (channel.id === saved.id ? saved : channel));
            });
            await queryClient.invalidateQueries({ queryKey: CHANNELS_KEY });
            return saved;
        },
        [queryClient, selectedChannel, selectedEntry],
    );

    const test = useCallback(
        async (channel: AiChannel, model?: string): Promise<AiChannelTestResult> => {
            const result = await testAiChannel(channel.id, model);
            await queryClient.invalidateQueries({ queryKey: CHANNELS_KEY });
            return result;
        },
        [queryClient],
    );

    const fetchModels = useCallback(async () => {
        if (!selectedChannel) {
            throw new Error("请先保存渠道配置。");
        }
        setFetchingModels(true);
        try {
            await importAiChannelModels(selectedChannel.id);
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: MODELS_KEY }),
                queryClient.invalidateQueries({ queryKey: ROUTES_KEY }),
            ]);
        } finally {
            setFetchingModels(false);
        }
    }, [queryClient, selectedChannel]);

    const toggleModel = useCallback(
        async (route: AiChannelModel, enabled: boolean) => {
            await updateAiChannelModel(route.id, { enabled });
            await queryClient.invalidateQueries({ queryKey: ROUTES_KEY });
        },
        [queryClient],
    );

    const showAllProviders = useCallback(() => setSelectedProvider(undefined), []);

    const toggleProvider = useCallback(
        async (entry: ProviderEntry, enabled: boolean) => {
            if (!entry.channel) {
                setSelectedProvider(entry.descriptor.provider);
                return;
            }
            const saved = await updateAiChannel(entry.channel.id, { status: enabled ? "active" : "disabled" });
            queryClient.setQueryData<AiChannel[]>(CHANNELS_KEY, (current = []) =>
                current.map((channel) => (channel.id === saved.id ? saved : channel)),
            );
            await queryClient.invalidateQueries({ queryKey: CHANNELS_KEY });
        },
        [queryClient],
    );

    return (
        <DansPage
            description="服务商凭据与代理地址加密保存；模型列表仅使用服务商真实模型接口返回的数据。"
            icon={<DataBaseIcon size={22} />}
            section="AI"
            showOverview={false}
            title="AI 服务商"
        >
            {firstError ? (
                <Alert
                    message={consoleErrorMessage(firstError)}
                    operation={
                        <Button onClick={() => void refreshAll()} size="small" variant="text">
                            重试
                        </Button>
                    }
                    theme="error"
                />
            ) : null}

            <div className="grid min-h-[640px] overflow-hidden rounded-xl border border-[var(--td-border-level-1-color)] bg-[var(--td-bg-color-container)] lg:grid-cols-[280px_minmax(0,1fr)]">
                <ProviderSidebar
                    entries={entries}
                    loading={providersQuery.isPending || channelsQuery.isPending}
                    onAdd={() => setAddOpen(true)}
                    onRefresh={refreshAll}
                    onSearchChange={setProviderSearch}
                    onSelect={setSelectedProvider}
                    onShowAll={showAllProviders}
                    refreshing={refreshing}
                    search={providerSearch}
                    selectedProvider={resolvedProvider}
                />

                <main className="min-w-0">
                    {providersQuery.isPending ? (
                        <div className="grid min-h-96 place-items-center">
                            <Loading />
                        </div>
                    ) : selectedEntry ? (
                        <>
                            <ProviderDetail
                                channel={selectedChannel}
                                descriptor={selectedEntry.descriptor}
                                key={`${selectedEntry.descriptor.provider}:${selectedChannel?.id ?? "new"}`}
                                modelOptions={connectivityOptions}
                                onPersist={persistChannel}
                                onTest={test}
                            />
                            <ChannelModels
                                channel={selectedChannel}
                                descriptor={selectedEntry.descriptor}
                                fetching={fetchingModels}
                                loading={routesQuery.isPending || modelsQuery.isPending}
                                models={models}
                                onAddCustom={() => setModelDialog({})}
                                onEdit={(route, model) => setModelDialog({ model, route })}
                                onFetch={fetchModels}
                                onRefresh={refreshAll}
                                onToggle={toggleModel}
                                routes={selectedRoutes}
                            />
                        </>
                    ) : (
                        <ProviderOverview
                            entries={entries}
                            onSelect={setSelectedProvider}
                            onToggle={toggleProvider}
                            search={providerSearch}
                        />
                    )}
                </main>
            </div>

            {addOpen ? (
                <AddProviderDialog
                    existingProviderIds={entries.map((entry) => entry.descriptor.provider)}
                    onClose={() => setAddOpen(false)}
                    onCreated={(created) => {
                        queryClient.setQueryData<AiChannel[]>(CHANNELS_KEY, (current = []) => [
                            ...current.filter((channel) => channel.id !== created.id),
                            created,
                        ]);
                        setSelectedProvider(created.provider ?? created.id);
                        void refreshAll();
                    }}
                />
            ) : null}

            {modelDialog && selectedChannel && selectedEntry ? (
                <CustomModelDialog
                    channelId={selectedChannel.id}
                    key={modelDialog.route?.id ?? "new"}
                    model={modelDialog.model}
                    onClose={() => setModelDialog(undefined)}
                    onSaved={() => {
                        void queryClient.invalidateQueries({ queryKey: MODELS_KEY });
                        void queryClient.invalidateQueries({ queryKey: ROUTES_KEY });
                    }}
                    provider={selectedEntry.descriptor}
                    route={modelDialog.route}
                />
            ) : null}
        </DansPage>
    );
}
