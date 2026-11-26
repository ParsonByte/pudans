"use client";

import { useMemo, useState } from "react";

import { Sparkle } from "reicon-react";
import { Button, Form, Input, Select } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import { type AiChannel, createAiChannel } from "../../composables/_dans";
import { ProviderSecretInput } from "./provider-secret-input";

const PROVIDER_ID_PATTERN = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/;

const REQUEST_FORMATS = [
    { label: "OpenAI", value: "openai" },
    { label: "Anthropic", value: "anthropic" },
] as const;

type AddProviderDialogProps = {
    existingProviderIds: string[];
    onClose: () => void;
    onCreated: (channel: AiChannel) => void;
};

type AddProviderForm = {
    baseUrl: string;
    description?: string;
    displayName?: string;
    format: "anthropic" | "openai";
    logoUrl?: string;
    providerId: string;
};

function isHttpUrl(value: unknown): boolean {
    if (typeof value !== "string" || !value.trim()) {
        return false;
    }
    try {
        return ["http:", "https:"].includes(new URL(value.trim()).protocol);
    } catch {
        return false;
    }
}

export function AddProviderDialog({ existingProviderIds, onClose, onCreated }: AddProviderDialogProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [apiKey, setApiKey] = useState("");
    const [pending, setPending] = useState(false);
    const existing = useMemo(
        () => new Set(existingProviderIds.map((provider) => provider.trim().toLowerCase())),
        [existingProviderIds],
    );

    const submit = async (values: AddProviderForm) => {
        const provider = values.providerId.trim().toLowerCase();
        if (existing.has(provider)) {
            prompt.error({ content: "该服务商 ID 已存在，请使用其他 ID。" });
            return;
        }

        const displayName = values.displayName?.trim() || provider;
        const description = values.description?.trim();
        const logoUrl = values.logoUrl?.trim();
        const config: Record<string, unknown> = {
            customProvider: true,
            displayName,
        };
        if (description) {
            config.description = description;
        }
        if (logoUrl) {
            config.logoUrl = logoUrl;
        }

        setPending(true);
        try {
            const created = await createAiChannel({
                baseUrl: values.baseUrl.trim(),
                config,
                credentials: apiKey.trim() ? { apiKey: apiKey.trim() } : undefined,
                protocol: values.format === "anthropic" ? "messages" : "chat-completions",
                provider,
                status: "active",
            });
            prompt.success({ content: `${displayName} 已创建。` });
            onCreated(created);
            onClose();
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "服务商创建失败。") });
        } finally {
            setPending(false);
        }
    };

    const close = () => {
        if (!pending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            confirmLoading={pending}
            footer={
                <Button block disabled={pending} loading={pending} onClick={() => form.submit()} theme="primary">
                    新建
                </Button>
            }
            onClose={close}
            open
            title={
                <span className="inline-flex items-center gap-2">
                    <Sparkle size="16px" />
                    创建自定义 AI 服务商
                </span>
            }
            width={620}
        >
            <Form
                form={form}
                initialData={{ format: "openai" }}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit(form.getFieldsValue(true) as AddProviderForm);
                    }
                }}
            >
                <div className="mb-3 text-sm font-semibold">基本信息</div>
                <Form.FormItem
                    label="服务商 ID"
                    name="providerId"
                    rules={[
                        { message: "请输入服务商 ID。", required: true, whitespace: true },
                        {
                            message: "只能使用小写字母、数字和短横线，且必须以字母或数字开头结尾。",
                            pattern: PROVIDER_ID_PATTERN,
                        },
                        { message: "服务商 ID 不能超过 63 个字符。", max: 63 },
                        {
                            validator: (value) => {
                                if (typeof value !== "string" || !value.trim()) {
                                    return true;
                                }

                                return existing.has(value.trim().toLowerCase())
                                    ? { message: "该服务商 ID 已存在。", result: false }
                                    : true;
                            },
                        },
                    ]}
                >
                    <Input placeholder="例如 openai、gemini 等" />
                </Form.FormItem>
                <p className="-mt-3 mb-4 text-xs text-[var(--td-text-color-placeholder)]">
                    作为服务商唯一标识，创建后将不可修改
                </p>

                <Form.FormItem label="服务商名称" name="displayName">
                    <Input placeholder="请输入服务商的展示名称" />
                </Form.FormItem>
                <Form.FormItem label="服务商简介" name="description">
                    <Input placeholder="服务商简介（选填）" />
                </Form.FormItem>
                <Form.FormItem
                    label="服务商 Logo URL"
                    name="logoUrl"
                    rules={[
                        {
                            message: "请输入有效的 HTTP 或 HTTPS 图片地址。",
                            validator: (value) => typeof value !== "string" || !value.trim() || isHttpUrl(value),
                        },
                    ]}
                >
                    <Input placeholder="https://example.com/logo.png" />
                </Form.FormItem>

                <div className="mt-2 mb-3 text-sm font-semibold">配置信息</div>
                <Form.FormItem label="请求格式" name="format" rules={[{ message: "请选择请求格式。", required: true }]}>
                    <Select options={[...REQUEST_FORMATS]} placeholder="请选择请求格式" />
                </Form.FormItem>
                <Form.FormItem
                    label="代理地址"
                    name="baseUrl"
                    rules={[
                        { message: "请输入代理地址。", required: true, whitespace: true },
                        {
                            message: "请输入有效的 HTTP 或 HTTPS 地址。",
                            validator: isHttpUrl,
                        },
                    ]}
                >
                    <Input placeholder="https://your-proxy-url.com/v1" />
                </Form.FormItem>
                <Form.FormItem label="API Key" name="apiKey">
                    <ProviderSecretInput onChange={setApiKey} placeholder="请填写你的 API Key" value={apiKey} />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
