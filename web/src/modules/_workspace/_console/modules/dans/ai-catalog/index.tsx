"use client";

import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AddIcon, FileSettingIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, RadioGroup } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import { DansPage } from "../components/page";
import { type CatalogEntry, deleteCatalogEntry, listAiTools, listCatalogEntries } from "../composables/_dans";
import { CatalogEntryDialog } from "./components/catalog-entry-dialog";
import { CatalogEntryGrid } from "./components/catalog-entry-grid";
import { CatalogTools } from "./components/catalog-tools";

const TABS = [
    { label: "目录项", value: "entries" },
    { label: "AI 工具", value: "tools" },
] as const;

type CatalogTab = (typeof TABS)[number]["value"];

export function AiCatalogView() {
    const [tab, setTab] = useState<CatalogTab>("entries");
    const [dialogOpen, setDialogOpen] = useState(false);
    const [editing, setEditing] = useState<CatalogEntry>();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const sessionQuery = useConsoleSession();
    const { allowed: canManage } = usePermission(
        { permissions: sessionQuery.data?.uiPermissions },
        "ai-catalog:manage",
    );
    const entriesQuery = useQuery({
        queryFn: () => listCatalogEntries(),
        queryKey: ["dans", "ai", "catalog-entries"],
    });
    const toolsQuery = useQuery({
        queryFn: listAiTools,
        queryKey: ["dans", "ai", "catalog-tools"],
    });
    const invalidateEntries = () => void queryClient.invalidateQueries({ queryKey: ["dans", "ai", "catalog-entries"] });
    const deleteMutation = useMutation({
        mutationFn: deleteCatalogEntry,
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            invalidateEntries();
            prompt.success({ content: "目录项已删除。" });
        },
    });
    const refresh = () => {
        void entriesQuery.refetch();
        void toolsQuery.refetch();
    };
    const openCreate = () => {
        setEditing(undefined);
        setDialogOpen(true);
    };
    const openEdit = (entry: CatalogEntry) => {
        setEditing(entry);
        setDialogOpen(true);
    };
    const confirmDelete = (entry: CatalogEntry) =>
        confirmGlobalDialog({
            content: `删除后不可恢复，确定删除目录项“${entry.label ?? entry.entryKey ?? entry.id}”吗？`,
            danger: true,
            onConfirm: () => deleteMutation.mutate(entry.id),
            title: "删除目录项",
        });

    return (
        <DansPage
            actions={
                <div className="flex flex-wrap items-center justify-end gap-2">
                    {tab === "entries" && canManage ? (
                        <Button icon={<AddIcon />} onClick={openCreate} theme="primary">
                            新建目录项
                        </Button>
                    ) : null}
                    <Button
                        icon={<RefreshIcon />}
                        loading={entriesQuery.isFetching || toolsQuery.isFetching}
                        onClick={refresh}
                        variant="outline"
                    >
                        刷新
                    </Button>
                </div>
            }
            icon={<FileSettingIcon size={22} />}
            section="AI"
            showOverview={false}
            title="能力目录"
        >
            <RadioGroup
                className="w-fit"
                onChange={(value) => setTab(String(value) as CatalogTab)}
                options={TABS.map((item) => ({ label: item.label, value: item.value }))}
                theme="button"
                value={tab}
                variant="default-filled"
            />
            {tab === "entries" ? (
                <CatalogEntryGrid
                    entries={entriesQuery.data ?? []}
                    error={entriesQuery.error}
                    loading={entriesQuery.isPending}
                    onCreate={canManage ? openCreate : undefined}
                    onDelete={canManage ? confirmDelete : undefined}
                    onEdit={canManage ? openEdit : undefined}
                    onRetry={() => void entriesQuery.refetch()}
                />
            ) : (
                <CatalogTools
                    error={toolsQuery.error}
                    loading={toolsQuery.isPending}
                    onRetry={() => void toolsQuery.refetch()}
                    tools={toolsQuery.data ?? []}
                />
            )}
            {dialogOpen ? (
                <CatalogEntryDialog entry={editing} onClose={() => setDialogOpen(false)} onSaved={invalidateEntries} />
            ) : null}
        </DansPage>
    );
}
