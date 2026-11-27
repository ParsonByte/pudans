"use client";

import { useState } from "react";

import { HelpCircleIcon, LockOnIcon } from "tdesign-icons-react";
import { Button, Input, Select, Switch, Tag, Tooltip } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import { ProviderMark } from "../../components/provider-mark";
import type { AiChannel, AiChannelTestResult, AiProviderDescriptor } from "../../composables/_dans";
import { ProviderSecretInput } from "./provider-secret-input";

export type ProviderDraft = {
    apiKey?: string;
    baseUrl: string;
    enabled: boolean;
};

type ModelOption = {
    label: string;
    value: string;
};

type ProviderDetailProps = {
    channel?: AiChannel;
    descriptor: AiProviderDescriptor;
    modelOptions: ModelOption[];
    onPersist: (draft: ProviderDraft) => Promise<AiChannel>;
    onTest: (channel: AiChannel, model?: string) => Promise<AiChannelTestResult>;
};

function isEnabled(channel?: AiChannel): boolean {
    return channel?.status === "active";
}

export function ProviderDetail({ channel, descriptor, modelOptions, onPersist, onTest }: ProviderDetailProps) {
    const prompt = useMessagePrompt();
    const [apiKey, setApiKey] = useState("");
    const [baseUrl, setBaseUrl] = useState(channel?.baseUrl ?? descriptor.defaultBaseUrl ?? "");
    const [enabled, setEnabled] = useState(channel ? isEnabled(channel) : true);
    const [pending, setPending] = useState(false);
    const [testing, setTesting] = useState(false);
    const [testModel, setTestModel] = useState<string>();
    const [testResult, setTestResult] = useState<AiChannelTestResult>();
    const [savedAt, setSavedAt] = useState<string>();

    const persist = async (nextEnabled = enabled): Promise<AiChannel> => {
        const resolvedBaseUrl = baseUrl.trim();
        if (!resolvedBaseUrl) {
            throw new Error("请输入 API 代理地址。");
        }
        if (!channel && !apiKey.trim()) {
            throw new Error("请先填写 API Key。");
        }

        setPending(true);
        try {
            const saved = await onPersist({
                apiKey: apiKey.trim() || undefined,
                baseUrl: resolvedBaseUrl,
                enabled: nextEnabled,
            });
            setApiKey("");
            setSavedAt(new Date().toLocaleTimeString("zh-CN", { hour12: false }));
            return saved;
        } finally {
            setPending(false);
        }
    };

    const saveSilently = async (nextEnabled = enabled) => {
        try {
            await persist(nextEnabled);
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "渠道配置保存失败。") });
        }
    };

    const toggleEnabled = async (value: boolean | string) => {
        if (typeof value !== "boolean" || value === enabled) {
            return;
        }

        setEnabled(value);
        try {
            await persist(value);
        } catch (error) {
            setEnabled(!value);
            prompt.error({ content: consoleErrorMessage(error, "渠道状态更新失败。") });
        }
    };

    const test = async () => {
        setTesting(true);
        try {
            const saved = await persist();
            const result = await onTest(saved, testModel);
            setTestResult(result);
            if (result.success) {
                prompt.success({ content: "渠道连通性检查通过。" });
            } else {
                prompt.error({ content: result.message ?? "渠道连通性检查失败。" });
            }
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "渠道连通性检查失败。") });
        } finally {
            setTesting(false);
        }
    };

    return (
        <section className="flex min-w-0 flex-col gap-6 p-4 lg:p-6">
            <header className="flex flex-wrap items-center gap-3">
                <ProviderMark logoUrl={descriptor.logoUrl} provider={descriptor.provider} size="large" />
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                        <h2 className="m-0 truncate text-xl font-semibold">{descriptor.displayName}</h2>
                        {descriptor.documentationUrl ? (
                            <Tooltip content="打开服务商文档">
                                <a
                                    aria-label={`${descriptor.displayName} 文档`}
                                    className="inline-flex text-[var(--td-text-color-placeholder)] transition-colors hover:text-[var(--td-brand-color)]"
                                    href={descriptor.documentationUrl}
                                    rel="noreferrer"
                                    target="_blank"
                                >
                                    <HelpCircleIcon size="18px" />
                                </a>
                            </Tooltip>
                        ) : null}
                    </div>
                    {descriptor.description ? (
                        <p className="m-0 mt-1 line-clamp-1 text-xs text-[var(--td-text-color-placeholder)]">
                            {descriptor.description}
                        </p>
                    ) : null}
                </div>
                {savedAt ? (
                    <span className="text-xs text-[var(--td-text-color-placeholder)]">已保存 {savedAt}</span>
                ) : null}
                <Switch
                    aria-label="启用渠道"
                    loading={pending}
                    onChange={(value) => void toggleEnabled(value)}
                    value={enabled}
                />
            </header>

            <div className="grid items-start gap-4 lg:grid-cols-[minmax(220px,320px)_minmax(0,1fr)]">
                <div>
                    <div className="text-sm font-medium">API Key</div>
                    <p className="m-0 mt-1 text-xs leading-5 text-[var(--td-text-color-placeholder)]">
                        请填写你的 {descriptor.displayName} API Key
                        {channel?.credentialConfigured ? "；留空会保留已保存的凭据。" : "。"}
                    </p>
                </div>
                <ProviderSecretInput
                    onBlur={() => {
                        if (apiKey.trim()) {
                            void saveSilently();
                        }
                    }}
                    onChange={setApiKey}
                    placeholder={
                        channel?.credentialConfigured ? "已配置，输入新值以替换" : `${descriptor.displayName} API Key`
                    }
                    value={apiKey}
                />

                <div>
                    <div className="text-sm font-medium">API 代理地址</div>
                    <p className="m-0 mt-1 text-xs leading-5 text-[var(--td-text-color-placeholder)]">
                        必须包含 http(s)://
                    </p>
                </div>
                <Input
                    onBlur={() => {
                        if (
                            baseUrl.trim() &&
                            (channel || apiKey.trim()) &&
                            baseUrl.trim() !== (channel?.baseUrl ?? "")
                        ) {
                            void saveSilently();
                        }
                    }}
                    onChange={setBaseUrl}
                    placeholder={descriptor.defaultBaseUrl ?? "https://api.example.com"}
                    value={baseUrl}
                />

                <div>
                    <div className="text-sm font-medium">连通性检查</div>
                    <p className="m-0 mt-1 text-xs leading-5 text-[var(--td-text-color-placeholder)]">
                        测试 API Key 与代理地址是否正确填写
                    </p>
                </div>
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <Select
                        className="min-w-0 flex-1"
                        clearable
                        onChange={(value) => setTestModel(value ? String(value) : undefined)}
                        options={modelOptions}
                        placeholder="自动检查模型目录"
                        value={testModel}
                        valueType="value"
                    />
                    <Button loading={testing || pending} onClick={() => void test()} variant="outline">
                        检查
                    </Button>
                    {testResult ? (
                        <Tag theme={testResult.success ? "success" : "danger"} variant="light">
                            {testResult.success ? "可用" : "失败"}
                        </Tag>
                    ) : null}
                </div>
            </div>

            <div className="flex items-start justify-end gap-2 text-xs text-[var(--td-text-color-placeholder)]">
                <LockOnIcon className="mt-0.5 shrink-0" />
                <span>你的密钥与代理地址将使用 AES-GCM 加密算法进行加密</span>
            </div>
        </section>
    );
}
