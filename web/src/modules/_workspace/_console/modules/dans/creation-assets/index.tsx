"use client";

import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { FolderIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Tabs, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatBytes, formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import { type Asset, type Subject, getAssetFacets, listAssets, listSubjects } from "../composables/_dans";

const TABS = [
    { label: "资产", value: "assets" },
    { label: "主体", value: "subjects" },
] as const;

type AssetTab = (typeof TABS)[number]["value"];

function facetCount(facets: { key?: string; count?: number }[] | undefined, key: string) {
    return facets?.find((facet) => facet.key === key)?.count;
}

export function CreationAssetsView() {
    const [tab, setTab] = useState<AssetTab>("assets");
    const facetsQuery = useQuery({
        queryFn: getAssetFacets,
        queryKey: ["dans", "creation", "asset-facets"],
    });
    const assetsQuery = useQuery({
        queryFn: () => listAssets(100),
        queryKey: ["dans", "creation", "assets"],
    });
    const subjectsQuery = useQuery({
        queryFn: () => listSubjects(100),
        queryKey: ["dans", "creation", "subjects"],
    });
    const assets = assetsQuery.data?.items ?? [];
    const subjects = subjectsQuery.data?.items ?? [];
    const activeQuery = tab === "assets" ? assetsQuery : subjectsQuery;
    const refresh = () => {
        void facetsQuery.refetch();
        void assetsQuery.refetch();
        void subjectsQuery.refetch();
    };
    const assetColumns: TableProps<Asset>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.name ?? "未命名资产"}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">{row.id}</span>
                </div>
            ),
            colKey: "name",
            title: "资产",
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-wrap gap-1">
                    <Tag variant="light">{row.kind ?? "—"}</Tag>
                    <Tag variant="light">{row.source ?? "—"}</Tag>
                </div>
            ),
            colKey: "kind",
            title: "类型 / 来源",
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-col gap-1 text-xs">
                    <span>{row.contentType ?? "—"}</span>
                    <span className="text-[var(--td-text-color-secondary)]">
                        {row.width && row.height ? `${row.width} × ${row.height}` : "—"}
                    </span>
                </div>
            ),
            colKey: "contentType",
            title: "文件",
        },
        { cell: ({ row }) => formatBytes(row.size), colKey: "size", title: "大小" },
        {
            cell: ({ row }) => (
                <Tag theme={row.status === "ready" ? "success" : "primary"} variant="light">
                    {row.status ?? "未知"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        { cell: ({ row }) => formatDateTime(row.createdAt), colKey: "createdAt", title: "创建时间" },
    ];
    const subjectColumns: TableProps<Subject>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.name ?? "未命名主体"}</span>
                    <span className="truncate text-xs text-[var(--td-text-color-secondary)]">
                        {row.description ?? row.id}
                    </span>
                </div>
            ),
            colKey: "name",
            title: "主体",
        },
        { cell: ({ row }) => row.kind ?? <EmptyValue />, colKey: "kind", title: "类型" },
        { cell: ({ row }) => row.visibility ?? <EmptyValue />, colKey: "visibility", title: "可见性" },
        {
            cell: ({ row }) => (
                <Tag theme={row.status === "active" ? "success" : "default"} variant="light">
                    {row.status ?? "未知"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        { cell: ({ row }) => row.ownerId ?? <EmptyValue />, colKey: "ownerId", title: "所属账号" },
        { cell: ({ row }) => formatDateTime(row.createdAt), colKey: "createdAt", title: "创建时间" },
    ];

    return (
        <DansPage
            actions={
                <Button
                    icon={<RefreshIcon />}
                    loading={activeQuery.isFetching || facetsQuery.isFetching}
                    onClick={refresh}
                    variant="outline"
                >
                    刷新
                </Button>
            }
            description="资产目录展示平台范围类型与来源分布，资产明细和主体使用管理员范围接口返回。"
            icon={<FolderIcon size={22} />}
            metrics={[
                { label: "资产总量", value: formatCountOrDash(facetsQuery.data?.total) },
                { label: "图片资产", value: formatCountOrDash(facetCount(facetsQuery.data?.kinds, "image")) },
                { label: "视频资产", value: formatCountOrDash(facetCount(facetsQuery.data?.kinds, "video")) },
                { label: "主体总量", value: formatCountOrDash(subjectsQuery.data?.items?.length) },
            ]}
            section="创作"
            title="资产与主体"
        >
            <Tabs
                list={TABS.map((item) => ({ label: item.label, value: item.value }))}
                onChange={(value) => setTab(String(value) as AssetTab)}
                value={tab}
            />
            {tab === "assets" ? (
                <ConsoleTable
                    columns={assetColumns}
                    data={assets}
                    error={assetsQuery.error}
                    loading={assetsQuery.isPending}
                    onRetry={() => void assetsQuery.refetch()}
                    rowKey="id"
                    title="资产列表"
                />
            ) : (
                <ConsoleTable
                    columns={subjectColumns}
                    data={subjects}
                    error={subjectsQuery.error}
                    loading={subjectsQuery.isPending}
                    onRetry={() => void subjectsQuery.refetch()}
                    rowKey="id"
                    title="主体列表"
                />
            )}
        </DansPage>
    );
}
