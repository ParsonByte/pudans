"use client";

import { useEffect, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Skeleton } from "tdesign-react";

import { GlobalDialog, confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { ObjectDetails } from "../../../components/object-details";
import { PageBreadcrumb } from "../../../components/page-breadcrumb";
import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import { type StoragePolicy, deleteStoragePolicy, listStoragePolicies } from "../../../composables/_storage";
import { StoragePolicyCard } from "./components/storage-policy-card";
import { StoragePolicyEditorModal } from "./components/storage-policy-editor-modal";
import { StoragePolicyMetricGrid } from "./components/storage-policy-metric-grid";
import {
    STORAGE_POLICIES_QUERY_KEY,
    STORAGE_POLICIES_QUERY_ROOT,
    STORAGE_POLICY_LIST_SIZE,
    STORAGE_POLICY_PERMISSIONS,
    storagePolicyDisplayName,
} from "./composables/_storage-policies";

const POLICY_SKELETON_COUNT = 3;

export function StoragePoliciesView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const sessionQuery = useConsoleSession();
    const { allowed: canView, can } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        STORAGE_POLICY_PERMISSIONS.view,
    );
    const canDelete = can(STORAGE_POLICY_PERMISSIONS.manage) || can(STORAGE_POLICY_PERMISSIONS.delete);
    const canCreate = can(STORAGE_POLICY_PERMISSIONS.manage) || can(STORAGE_POLICY_PERMISSIONS.create);
    const [selected, setSelected] = useState<StoragePolicy>();
    const [editorOpen, setEditorOpen] = useState(false);
    const policiesQuery = useQuery({
        enabled: sessionQuery.isSuccess && canView,
        queryFn: async () => listStoragePolicies({ page: 1, size: STORAGE_POLICY_LIST_SIZE }),
        queryKey: STORAGE_POLICIES_QUERY_KEY,
    });
    const deletePolicy = useMutation({
        mutationFn: async (name: string) => deleteStoragePolicy(name),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "存储策略已删除。" });
            void queryClient.invalidateQueries({ queryKey: STORAGE_POLICIES_QUERY_ROOT });
        },
    });

    useEffect(() => {
        if (policiesQuery.error) {
            prompt.error({ content: consoleErrorMessage(policiesQuery.error) });
        }
    }, [policiesQuery.error, prompt]);

    const policies = policiesQuery.data ? (policiesQuery.data.items ?? []) : undefined;
    const refresh = () => void policiesQuery.refetch();
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

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                    <PageBreadcrumb current="存储策略" parents={["存储"]} />
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
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
                    <StoragePolicyMetricGrid
                        loading={policiesQuery.isPending}
                        policies={policies}
                        total={policiesQuery.data?.total}
                    />
                </div>

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
                                onDelete={canDelete ? () => confirmDelete(policy) : undefined}
                                onDetails={() => setSelected(policy)}
                                pending={deletePolicy.isPending}
                                policy={policy}
                            />
                        ))}
                    </div>
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
            {editorOpen ? <StoragePolicyEditorModal onClose={() => setEditorOpen(false)} /> : null}
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
