"use client";

import { useEffect, useMemo, useState } from "react";

import type { SecretCatalogItem } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, RefreshIcon } from "tdesign-icons-react";
import { Breadcrumb, Button, Empty, RadioGroup, Select, Skeleton, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { SectionCard, confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { ConsoleTable } from "../../../components/console-table";
import { CopyableIdentifier } from "../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCount, formatDateTime } from "../../../composables/_format";
import { useConsoleSession } from "../../../composables/_session";
import { useConsoleTableState } from "../../../composables/_table";
import { useDashboardTheme } from "../../overview/dashboard/composables/_theme";
import { SecuritySearchInput } from "../components/security-search-input";
import { SecretDetailModal } from "./components/secret-detail-modal";
import { SecretEditorModal } from "./components/secret-editor-modal";
import { SecretKeyProfile } from "./components/secret-key-profile";
import { SecretKeyTags } from "./components/secret-key-tags";
import { SecretMetricGrid } from "./components/secret-metric-grid";
import { SecretTypeChart } from "./components/secret-type-chart";
import {
    DEFAULT_SECRET_TYPE,
    SECRET_PERMISSIONS,
    SECRET_QUERY_ROOT,
    secretEntryCount,
    secretTypeLabel,
} from "./composables/_secrets";

const { BreadcrumbItem } = Breadcrumb;

type SecretTab = "catalog" | "overview";

const SECRET_TAB_OPTIONS: Array<{ label: string; value: SecretTab }> = [
    { label: "密钥概览", value: "overview" },
    { label: "密钥目录", value: "catalog" },
];

type SecretEditorState = {
    item?: SecretCatalogItem;
    mode: "create" | "edit";
};

type SecretRow = SecretCatalogItem & {
    rowId: string;
};

export function SecretsView() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const table = useConsoleTableState();
    const { page, pageSize, setPage } = table;
    const sessionQuery = useConsoleSession();
    const { allowed: canView, can } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        SECRET_PERMISSIONS.view,
    );
    const canCreate = can(SECRET_PERMISSIONS.create);
    const canDelete = can(SECRET_PERMISSIONS.delete);
    const canUpdate = can(SECRET_PERMISSIONS.update);
    const { token } = useDashboardTheme();
    const keyword = table.keyword;
    const typeFilter = table.filters.type ?? "";
    const [activeTab, setActiveTab] = useState<SecretTab>("overview");
    const [selected, setSelected] = useState<SecretCatalogItem>();
    const [editor, setEditor] = useState<SecretEditorState>();
    const catalogQuery = useQuery({
        placeholderData: keepPreviousData,
        enabled: sessionQuery.isSuccess && canView,
        queryFn: async () =>
            (
                await apiClient.secret.getSecretCatalog({
                    keyword: keyword || undefined,
                    page,
                    size: pageSize,
                    type: typeFilter || undefined,
                })
            ).data,
        queryKey: [...SECRET_QUERY_ROOT, "catalog", page, pageSize, keyword, typeFilter],
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
    const deleteSecret = useMutation({
        mutationFn: async (name: string) => apiClient.secret.deleteSecret({ name }),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "密钥已删除。" });
            setSelected(undefined);
            void queryClient.invalidateQueries({ queryKey: SECRET_QUERY_ROOT });
        },
    });
    const catalog = catalogQuery.data;
    const summary = catalog?.summary;
    const items = useMemo(() => catalog?.items ?? [], [catalog?.items]);
    const rows = useMemo<SecretRow[]>(
        () =>
            items.map((item, index) => ({
                ...item,
                rowId:
                    item.name ?? `${item.type ?? DEFAULT_SECRET_TYPE}:${item.creationTimestamp ?? "unknown"}:${index}`,
            })),
        [items],
    );
    const totalPages = catalog?.totalPages ?? 0;
    const isSyncing = catalogQuery.isFetching || deleteSecret.isPending;
    const typeOptions = (summary?.types ?? [])
        .filter((type) => Boolean(type.type))
        .map((type) => ({
            label: `${secretTypeLabel(type.type)} · ${formatCount(type.count)}`,
            value: type.type as string,
        }));

    useEffect(() => {
        if (!catalogQuery.isSuccess) {
            return;
        }
        if (totalPages === 0 && page > 1) {
            setPage(1);
            return;
        }
        if (totalPages > 0 && page > totalPages) {
            setPage(totalPages);
        }
    }, [catalogQuery.isSuccess, page, setPage, totalPages]);

    useEffect(() => {
        if (catalogQuery.error) {
            prompt.error({ content: consoleErrorMessage(catalogQuery.error) });
        }
    }, [catalogQuery.error, prompt]);

    if (sessionQuery.isPending) {
        return <Skeleton theme="paragraph" />;
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
        return <Empty description="当前账号没有查看密钥目录的权限。" title="无权查看" type="fail" />;
    }

    const refresh = () => void catalogQuery.refetch();
    const openCreate = () => setEditor({ mode: "create" });
    const openEdit = (item: SecretCatalogItem) => setEditor({ item, mode: "edit" });
    const confirmDelete = (item: SecretCatalogItem) => {
        const name = item.name;
        if (!name) {
            return;
        }
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: (
                <>
                    确定删除密钥 <strong>{name}</strong> 吗？删除后无法恢复。
                </>
            ),
            danger: true,
            onConfirm: () => void deleteSecret.mutateAsync(name),
            title: "删除密钥",
        });
    };
    const columns: TableProps<SecretRow>["columns"] = [
        {
            cell: ({ row }) => <CopyableIdentifier mask value={row.name} />,
            colKey: "name",
            title: "名称",
            width: 220,
        },
        {
            cell: ({ row }) => (
                <Tooltip content={row.type ?? DEFAULT_SECRET_TYPE}>
                    <Tag theme={!row.type || row.type === DEFAULT_SECRET_TYPE ? "default" : "primary"} variant="light">
                        {secretTypeLabel(row.type)}
                    </Tag>
                </Tooltip>
            ),
            colKey: "type",
            title: "类型",
            width: 150,
        },
        {
            cell: ({ row }) => {
                const count = secretEntryCount(row);
                if (count === 0) {
                    return (
                        <Tag theme="warning" variant="light">
                            空密钥
                        </Tag>
                    );
                }

                return (
                    <div className="flex flex-col gap-0.5">
                        <span className="text-sm font-medium tabular-nums">{formatCount(count)} 个键</span>
                        <span className="text-[11px] whitespace-nowrap" style={{ color: token.colorTextTertiary }}>
                            文本 {formatCount(row.stringDataKeys?.length)} · 二进制 {formatCount(row.dataKeys?.length)}
                        </span>
                    </div>
                );
            },
            colKey: "scale",
            title: "规模",
            width: 132,
        },
        {
            cell: ({ row }) => <SecretKeyTags dataKeys={row.dataKeys} limit={4} stringDataKeys={row.stringDataKeys} />,
            colKey: "keys",
            title: "键名",
            width: 320,
        },
        {
            cell: ({ row }) => (
                <span className="whitespace-nowrap tabular-nums">{formatDateTime(row.creationTimestamp)}</span>
            ),
            colKey: "creationTimestamp",
            title: "创建时间",
            width: 172,
        },
        {
            cell: ({ row }) => (
                <div className="flex items-center gap-1">
                    <Button onClick={() => setSelected(row)} size="small" theme="primary" variant="text">
                        详情
                    </Button>
                    {canUpdate ? (
                        <Button onClick={() => openEdit(row)} size="small" theme="primary" variant="text">
                            编辑
                        </Button>
                    ) : null}
                    {canDelete ? (
                        <Button onClick={() => confirmDelete(row)} size="small" theme="danger" variant="text">
                            删除
                        </Button>
                    ) : null}
                </div>
            ),
            colKey: "actions",
            fixed: "right",
            title: "操作",
            width: canUpdate && canDelete ? 180 : 150,
        },
    ];

    return (
        <div className="flex w-full min-w-0 flex-col gap-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <Breadcrumb>
                    <BreadcrumbItem>安全与审计</BreadcrumbItem>
                    <BreadcrumbItem>密钥管理</BreadcrumbItem>
                </Breadcrumb>
                <div className="flex flex-wrap items-center gap-2">
                    <Button icon={<RefreshIcon />} loading={isSyncing} onClick={refresh} variant="outline">
                        刷新
                    </Button>
                    {canCreate ? (
                        <Button icon={<AddIcon />} onClick={openCreate} theme="primary">
                            新建密钥
                        </Button>
                    ) : null}
                </div>
            </div>

            <RadioGroup
                className="w-fit"
                onChange={(value) => setActiveTab(value as SecretTab)}
                options={SECRET_TAB_OPTIONS}
                theme="button"
                value={activeTab}
                variant="default-filled"
            />

            {activeTab === "overview" ? (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
                    <div
                        className="text-xs leading-5 md:col-span-2 xl:col-span-12"
                        style={{ color: token.colorTextTertiary }}
                    >
                        指标与类型分布基于全部密钥；密钥规模排行仅统计当前筛选页。
                    </div>
                    <SecretMetricGrid loading={catalogQuery.isPending} summary={summary} />
                    <div className="md:col-span-2 xl:col-span-7">
                        <SectionCard title="当前页密钥规模">
                            <SecretKeyProfile items={items} />
                        </SectionCard>
                    </div>
                    <div className="md:col-span-2 xl:col-span-5">
                        <SectionCard title="密钥类型分布">
                            <SecretTypeChart types={summary?.types} />
                        </SectionCard>
                    </div>
                </div>
            ) : null}

            <div hidden={activeTab !== "catalog"}>
                <SectionCard title="密钥目录">
                    <ConsoleTable
                        cardless
                        columns={columns}
                        data={rows}
                        error={catalogQuery.error}
                        loading={catalogQuery.isPending}
                        onRetry={catalogQuery.error ? () => void catalogQuery.refetch() : undefined}
                        pagination={{ ...table.pagination, total: catalog?.total ?? 0 }}
                        rowKey="rowId"
                        toolbar={
                            <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
                                <div className="min-w-0 sm:max-w-[320px] sm:flex-1">
                                    <SecuritySearchInput
                                        onChange={() => undefined}
                                        onSearch={(value) => table.setKeyword(value)}
                                        placeholder="搜索密钥名称或键名"
                                        value={keyword}
                                    />
                                </div>
                                <div className="flex items-center gap-2">
                                    <div className="min-w-0 flex-1 sm:w-[220px] sm:flex-none">
                                        <Select
                                            clearable
                                            onChange={(value) =>
                                                table.setFilter("type", (value as string | undefined) ?? null)
                                            }
                                            options={typeOptions}
                                            placeholder="全部类型"
                                            style={{ width: "100%" }}
                                            value={typeFilter || undefined}
                                        />
                                    </div>
                                    <Button
                                        disabled={!keyword && !typeFilter}
                                        onClick={() => table.reset()}
                                        variant="outline"
                                    >
                                        重置
                                    </Button>
                                </div>
                            </div>
                        }
                    />
                </SectionCard>
            </div>

            <SecretDetailModal item={selected} onClose={() => setSelected(undefined)} />
            {editor ? (
                <SecretEditorModal
                    item={editor.item}
                    key={`${editor.mode}:${editor.item?.name ?? "new"}`}
                    mode={editor.mode}
                    onClose={() => setEditor(undefined)}
                />
            ) : null}
        </div>
    );
}
