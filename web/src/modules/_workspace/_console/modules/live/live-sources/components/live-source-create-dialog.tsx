"use client";

import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Select, Switch, Tag } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { DescriptionGrid } from "../../../../components/description-grid";
import { EmptyValue } from "../../../../components/empty-value";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatDateTime } from "../../../../composables/_format";
import {
    type LiveSourceCreateRequest,
    type LiveSourceHealth,
    createLiveSourceFromUrl,
    resolveLiveSourceDraft,
} from "../../../../composables/_live";
import { type StoragePolicy, listStoragePolicies } from "../../../../composables/_storage";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";
import {
    LIVE_SOURCE_DEFAULT_MERGE_SECONDS,
    LIVE_SOURCE_DEFAULT_SEGMENT_SECONDS,
    LIVE_SOURCE_MERGE_OPTIONS,
    LIVE_SOURCE_QUERY_ROOT,
    LIVE_SOURCE_SEGMENT_OPTIONS,
    liveSourceStateMeta,
} from "../composables/_live-sources";

type LiveSourceCreateDialogProps = {
    onClose: () => void;
    open: boolean;
};

type LiveSourceFormValues = {
    alias?: string;
    autoRecord: boolean;
    mergeIntervalSeconds: number;
    roomUrl: string;
    segmentSeconds: number;
    storagePolicy?: string;
};

const INITIAL_VALUES: LiveSourceFormValues = {
    autoRecord: true,
    mergeIntervalSeconds: LIVE_SOURCE_DEFAULT_MERGE_SECONDS,
    roomUrl: "",
    segmentSeconds: LIVE_SOURCE_DEFAULT_SEGMENT_SECONDS,
};

const STORAGE_POLICY_OPTION_LIMIT = 200;
const STORAGE_POLICY_OPTIONS_QUERY_KEY = [...LIVE_SOURCE_QUERY_ROOT, "storage-policies"] as const;

export function LiveSourceCreateDialog({ onClose, open }: LiveSourceCreateDialogProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const [preview, setPreview] = useState<LiveSourceHealth | null>(null);

    const policiesQuery = useQuery({
        queryFn: async () => listStoragePolicies({ page: 1, size: STORAGE_POLICY_OPTION_LIMIT }),
        queryKey: STORAGE_POLICY_OPTIONS_QUERY_KEY,
    });

    const previewMutation = useMutation({
        mutationFn: async (values: LiveSourceFormValues) => resolveLiveSourceDraft({ roomUrl: values.roomUrl.trim() }),
        onError: (error) => {
            setPreview(null);
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: (health) => setPreview(health),
    });

    const createMutation = useMutation({
        mutationFn: async (values: LiveSourceFormValues) => {
            const payload: LiveSourceCreateRequest = {
                autoRecord: values.autoRecord,
                mergeIntervalSeconds: values.mergeIntervalSeconds,
                roomUrl: values.roomUrl.trim(),
                segmentSeconds: values.segmentSeconds,
            };

            if (values.alias?.trim()) {
                payload.alias = values.alias.trim();
            }

            if (values.storagePolicy) {
                payload.storagePolicy = values.storagePolicy;
            }

            return createLiveSourceFromUrl(payload);
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: () => {
            prompt.success({ content: "直播源已创建。" });
            void queryClient.invalidateQueries({ queryKey: LIVE_SOURCE_QUERY_ROOT });
            onClose();
        },
    });

    const policyOptions = (policiesQuery.data?.items ?? []).map((policy: StoragePolicy) => ({
        label: policy.spec.displayName?.trim() || policy.metadata.name,
        value: policy.metadata.name,
    }));

    const readValues = async (): Promise<LiveSourceFormValues | null> => {
        const validateResult = await form.validate();

        if (validateResult !== true) {
            return null;
        }

        return form.getFieldsValue(true) as LiveSourceFormValues;
    };

    const handlePreview = async () => {
        const values = await readValues();

        if (values) {
            previewMutation.mutate(values);
        }
    };

    const handleSubmit = async () => {
        const values = await readValues();

        if (values) {
            createMutation.mutate(values);
        }
    };

    const previewState = liveSourceStateMeta(preview?.state);
    const previewItems = [
        { label: "直播间标题", value: preview?.roomTitle ? preview.roomTitle : <EmptyValue /> },
        { label: "主播", value: preview?.anchorName ? preview.anchorName : <EmptyValue /> },
        {
            label: "房间状态",
            value: (
                <div className="flex flex-wrap items-center gap-1">
                    <Tag theme={previewState.theme} variant="light">
                        {previewState.label}
                    </Tag>
                    {preview?.live ? (
                        <Tag theme="success" variant="light">
                            正在直播
                        </Tag>
                    ) : null}
                </div>
            ),
        },
        { label: "最近解析", value: formatDateTime(preview?.lastResolvedAt) },
        { label: "解析结果码", value: preview?.lastResolveCode ? preview.lastResolveCode : <EmptyValue /> },
        { label: "解析错误", value: preview?.lastResolveError ? preview.lastResolveError : <EmptyValue /> },
    ];

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    extra={
                        <Button
                            disabled={createMutation.isPending}
                            loading={previewMutation.isPending}
                            onClick={() => void handlePreview()}
                            variant="outline"
                        >
                            解析预览
                        </Button>
                    }
                    onCancel={onClose}
                    onSubmit={() => void handleSubmit()}
                    pending={createMutation.isPending}
                    submitLabel="保存"
                />
            }
            onClose={onClose}
            open={open}
            title="新建直播源"
            width={640}
        >
            <Form form={form} initialData={INITIAL_VALUES} labelAlign="top" layout="vertical">
                <div className="grid gap-x-4 sm:grid-cols-2">
                    <Form.FormItem
                        className="sm:col-span-2"
                        label="直播间链接"
                        name="roomUrl"
                        rules={[{ message: "请粘贴直播间链接。", required: true, whitespace: true }]}
                    >
                        <Input placeholder="粘贴直播间分享链接" />
                    </Form.FormItem>
                    <Form.FormItem label="别名" name="alias">
                        <Input placeholder="别名" />
                    </Form.FormItem>
                    <Form.FormItem label="存储策略" name="storagePolicy">
                        <Select
                            clearable
                            filterable
                            loading={policiesQuery.isPending}
                            options={policyOptions}
                            placeholder="使用默认存储策略"
                            valueType="value"
                        />
                    </Form.FormItem>
                    <Form.FormItem label="分段时长" name="segmentSeconds">
                        <Select options={LIVE_SOURCE_SEGMENT_OPTIONS} valueType="value" />
                    </Form.FormItem>
                    <Form.FormItem label="合并间隔" name="mergeIntervalSeconds">
                        <Select options={LIVE_SOURCE_MERGE_OPTIONS} valueType="value" />
                    </Form.FormItem>
                    <Form.FormItem label="自动录制" name="autoRecord">
                        <Switch />
                    </Form.FormItem>
                </div>
            </Form>
            {preview ? (
                <div className="mt-4 flex flex-col gap-2">
                    <span className="text-sm font-medium">解析结果</span>
                    <DescriptionGrid columns={2} items={previewItems} />
                </div>
            ) : null}
        </GlobalDialog>
    );
}
