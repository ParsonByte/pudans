"use client";

import { useState } from "react";

import { type AnnotationSetting, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { RefreshIcon } from "tdesign-icons-react";
import { Button, Input } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { formatDateTime } from "../../../composables/_format";

export function AnnotationsView() {
    const [targetRef, setTargetRef] = useState("");
    const [queryTarget, setQueryTarget] = useState("");
    const [selected, setSelected] = useState<AnnotationSetting>();
    const settingsQuery = useQuery({
        enabled: Boolean(queryTarget),
        queryKey: ["console", "annotations", queryTarget],
        queryFn: async () => {
            const response = await apiClient.annotationSetting.listAvailableAnnotationSettings({
                targetRef: queryTarget,
            });
            const data = response.data as unknown;

            return Array.isArray(data)
                ? (data as AnnotationSetting[])
                : ((data as { items?: AnnotationSetting[] }).items ?? []);
        },
    });

    const search = (value: string) => setQueryTarget(value.trim());
    const columns: TableProps<AnnotationSetting>["columns"] = [
        {
            cell: ({ row }) => row.metadata.name,
            colKey: "name",
            title: "名称",
        },
        {
            cell: ({ row }) => [row.spec.targetRef.group, row.spec.targetRef.kind].filter(Boolean).join("/") || "—",
            colKey: "targetRef",
            title: "目标类型",
        },
        {
            cell: ({ row }) => row.spec.formSchema?.length ?? 0,
            colKey: "fields",
            title: "表单字段",
        },
        {
            cell: ({ row }) => formatDateTime(row.metadata.creationTimestamp),
            colKey: "creationTimestamp",
            title: "创建时间",
        },
        {
            cell: ({ row }) => (
                <Button onClick={() => setSelected(row)} size="small" variant="text">
                    详情
                </Button>
            ),
            colKey: "actions",
            title: "操作",
        },
    ];

    return (
        <>
            <ConsoleTable
                columns={columns}
                data={settingsQuery.data}
                error={settingsQuery.error}
                extra={
                    queryTarget ? (
                        <Button
                            icon={<RefreshIcon />}
                            onClick={() => void settingsQuery.refetch()}
                            size="small"
                            variant="outline"
                        >
                            刷新
                        </Button>
                    ) : undefined
                }
                loading={settingsQuery.isFetching}
                onRetry={queryTarget ? () => void settingsQuery.refetch() : undefined}
                pagination={false}
                rowKey="metadata.name"
                title="注解设置"
                toolbar={
                    <div className="flex w-full flex-col gap-2 sm:flex-row">
                        <Input
                            clearable
                            label="targetRef"
                            onChange={(value) => {
                                const next = String(value ?? "");
                                setTargetRef(next);
                                if (!next) {
                                    setQueryTarget("");
                                }
                            }}
                            onEnter={(value) => search(String(value ?? ""))}
                            value={targetRef}
                        />
                        <Button onClick={() => search(targetRef)} theme="primary">
                            查询
                        </Button>
                    </div>
                }
            />
            <GlobalDialog
                footer={false}
                onClose={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.metadata.name}
                width={720}
            >
                <ObjectDetails value={selected} />
            </GlobalDialog>
        </>
    );
}
