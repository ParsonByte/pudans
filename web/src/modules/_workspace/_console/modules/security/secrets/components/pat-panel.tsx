"use client";

import { useMemo, useState } from "react";

import type { PersonalAccessToken } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Select } from "tdesign-react";
import type { PaginationProps, TableProps } from "tdesign-react";

import { SectionCard, confirmGlobalDialog } from "@/components/ui";
import { DEFAULT_PAGE_SIZE } from "@/hooks/table/use-pagination";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useMobile } from "@/hooks/ui/use-mobile";

import { ConsoleTable } from "../../../../components/console-table";
import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { useSudoConfirmation } from "../../../../components/sudo-confirm-dialog";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatDateTime } from "../../../../composables/_format";
import { useConsoleSession } from "../../../../composables/_session";
import { SudoCancelledError, withSudoRetry } from "../../../../composables/_sudo";
import { useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { SecuritySearchInput } from "../../components/security-search-input";
import {
    PAT_QUERY_ROOT,
    PAT_STATUS_OPTIONS,
    type PatStatus,
    normalizePatList,
    patDisplayName,
    patRoles,
    patStatus,
} from "../composables/_pat";
import { PatCreateModal } from "./pat-create-modal";
import { PatDetailModal } from "./pat-detail-modal";
import { PatMetricGrid } from "./pat-metric-grid";
import { PatRoleTags } from "./pat-role-tags";
import { PatStatusTag } from "./pat-status-tag";

type PatRow = PersonalAccessToken & {
    rowId: string;
};

type PatLifecycleAction = "delete" | "restore" | "revoke";

const PAT_LIFECYCLE_DIALOGS: Record<
    PatLifecycleAction,
    { confirmText: string; consequence: string; danger: boolean; title: string; verb: string }
> = {
    delete: { confirmText: "删除", consequence: "删除后无法恢复。", danger: true, title: "删除令牌", verb: "删除" },
    restore: {
        confirmText: "恢复",
        consequence: "恢复后若未过期即可继续使用。",
        danger: false,
        title: "恢复令牌",
        verb: "恢复",
    },
    revoke: {
        confirmText: "撤销",
        consequence: "撤销后使用该令牌的调用将立即失效。",
        danger: true,
        title: "撤销令牌",
        verb: "撤销",
    },
};

const PAT_LIFECYCLE_LABELS: Record<PatLifecycleAction, string> = {
    delete: "令牌已删除。",
    restore: "令牌已恢复。",
    revoke: "令牌已撤销。",
};

export function PatPanel() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const sessionQuery = useConsoleSession();
    const isMobile = useMobile();
    const { token } = useDashboardTheme();
    const { confirm, dialog: sudoDialog } = useSudoConfirmation();
    const [keyword, setKeyword] = useState("");
    const [statusFilter, setStatusFilter] = useState<PatStatus | "">("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
    const [selected, setSelected] = useState<PersonalAccessToken>();
    const [creating, setCreating] = useState(false);
    const listQuery = useQuery({
        queryFn: async () => normalizePatList((await apiClient.personalAccessToken.obtainPats()).data),
        queryKey: [...PAT_QUERY_ROOT],
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
    const lifecycle = useMutation({
        mutationFn: async ({ action, name }: { action: PatLifecycleAction; name: string }) =>
            withSudoRetry(async () => {
                if (action === "revoke") {
                    await apiClient.personalAccessToken.revokePat({ name });
                    return;
                }
                if (action === "restore") {
                    await apiClient.personalAccessToken.restorePat({ name });
                    return;
                }

                await apiClient.personalAccessToken.deletePat({ name });
            }, confirm),
        onError: (error) => {
            if (error instanceof SudoCancelledError) {
                return;
            }
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: (_result, { action }) => {
            prompt.success({ content: PAT_LIFECYCLE_LABELS[action] });
            setSelected(undefined);
            void queryClient.invalidateQueries({ queryKey: PAT_QUERY_ROOT });
        },
    });
    const items = useMemo(() => listQuery.data ?? [], [listQuery.data]);
    const filtered = useMemo(() => {
        const search = keyword.trim().toLowerCase();

        return items.filter((pat) => {
            if (statusFilter && patStatus(pat) !== statusFilter) {
                return false;
            }
            if (!search) {
                return true;
            }

            return [patDisplayName(pat), pat.metadata?.name, pat.spec?.description, ...patRoles(pat)]
                .filter((value): value is string => Boolean(value))
                .some((value) => value.toLowerCase().includes(search));
        });
    }, [items, keyword, statusFilter]);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const currentPage = Math.min(page, totalPages);
    const rows = useMemo<PatRow[]>(() => {
        const offset = (currentPage - 1) * pageSize;

        return filtered.slice(offset, offset + pageSize).map((pat, index) => ({
            ...pat,
            rowId: pat.metadata?.name ?? `pat-${offset + index}`,
        }));
    }, [filtered, currentPage, pageSize]);
    const pagination: PaginationProps = {
        current: currentPage,
        pageSize,
        showJumper: false,
        showPageSize: !isMobile,
        size: isMobile ? "small" : "medium",
        total: filtered.length,
        onChange: (pageInfo) => {
            if (pageInfo.pageSize !== pageSize) {
                setPageSize(pageInfo.pageSize);
                setPage(1);
                return;
            }

            setPage(pageInfo.current);
        },
    };
    const isSyncing = listQuery.isFetching || lifecycle.isPending;

    const refresh = () => void listQuery.refetch();
    const reset = () => {
        setKeyword("");
        setStatusFilter("");
        setPage(1);
    };
    const confirmAction = (action: PatLifecycleAction, pat: PersonalAccessToken) => {
        const name = pat.metadata?.name;
        if (!name) {
            return;
        }

        const dialog = PAT_LIFECYCLE_DIALOGS[action];
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: dialog.confirmText,
            content: (
                <>
                    确定{dialog.verb}令牌 <strong>{patDisplayName(pat)}</strong> 吗？{dialog.consequence}
                </>
            ),
            danger: dialog.danger,
            onConfirm: () => void lifecycle.mutateAsync({ action, name }),
            title: dialog.title,
        });
    };
    const columns: TableProps<PatRow>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-0.5">
                    <span className="truncate text-sm font-medium">{patDisplayName(row)}</span>
                    <CopyableIdentifier mask quiet value={row.metadata?.name} />
                </div>
            ),
            colKey: "name",
            title: "名称",
            width: 220,
        },
        {
            cell: ({ row }) => <PatStatusTag pat={row} />,
            colKey: "status",
            title: "状态",
            width: 110,
        },
        {
            cell: ({ row }) => <PatRoleTags limit={4} roles={patRoles(row)} />,
            colKey: "roles",
            title: "角色",
            width: 240,
        },
        {
            cell: ({ row }) => (
                <span className="whitespace-nowrap tabular-nums">{formatDateTime(row.spec?.expiresAt)}</span>
            ),
            colKey: "expiresAt",
            title: "有效期至",
            width: 172,
        },
        {
            cell: ({ row }) =>
                row.spec?.lastUsed ? (
                    <span className="whitespace-nowrap tabular-nums">{formatDateTime(row.spec.lastUsed)}</span>
                ) : (
                    <span className="text-xs" style={{ color: token.colorTextTertiary }}>
                        从未使用
                    </span>
                ),
            colKey: "lastUsed",
            title: "最后使用",
            width: 172,
        },
        {
            cell: ({ row }) => (
                <span className="whitespace-nowrap tabular-nums">
                    {formatDateTime(row.metadata?.creationTimestamp)}
                </span>
            ),
            colKey: "creationTimestamp",
            title: "创建时间",
            width: 172,
        },
        {
            cell: ({ row }) => {
                const status = patStatus(row);

                return (
                    <div className="flex items-center gap-1">
                        <Button onClick={() => setSelected(row)} size="small" theme="primary" variant="text">
                            详情
                        </Button>
                        {status === "active" || status === "expiring" ? (
                            <Button
                                onClick={() => confirmAction("revoke", row)}
                                size="small"
                                theme="warning"
                                variant="text"
                            >
                                撤销
                            </Button>
                        ) : null}
                        {status === "revoked" ? (
                            <Button
                                onClick={() => confirmAction("restore", row)}
                                size="small"
                                theme="primary"
                                variant="text"
                            >
                                恢复
                            </Button>
                        ) : null}
                        <Button onClick={() => confirmAction("delete", row)} size="small" theme="danger" variant="text">
                            删除
                        </Button>
                    </div>
                );
            },
            colKey: "actions",
            fixed: "right",
            title: "操作",
            width: 200,
        },
    ];

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
                <div
                    className="text-xs leading-5 md:col-span-2 xl:col-span-12"
                    style={{ color: token.colorTextTertiary }}
                >
                    个人访问令牌用于以当前用户身份调用平台 API；明文仅在创建时显示一次，指标基于全部令牌统计。
                </div>
                <PatMetricGrid items={items} loading={listQuery.isPending} />
            </div>

            <SectionCard title="个人访问令牌">
                <ConsoleTable
                    cardless
                    columns={columns}
                    data={rows}
                    error={listQuery.error}
                    loading={listQuery.isPending}
                    onRetry={listQuery.error ? () => void listQuery.refetch() : undefined}
                    pagination={pagination}
                    rowKey="rowId"
                    toolbar={
                        <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
                            <div className="min-w-0 sm:max-w-[320px] sm:flex-1">
                                <SecuritySearchInput
                                    onChange={() => undefined}
                                    onSearch={(value) => {
                                        setKeyword(value);
                                        setPage(1);
                                    }}
                                    placeholder="搜索名称、说明或角色"
                                    value={keyword}
                                />
                            </div>
                            <div className="flex flex-wrap items-center gap-2">
                                <div className="min-w-0 flex-1 sm:w-[180px] sm:flex-none">
                                    <Select
                                        clearable
                                        onChange={(value) => {
                                            setStatusFilter((value as PatStatus | undefined) ?? "");
                                            setPage(1);
                                        }}
                                        options={PAT_STATUS_OPTIONS}
                                        placeholder="全部状态"
                                        style={{ width: "100%" }}
                                        value={statusFilter || undefined}
                                    />
                                </div>
                                <Button disabled={!keyword && !statusFilter} onClick={reset} variant="outline">
                                    重置
                                </Button>
                                <Button icon={<RefreshIcon />} loading={isSyncing} onClick={refresh} variant="outline">
                                    刷新
                                </Button>
                                <Button icon={<AddIcon />} onClick={() => setCreating(true)} theme="primary">
                                    新建令牌
                                </Button>
                            </div>
                        </div>
                    }
                />
            </SectionCard>

            <PatDetailModal onClose={() => setSelected(undefined)} pat={selected} />
            {creating ? (
                <PatCreateModal onClose={() => setCreating(false)} username={sessionQuery.data?.username} />
            ) : null}
            {sudoDialog}
        </div>
    );
}
