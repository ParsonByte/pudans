"use client";

import { useState } from "react";

import { useQueryClient } from "@tanstack/react-query";
import { Form, Select, Switch, Tag, Textarea } from "tdesign-react";

import { GlobalDialog, useDesignTokens } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import {
    type StoragePolicy,
    type StorageRoute,
    type StorageRoutePurposeDescriptor,
    putStorageRoute,
} from "../../../../composables/_storage";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";
import {
    STORAGE_ROUTES_QUERY_KEY,
    storagePolicyDisplayName,
    storageRoutePurposeMeta,
} from "../composables/_storage-policies";

type StorageRouteEditorModalProps = {
    descriptor: StorageRoutePurposeDescriptor;
    onClose: () => void;
    policies?: StoragePolicy[];
    route?: StorageRoute;
};

type StorageRouteFormValues = {
    description?: string;
    enabled?: boolean;
    policyName?: string;
};

export function StorageRouteEditorModal({ descriptor, onClose, policies, route }: StorageRouteEditorModalProps) {
    const prompt = useMessagePrompt();
    const token = useDesignTokens();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const meta = storageRoutePurposeMeta(descriptor.purpose);
    const selectedPolicyName = Form.useWatch("policyName", form) as string | undefined;
    const selectedPolicy = policies?.find((policy) => policy.metadata.name === selectedPolicyName);
    const policyOptions = (policies ?? []).map((policy) => ({
        label: `${storagePolicyDisplayName(policy)}${policy.status?.ready ? "" : "（未就绪）"}`,
        value: policy.metadata.name,
    }));
    const initialValues: StorageRouteFormValues = {
        description: route?.spec.description ?? "",
        enabled: route?.spec.enabled ?? true,
        policyName: route?.spec.policyName,
    };
    const submit = async (values: StorageRouteFormValues) => {
        const policyName = values.policyName?.trim();

        if (!policyName) {
            prompt.error({ content: "请选择存储策略。" });
            return;
        }

        setSubmitting(true);
        try {
            await putStorageRoute(descriptor.purpose, {
                description: values.description?.trim() || undefined,
                enabled: values.enabled !== false,
                policyName,
                purpose: descriptor.purpose,
            });
            await queryClient.invalidateQueries({ queryKey: STORAGE_ROUTES_QUERY_KEY });
            prompt.success({ content: "存储路由已保存。" });
            onClose();
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error) });
        } finally {
            setSubmitting(false);
        }
    };
    const close = () => {
        if (!submitting) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    disabled={!policies?.length}
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={submitting}
                    submitLabel="保存路由"
                />
            }
            onClose={close}
            open
            title={`${route ? "编辑" : "绑定"}存储路由 · ${meta.label}`}
            width={620}
        >
            <Form
                form={form}
                initialData={initialValues}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit(form.getFieldsValue(true) as StorageRouteFormValues);
                    }
                }}
            >
                <Form.FormItem label="用途">
                    <div className="flex flex-col gap-1">
                        <div>
                            <Tag theme="primary" variant="light">
                                {meta.label}
                            </Tag>
                        </div>
                        <div className="text-xs" style={{ color: token.colorTextTertiary }}>
                            {meta.description}
                        </div>
                    </div>
                </Form.FormItem>
                <Form.FormItem
                    label="存储策略"
                    name="policyName"
                    rules={[{ message: "请选择存储策略。", required: true }]}
                >
                    <Select
                        filterable
                        loading={!policies}
                        options={policyOptions}
                        placeholder={policies?.length ? "选择要写入的存储策略" : "暂无可用存储策略"}
                    />
                </Form.FormItem>
                {selectedPolicy && !selectedPolicy.status?.ready ? (
                    <div className="mb-4 text-xs" style={{ color: token.colorWarning }}>
                        该策略当前未就绪，路由在策略就绪前不可用。
                    </div>
                ) : null}
                <Form.FormItem label="启用路由" name="enabled">
                    <Switch />
                </Form.FormItem>
                <Form.FormItem label="备注" name="description">
                    <Textarea autosize={{ maxRows: 4, minRows: 2 }} maxlength={200} />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
