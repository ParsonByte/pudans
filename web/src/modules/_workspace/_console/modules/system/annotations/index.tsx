"use client";

import { useState } from "react";

import { type AnnotationSetting, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, Input, Modal, type TableProps } from "antd";

import { ConsoleTable } from "../../../components/console-table";
import { ObjectDetails } from "../../../components/object-details";
import { formatDateTime } from "../../../composables/_format";

export function AnnotationsView() {
    const [targetRef, setTargetRef] = useState("");
    const [selected, setSelected] = useState<AnnotationSetting>();
    const settingsQuery = useQuery({
        enabled: Boolean(targetRef),
        queryKey: ["console", "annotations", targetRef],
        queryFn: async () => {
            const response = await apiClient.annotationSetting.listAvailableAnnotationSettings({ targetRef });
            const data = response.data as unknown;

            return Array.isArray(data)
                ? (data as AnnotationSetting[])
                : ((data as { items?: AnnotationSetting[] }).items ?? []);
        },
    });

    const columns: TableProps<AnnotationSetting>["columns"] = [
        { dataIndex: ["metadata", "name"], key: "name", title: "名称" },
        {
            key: "targetRef",
            render: (_, record) =>
                [record.spec.targetRef.group, record.spec.targetRef.kind].filter(Boolean).join("/") || "—",
            title: "目标类型",
        },
        {
            key: "fields",
            render: (_, record) => record.spec.formSchema?.length ?? 0,
            title: "表单字段",
        },
        {
            dataIndex: ["metadata", "creationTimestamp"],
            key: "creationTimestamp",
            render: (value: string) => formatDateTime(value),
            title: "创建时间",
        },
        {
            key: "actions",
            render: (_, record) => (
                <Button onClick={() => setSelected(record)} size="small">
                    详情
                </Button>
            ),
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
                    targetRef ? (
                        <Button onClick={() => void settingsQuery.refetch()} size="small">
                            刷新
                        </Button>
                    ) : undefined
                }
                loading={settingsQuery.isFetching}
                onRetry={targetRef ? () => void settingsQuery.refetch() : undefined}
                pagination={false}
                rowKey={(record) => record.metadata.name}
                title="注解设置"
                toolbar={
                    <Input.Search
                        allowClear
                        className="w-full sm:max-w-[460px]"
                        enterButton="查询"
                        onSearch={(value) => setTargetRef(value.trim())}
                        placeholder="输入 targetRef，例如 content.pudans.tolim.cn/Post"
                    />
                }
            />
            <Modal
                destroyOnHidden
                footer={null}
                onCancel={() => setSelected(undefined)}
                open={Boolean(selected)}
                title={selected?.metadata.name}
                width={720}
            >
                <ObjectDetails value={selected} />
            </Modal>
        </>
    );
}
