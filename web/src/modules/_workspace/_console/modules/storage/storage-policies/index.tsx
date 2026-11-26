"use client";

import { useEffect, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Card, Empty, RadioGroup, Skeleton } from "tdesign-react";

import { GlobalDialog, confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { ObjectDetails } from "../../../components/object-details";
import { PageBreadcrumb } from "../../../components/page-breadcrumb";
import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import {
    type StoragePolicy,
    configureStoragePolicyCors,
    deleteStoragePolicy,
    listStoragePolicies,
    listStoragePolicyTypes,
    testStoragePolicy,
} from "../../../composables/_storage";
import { StoragePolicyCard } from "./components/storage-policy-card";
import { StoragePolicyEditorModal } from "./components/storage-policy-editor-modal";
import { StoragePolicyMetricGrid } from "./components/storage-policy-metric-grid";
import { StoragePolicyOAuthModal } from "./components/storage-policy-oauth-modal";
import { StorageRoutesPanel } from "./components/storage-routes-panel";
import {
    STORAGE_POLICIES_QUERY_KEY,
    STORAGE_POLICIES_QUERY_ROOT,
    STORAGE_POLICY_LIST_SIZE,
    STORAGE_POLICY_PERMISSIONS,
    STORAGE_POLICY_TYPES_QUERY_KEY,
    isOneDriveStoragePolicy,
    storagePolicyDisplayName,
} from "./composables/_storage-policies";

const POLICY_SKELETON_COUNT = 3;

type StorageTab = "policies" | "routes";

const STORAGE_TAB_OPTIONS: Array<{ label: string; value: StorageTab }> = [
    { label: "存储策略", value: "policies" },
    { label: "存储路由", value: "routes" },
];

export function StoragePoliciesView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const sessionQuery = useConsoleSession();
    const { allowed: canView, can } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        STORAGE_POLICY_PERMISSIONS.view,
    );
    const canManage = can(STORAGE_POLICY_PERMISSIONS.manage);
    const canDelete = canManage || can(STORAGE_POLICY_PERMISSIONS.delete);
    const canCreate = canManage || can(STORAGE_POLICY_PERMISSIONS.create);
    const canOAuth = can(STORAGE_POLICY_PERMISSIONS.oauth);
    const canTest = can(STORAGE_POLICY_PERMISSIONS.test);
    const canUpdate = canManage || can(STORAGE_POLICY_PERMISSIONS.update);
    const [activeTab, setActiveTab] = useState<StorageTab>("policies");
    const [selected, setSelected] = useState<StoragePolicy>();
    const [editingPolicy, setEditingPolicy] = useState<StoragePolicy>();
    const [oauthPolicy, setOauthPolicy] = useState<StoragePolicy>();
    const [editorOpen, setEditorOpen] = useState(false);
    const policiesQuery = useQuery({
        enabled: sessionQuery.isSuccess && canView,
        queryFn: async () => listStoragePolicies({ page: 1, size: STORAGE_POLICY_LIST_SIZE }),
        queryKey: STORAGE_POLICIES_QUERY_KEY,
    });
    const typesQuery = useQuery({
        enabled: sessionQuery.isSuccess && canView,
        queryFn: listStoragePolicyTypes,
        queryKey: STORAGE_POLICY_TYPES_QUERY_KEY,
        staleTime: Number.POSITIVE_INFINITY,
    });
    const deletePolicy = useMutation({
        mutationFn: async (name: string) => deleteStoragePolicy(name),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "存储策略已删除。" });
            void queryClient.invalidateQueries({ queryKey: STORAGE_POLICIES_QUERY_ROOT });
        },
    });
    const testPolicy = useMutation({
        mutationFn: async (policy: StoragePolicy) => testStoragePolicy(policy.metadata.name),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (result, policy) => {
            const content = result.message || (result.success ? "存储连接测试通过。" : "存储连接测试失败。");
            const policyLabel = `存储策略 ${storagePolicyDisplayName(policy)}`;

            if (result.success) {
                prompt.success({ content: `${policyLabel}：${content}` });
            } else {
                prompt.error({ content: `${policyLabel}：${content}` });
            }
            void queryClient.invalidateQueries({ queryKey: STORAGE_POLICIES_QUERY_KEY });
        },
    });
    const configureCors = useMutation({
        mutationFn: async (policy: StoragePolicy) =>
            configureStoragePolicyCors(policy.metadata.name, [window.location.origin]),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (_result, policy) => {
            prompt.success({
                content: `存储策略 ${storagePolicyDisplayName(policy)}：已写入跨域来源 ${window.location.origin}。`,
            });
        },
    });

    useEffect(() => {
        if (policiesQuery.error) {
            prompt.error({ content: consoleErrorMessage(policiesQuery.error) });
        }
    }, [policiesQuery.error, prompt]);

    const policies = policiesQuery.data ? (policiesQuery.data.items ?? []) : undefined;
    const refresh = () => void policiesQuery.refetch();
    const supportsCors = (policy: StoragePolicy): boolean =>
        Boolean(
            typesQuery.data
                ?.find((descriptor) => descriptor.type === policy.spec.type)
                ?.capabilities?.includes("CORS_CONFIGURABLE"),
        );
    const confirmDelete = (policy: StoragePolicy) => {
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除存储策略 ${storagePolicyDisplayName(policy)} 吗？`,
            danger: true,
            onConfirm: () => void deletePolicy.mutateAsync(policy.metadata.name),
            title: "删除存储策略",
        });
    };

    if (sessionQuery.isPending) {
        return <Skeleton animation="gradient" theme="paragraph" />;
    }

    if (sessionQuery.error) {
        return (
            <Empty
                action={<Button onClick={() => void sessionQuery.refetch()}>重试</Button>}
                description={consoleErrorMessage(sessionQuery.error)}
                type="network-error"
            />
        );
    }

    if (!canView) {
        return <Empty description="当前账号没有查看存储策略的权限。" type="fail" />;
    }

    const renderPoliciesPanel = () => (
        <div className="flex min-w-0 flex-col gap-4">
            {policiesQuery.error ? (
                <StoragePolicyLoadError error={policiesQuery.error} onRetry={refresh} />
            ) : policiesQuery.isPending ? (
                <StoragePolicyGridSkeleton />
            ) : policies?.length === 0 ? (
                <Empty description="尚未配置存储策略" />
            ) : (
                <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
                    {policies?.map((policy) => (
                        <StoragePolicyCard
                            key={policy.metadata.name}
                            onCors={canTest && supportsCors(policy) ? () => configureCors.mutate(policy) : undefined}
                            onDelete={canDelete ? () => confirmDelete(policy) : undefined}
                            onDetails={() => setSelected(policy)}
                            onEdit={canUpdate ? () => setEditingPolicy(policy) : undefined}
                            onOAuth={
                                canOAuth && isOneDriveStoragePolicy(policy.spec.type)
                                    ? () => setOauthPolicy(policy)
                                    : undefined
                            }
                            onTest={canTest ? () => testPolicy.mutate(policy) : undefined}
                            pending={
                                (deletePolicy.isPending && deletePolicy.variables === policy.metadata.name) ||
                                (testPolicy.isPending &&
                                    testPolicy.variables?.metadata.name === policy.metadata.name) ||
                                (configureCors.isPending &&
                                    configureCors.variables?.metadata.name === policy.metadata.name)
                            }
                            policy={policy}
                        />
                    ))}
                </div>
            )}
        </div>
    );

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                    <PageBreadcrumb current={activeTab === "routes" ? "存储路由" : "存储策略"} parents={["存储"]} />
                    {activeTab === "policies" ? (
                        <div className="flex items-center gap-2">
                            <Button
                                icon={<RefreshIcon />}
                                loading={policiesQuery.isFetching}
                                onClick={refresh}
                                variant="outline"
                            >
                                刷新
                            </Button>
                            {canCreate ? (
                                <Button icon={<AddIcon />} onClick={() => setEditorOpen(true)} theme="primary">
                                    添加存储策略
                                </Button>
                            ) : null}
                        </div>
                    ) : null}
                </div>

                <RadioGroup
                    className="w-fit"
                    onChange={(value) => setActiveTab(value === "routes" ? "routes" : "policies")}
                    options={STORAGE_TAB_OPTIONS}
                    theme="button"
                    value={activeTab}
                    variant="default-filled"
                />

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
                    <StoragePolicyMetricGrid
                        loading={policiesQuery.isPending}
                        policies={policies}
                        total={policiesQuery.data?.total}
                    />
                </div>

                {activeTab === "policies" ? (
                    renderPoliciesPanel()
                ) : (
                    <StorageRoutesPanel canManage={canManage} policies={policies} />
                )}
            </div>

            <GlobalDialog
                footer={false}
                onClose={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected ? storagePolicyDisplayName(selected) : "存储策略详情"}
                width={720}
            >
                <ObjectDetails value={selected} />
            </GlobalDialog>
            {editingPolicy ? (
                <StoragePolicyEditorModal onClose={() => setEditingPolicy(undefined)} policy={editingPolicy} />
            ) : null}
            {editorOpen ? <StoragePolicyEditorModal onClose={() => setEditorOpen(false)} /> : null}
            {oauthPolicy ? (
                <StoragePolicyOAuthModal onClose={() => setOauthPolicy(undefined)} policy={oauthPolicy} />
            ) : null}
        </>
    );
}

function StoragePolicyGridSkeleton() {
    return (
        <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
            {Array.from({ length: POLICY_SKELETON_COUNT }).map((_, index) => (
                <Card bordered={false} key={index}>
                    <Skeleton animation="gradient" theme="avatar-text" />
                </Card>
            ))}
        </div>
    );
}

function StoragePolicyLoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
    return (
        <Card bordered={false}>
            <Empty
                action={
                    <Button icon={<RefreshIcon />} onClick={onRetry} theme="primary">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(error)}
                type="network-error"
            />
        </Card>
    );
}
