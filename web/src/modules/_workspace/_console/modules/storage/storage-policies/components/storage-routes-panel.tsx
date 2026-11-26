"use client";

import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Skeleton } from "tdesign-react";

import { confirmGlobalDialog, useDesignTokens } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import {
    type StoragePolicy,
    type StorageRoute,
    type StorageRoutePurposeDescriptor,
    deleteStorageRoute,
    listStorageRoutePurposes,
    listStorageRoutes,
    testStorageRoute,
} from "../../../../composables/_storage";
import {
    STORAGE_POLICIES_QUERY_KEY,
    STORAGE_ROUTES_QUERY_KEY,
    STORAGE_ROUTE_PURPOSES_QUERY_KEY,
    storageRoutePurposeMeta,
} from "../composables/_storage-policies";
import { StorageRouteCard } from "./storage-route-card";
import { StorageRouteEditorModal } from "./storage-route-editor-modal";

const ROUTE_SKELETON_COUNT = 2;

type StorageRoutesPanelProps = {
    canManage: boolean;
    policies?: StoragePolicy[];
};

type EditingRoute = {
    descriptor: StorageRoutePurposeDescriptor;
    route?: StorageRoute;
};

export function StorageRoutesPanel({ canManage, policies }: StorageRoutesPanelProps) {
    const prompt = useMessagePrompt();
    const token = useDesignTokens();
    const queryClient = useQueryClient();
    const [editing, setEditing] = useState<EditingRoute>();
    const routesQuery = useQuery({
        queryFn: listStorageRoutes,
        queryKey: STORAGE_ROUTES_QUERY_KEY,
    });
    const purposesQuery = useQuery({
        queryFn: listStorageRoutePurposes,
        queryKey: STORAGE_ROUTE_PURPOSES_QUERY_KEY,
        staleTime: Number.POSITIVE_INFINITY,
    });
    const deleteRoute = useMutation({
        mutationFn: async (descriptor: StorageRoutePurposeDescriptor) => deleteStorageRoute(descriptor.purpose),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (_result, descriptor) => {
            prompt.success({ content: `${storageRoutePurposeMeta(descriptor.purpose).label}路由已解除绑定。` });
            void queryClient.invalidateQueries({ queryKey: STORAGE_ROUTES_QUERY_KEY });
        },
    });
    const testRoute = useMutation({
        mutationFn: async (descriptor: StorageRoutePurposeDescriptor) => testStorageRoute(descriptor.purpose),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (result, descriptor) => {
            const label = storageRoutePurposeMeta(descriptor.purpose).label;
            const content = result.message || (result.success ? "存储连接测试通过。" : "存储连接测试失败。");

            if (result.success) {
                prompt.success({ content: `${label}：${content}` });
            } else {
                prompt.error({ content: `${label}：${content}` });
            }
            void queryClient.invalidateQueries({ queryKey: STORAGE_POLICIES_QUERY_KEY });
        },
    });
    const routesByPurpose = new Map((routesQuery.data ?? []).map((route) => [route.spec.purpose, route] as const));
    const refresh = () => {
        void routesQuery.refetch();
        void purposesQuery.refetch();
    };
    const confirmDelete = (descriptor: StorageRoutePurposeDescriptor) => {
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "解除绑定",
            content: `确定解除${storageRoutePurposeMeta(descriptor.purpose).label}的存储路由吗？${
                descriptor.fallbackToDefaultPolicy ? "解除后该用途将回退到默认策略。" : "解除后该用途将不可用。"
            }`,
            danger: true,
            onConfirm: () => void deleteRoute.mutateAsync(descriptor),
            title: "解除存储路由",
        });
    };

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                    <div className="text-sm font-medium">存储路由</div>
                    <div className="mt-0.5 text-xs" style={{ color: token.colorTextTertiary }}>
                        为生成结果和沙箱快照指定写入的存储策略。
                    </div>
                </div>
                <Button
                    icon={<RefreshIcon />}
                    loading={routesQuery.isFetching || purposesQuery.isFetching}
                    onClick={refresh}
                    variant="outline"
                >
                    刷新
                </Button>
            </div>

            {routesQuery.error || purposesQuery.error ? (
                <Card bordered={false}>
                    <Empty
                        action={
                            <Button icon={<RefreshIcon />} onClick={refresh} theme="primary">
                                重试
                            </Button>
                        }
                        description={consoleErrorMessage(routesQuery.error ?? purposesQuery.error)}
                        type="network-error"
                    />
                </Card>
            ) : routesQuery.isPending || purposesQuery.isPending ? (
                <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
                    {Array.from({ length: ROUTE_SKELETON_COUNT }).map((_, index) => (
                        <Card bordered={false} key={index}>
                            <Skeleton animation="gradient" theme="avatar-text" />
                        </Card>
                    ))}
                </div>
            ) : (
                <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
                    {(purposesQuery.data ?? []).map((descriptor) => (
                        <StorageRouteCard
                            descriptor={descriptor}
                            key={descriptor.purpose}
                            onDelete={canManage ? () => confirmDelete(descriptor) : undefined}
                            onEdit={
                                canManage
                                    ? () => setEditing({ descriptor, route: routesByPurpose.get(descriptor.purpose) })
                                    : undefined
                            }
                            onTest={canManage ? () => testRoute.mutate(descriptor) : undefined}
                            pending={
                                (deleteRoute.isPending && deleteRoute.variables?.purpose === descriptor.purpose) ||
                                (testRoute.isPending && testRoute.variables?.purpose === descriptor.purpose)
                            }
                            policies={policies}
                            route={routesByPurpose.get(descriptor.purpose)}
                        />
                    ))}
                </div>
            )}

            {editing ? (
                <StorageRouteEditorModal
                    descriptor={editing.descriptor}
                    onClose={() => setEditing(undefined)}
                    policies={policies}
                    route={editing.route}
                />
            ) : null}
        </div>
    );
}
