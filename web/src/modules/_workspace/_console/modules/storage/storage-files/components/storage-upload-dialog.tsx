"use client";

import { type ChangeEvent, useEffect, useMemo, useRef, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { UploadIcon } from "tdesign-icons-react";
import { Button, Progress, Select, Tag, Tooltip } from "tdesign-react";

import { GlobalDialog, ScrollArea, useDesignTokens } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatBytes } from "../../../../composables/_format";
import { listStoragePolicies } from "../../../../composables/_storage";
import {
    STORAGE_POLICIES_QUERY_KEY,
    STORAGE_POLICY_LIST_SIZE,
    storagePolicyDisplayName,
} from "../../storage-policies/composables/_storage-policies";
import {
    type StorageUploadController,
    type StorageUploadItem,
    type StorageUploadItemStatus,
} from "../composables/_storage-upload";

type StorageUploadDialogProps = {
    onClose: () => void;
    open: boolean;
    uploads: StorageUploadController;
};

const STATUS_META: Record<
    StorageUploadItemStatus,
    { label: string; theme: "danger" | "default" | "primary" | "success" | "warning" }
> = {
    canceled: { label: "已取消", theme: "default" },
    completed: { label: "已完成", theme: "success" },
    failed: { label: "失败", theme: "danger" },
    paused: { label: "已暂停", theme: "warning" },
    preparing: { label: "准备中", theme: "primary" },
    queued: { label: "等待上传", theme: "default" },
    uploading: { label: "上传中", theme: "primary" },
};

export function StorageUploadDialog({ onClose, open, uploads }: StorageUploadDialogProps) {
    const prompt = useMessagePrompt();
    const token = useDesignTokens();
    const fileInputRef = useRef<HTMLInputElement>(null);
    const attachTargetRef = useRef<string | undefined>(undefined);
    const [selectedPolicyState, setSelectedPolicy] = useState<string>();
    const policiesQuery = useQuery({
        queryFn: async () => listStoragePolicies({ page: 1, size: STORAGE_POLICY_LIST_SIZE }),
        queryKey: STORAGE_POLICIES_QUERY_KEY,
        enabled: open,
    });
    const readyPolicies = useMemo(
        () => (policiesQuery.data?.items ?? []).filter((policy) => policy.status?.ready),
        [policiesQuery.data],
    );
    const defaultPolicy = readyPolicies.find((policy) => policy.spec.defaultPolicy) ?? readyPolicies[0];
    const selectedPolicy =
        selectedPolicyState && readyPolicies.some((policy) => policy.metadata.name === selectedPolicyState)
            ? selectedPolicyState
            : defaultPolicy?.metadata.name;
    const policyNames = useMemo(() => {
        const map = new Map<string, string>();
        for (const policy of policiesQuery.data?.items ?? []) {
            map.set(policy.metadata.name, storagePolicyDisplayName(policy));
        }
        return map;
    }, [policiesQuery.data]);

    useEffect(() => {
        if (!policiesQuery.error) {
            return;
        }
        prompt.error({ content: consoleErrorMessage(policiesQuery.error) });
    }, [policiesQuery.error, prompt]);

    const policyLabel = (name: string) => policyNames.get(name) ?? name;
    const openPicker = () => {
        attachTargetRef.current = undefined;
        fileInputRef.current?.click();
    };
    const openAttachPicker = (id: string) => {
        attachTargetRef.current = id;
        fileInputRef.current?.click();
    };
    const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(event.target.files ?? []);
        event.target.value = "";
        const attachTarget = attachTargetRef.current;
        attachTargetRef.current = undefined;
        if (attachTarget) {
            const file = files[0];
            if (!file) {
                return;
            }
            if (!uploads.attachFile(attachTarget, file)) {
                prompt.error({ content: "请选择与待恢复记录同名且大小相同的文件。" });
            }
            return;
        }
        if (!selectedPolicy) {
            prompt.error({ content: "请先选择存储策略。" });
            return;
        }
        uploads.addFiles(files, selectedPolicy);
    };
    const policyOptions = readyPolicies.map((policy) => ({
        label: storagePolicyDisplayName(policy),
        value: policy.metadata.name,
    }));
    const selectingPolicy = policiesQuery.isPending || readyPolicies.length === 0;

    return (
        <GlobalDialog
            footer={
                <Button onClick={onClose} variant="outline">
                    关闭
                </Button>
            }
            onClose={onClose}
            open={open}
            title="上传文件"
            width={720}
        >
            <div className="flex min-w-0 flex-col gap-3">
                <div className="flex flex-wrap items-end gap-2">
                    <div className="min-w-0 flex-1">
                        <div className="mb-1 text-xs" style={{ color: token.colorTextSecondary }}>
                            存储策略
                        </div>
                        <Select
                            disabled={selectingPolicy}
                            loading={policiesQuery.isPending}
                            onChange={(value) => setSelectedPolicy(typeof value === "string" ? value : undefined)}
                            options={policyOptions}
                            placeholder={policiesQuery.isPending ? "正在加载存储策略" : "请选择存储策略"}
                            value={selectedPolicy}
                            valueType="value"
                        />
                    </div>
                    <Button
                        disabled={selectingPolicy || !selectedPolicy}
                        icon={<UploadIcon />}
                        onClick={openPicker}
                        theme="primary"
                    >
                        选择文件
                    </Button>
                    <input className="hidden" multiple onChange={handleFileChange} ref={fileInputRef} type="file" />
                </div>
                {!policiesQuery.isPending && readyPolicies.length === 0 ? (
                    <div className="text-xs" style={{ color: token.colorTextTertiary }}>
                        暂无就绪的存储策略，请先在存储策略页面完成配置。
                    </div>
                ) : null}
                <ScrollArea className="max-h-[52vh] pr-1">
                    {uploads.items.length === 0 ? (
                        <div
                            className="flex min-h-32 items-center justify-center rounded-xl border border-dashed text-sm"
                            style={{ borderColor: token.colorBorderSecondary, color: token.colorTextTertiary }}
                        >
                            尚未选择文件
                        </div>
                    ) : (
                        <div className="flex min-w-0 flex-col gap-3">
                            {uploads.items.map((item) => (
                                <StorageUploadRow
                                    item={item}
                                    key={item.id}
                                    onAttach={() => openAttachPicker(item.id)}
                                    onCancel={() => void uploads.cancel(item.id)}
                                    onDiscard={() => void uploads.discard(item.id)}
                                    onPause={() => uploads.pause(item.id)}
                                    onResume={() => uploads.resume(item.id)}
                                    onRetry={() => uploads.retry(item.id)}
                                    policyLabel={policyLabel(item.policy)}
                                />
                            ))}
                        </div>
                    )}
                </ScrollArea>
                <div className="text-xs" style={{ color: token.colorTextTertiary }}>
                    关闭窗口不会中断上传，返回本页可继续查看进度。
                </div>
            </div>
        </GlobalDialog>
    );
}

type StorageUploadRowProps = {
    item: StorageUploadItem;
    onAttach: () => void;
    onCancel: () => void;
    onDiscard: () => void;
    onPause: () => void;
    onResume: () => void;
    onRetry: () => void;
    policyLabel: string;
};

function StorageUploadRow({
    item,
    onAttach,
    onCancel,
    onDiscard,
    onPause,
    onResume,
    onRetry,
    policyLabel,
}: StorageUploadRowProps) {
    const token = useDesignTokens();
    const meta = STATUS_META[item.status];
    const percent = item.size > 0 ? Math.min(100, Math.floor((item.loadedBytes / item.size) * 100)) : 0;
    const progressStatus =
        item.status === "completed"
            ? ("success" as const)
            : item.status === "failed"
              ? ("error" as const)
              : item.status === "paused"
                ? ("warning" as const)
                : item.status === "uploading"
                  ? ("active" as const)
                  : undefined;
    const active = item.status === "preparing" || item.status === "uploading";
    const missingFile = !item.file && item.status === "paused";

    return (
        <div
            className="flex min-w-0 flex-col gap-2 rounded-xl border p-3"
            style={{ borderColor: token.colorBorderSecondary }}
        >
            <div className="flex min-w-0 flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                    <Tooltip content={item.originalName}>
                        <div className="truncate text-sm font-medium">{item.originalName}</div>
                    </Tooltip>
                    <div
                        className="mt-1 flex flex-wrap items-center gap-2 text-xs"
                        style={{ color: token.colorTextTertiary }}
                    >
                        <span className="tabular-nums">{formatBytes(item.size)}</span>
                        <span>{policyLabel}</span>
                        <Tag theme={meta.theme} variant="light">
                            {meta.label}
                        </Tag>
                        {item.partCount && item.partCount > 1 ? <span>{item.partCount} 个分片</span> : null}
                    </div>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                    {item.status === "uploading" ? (
                        <Button onClick={onPause} size="small" variant="outline">
                            暂停
                        </Button>
                    ) : null}
                    {missingFile ? (
                        <Button onClick={onAttach} size="small" variant="outline">
                            选择文件
                        </Button>
                    ) : null}
                    {item.status === "paused" && item.file ? (
                        <Button onClick={onResume} size="small" variant="outline">
                            继续
                        </Button>
                    ) : null}
                    {item.status === "failed" && item.file ? (
                        <Button onClick={onRetry} size="small" variant="outline">
                            重试
                        </Button>
                    ) : null}
                    {active || item.status === "queued" ? (
                        <Button onClick={onCancel} size="small" theme="danger" variant="text">
                            取消
                        </Button>
                    ) : (
                        <Button onClick={onDiscard} size="small" variant="text">
                            移除
                        </Button>
                    )}
                </div>
            </div>
            <Progress percentage={percent} status={progressStatus} theme="line" />
            {item.error ? (
                <div className="text-xs" style={{ color: token.colorError }}>
                    {item.error}
                </div>
            ) : null}
            {missingFile ? (
                <div className="text-xs" style={{ color: token.colorTextTertiary }}>
                    上传会话已保留，重新选择原文件后即可继续。
                </div>
            ) : null}
        </div>
    );
}
