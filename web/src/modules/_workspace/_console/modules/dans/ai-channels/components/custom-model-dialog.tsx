"use client";

import { useMemo, useState } from "react";

import { Form, Input, InputNumber, Select, Slider, Switch } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { IdentityDialogFooter } from "@/modules/_workspace/_console/modules/identity/components/identity-dialog-footer";

import {
    type AiChannelModel,
    type AiModel,
    type AiProviderDescriptor,
    createAiChannelModel,
    createAiModel,
    deleteAiModel,
    updateAiChannelModel,
    updateAiModel,
} from "../../composables/_dans";
import {
    MODEL_FEATURE_LABELS,
    MODEL_TYPE_OPTIONS,
    formatTokenLimit,
    modelFeatures,
    modelParameters,
    modelTypeOf,
} from "../../composables/_model-meta";

const CONTEXT_MAX = 2_147_483_647;
const CONTEXT_STEP = 1_024;
const CONTEXT_PRESETS = [
    0,
    8_192,
    16_384,
    32_768,
    65_536,
    131_072,
    262_144,
    524_288,
    1_048_576,
    2_097_152,
    4_194_304,
    8_388_608,
    16_777_216,
    33_554_432,
    67_108_864,
    134_217_728,
    268_435_456,
    536_870_912,
    1_073_741_824,
    CONTEXT_MAX,
] as const;

const EXTENSION_OPTIONS = [
    { label: "temperature", value: "temperature" },
    { label: "top_p", value: "top_p" },
    { label: "max_tokens", value: "max_tokens" },
    { label: "stop", value: "stop" },
    { label: "response_format", value: "response_format" },
    { label: "tools", value: "tools" },
    { label: "thinking", value: "thinking" },
    { label: "reasoning_effort", value: "reasoning_effort" },
];

const FEATURES = [
    {
        code: "tools",
        description: "仅开启模型使用工具的能力；是否可用取决于模型本身。",
    },
    {
        code: "vision",
        description: "仅开启图片上传与视觉识别能力；实际识别效果取决于模型本身。",
    },
    {
        code: "reasoning",
        description: "仅开启深度思考能力；具体效果取决于模型本身。",
    },
    {
        code: "web-search",
        description: "仅开启内置联网搜索能力；是否支持取决于模型本身。",
    },
    {
        code: "image",
        description: "仅开启图片生成能力；实际生成质量取决于模型本身。",
    },
    {
        code: "video",
        description: "仅开启视频识别配置；实际识别能力取决于模型本身。",
    },
] as const;

type CustomModelDialogProps = {
    channelId: string;
    model?: AiModel;
    onClose: () => void;
    onSaved: () => void;
    provider: AiProviderDescriptor;
    route?: AiChannelModel;
};

type CustomModelForm = {
    contextWindow?: number;
    displayName?: string;
    extensions?: string[];
    modelId: string;
    modelType?: string;
};

function contextSliderIndex(value: number): number {
    if (value <= 0) {
        return 0;
    }

    let closest = 0;
    for (let index = 1; index < CONTEXT_PRESETS.length; index += 1) {
        if (Math.abs(CONTEXT_PRESETS[index] - value) < Math.abs(CONTEXT_PRESETS[closest] - value)) {
            closest = index;
        }
    }
    return closest;
}

function contextPresetAt(index: number): number {
    return CONTEXT_PRESETS[index] ?? 0;
}

export function CustomModelDialog({ channelId, model, onClose, onSaved, provider, route }: CustomModelDialogProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [contextWindow, setContextWindow] = useState(model?.contextWindow ?? 0);
    const [features, setFeatures] = useState<string[]>(() => modelFeatures(model));
    const [pending, setPending] = useState(false);
    const editing = Boolean(model && route);
    const currentModelType = modelTypeOf(model);
    const modelTypeOptions = useMemo(() => {
        const supported = new Set((provider.modelTypes ?? []).filter(Boolean));
        return supported.size > 0
            ? MODEL_TYPE_OPTIONS.filter((option) => supported.has(option.value))
            : MODEL_TYPE_OPTIONS;
    }, [provider.modelTypes]);
    const defaultModelType = modelTypeOptions.some((option) => option.value === currentModelType)
        ? currentModelType
        : (modelTypeOptions[0]?.value ?? "chat");
    const featureOptions = useMemo(() => {
        if (!provider.capabilities) {
            return FEATURES;
        }
        const available = new Set(
            provider.capabilities
                .filter((capability) => capability.status === "available")
                .map((capability) => capability.code),
        );
        return FEATURES.filter((feature) => available.has(feature.code) || features.includes(feature.code));
    }, [features, provider.capabilities]);

    const toggleFeature = (code: string) => {
        setFeatures((current) =>
            current.includes(code) ? current.filter((item) => item !== code) : [...current, code],
        );
    };

    const submit = async (values: CustomModelForm) => {
        const upstreamModelKey = values.modelId.trim();
        const logicalName =
            editing && model?.logicalName
                ? model.logicalName
                : upstreamModelKey.startsWith(`${provider.provider}/`)
                  ? upstreamModelKey
                  : `${provider.provider}/${upstreamModelKey}`;
        if (logicalName.length > 128) {
            prompt.error({ content: "模型逻辑名称不能超过 128 个字符。" });
            return;
        }

        const modelType = values.modelType || defaultModelType;
        if (!modelTypeOptions.some((option) => option.value === modelType)) {
            prompt.error({ content: "当前服务商不支持所选的模型类型。" });
            return;
        }
        const parameters = values.extensions ?? [];
        const currentCapabilities = Object.fromEntries(
            Object.entries(model?.capabilities ?? {}).filter(
                ([key]) => !MODEL_TYPE_OPTIONS.some((option) => option.value === key),
            ),
        );
        const capabilities = {
            ...currentCapabilities,
            [modelType]: {
                features,
                parameters,
                source: "manual",
                type: modelType,
            },
        };
        const update = {
            capabilities,
            contextWindow: contextWindow > 0 ? contextWindow : undefined,
            displayName: values.displayName?.trim() || upstreamModelKey,
            parameterSchema: { parameters },
        };
        setPending(true);
        let createdModel: AiModel | undefined;
        try {
            if (editing && model && route) {
                await updateAiModel(model.id, update);
                if (route.upstreamModelKey !== upstreamModelKey) {
                    await updateAiChannelModel(route.id, { upstreamModelKey });
                }
                prompt.success({ content: "模型已更新。" });
            } else {
                createdModel = await createAiModel({
                    ...update,
                    logicalName,
                    status: "active",
                });
                await createAiChannelModel({
                    channelId,
                    enabled: true,
                    modelId: createdModel.id,
                    providerMetadata: { source: "manual" },
                    upstreamModelKey,
                });
                prompt.success({ content: "自定义模型已创建。" });
            }
            onSaved();
            onClose();
        } catch (error) {
            if (createdModel) {
                await deleteAiModel(createdModel.id).catch(() => undefined);
            }
            prompt.error({
                content: consoleErrorMessage(error, editing ? "模型更新失败。" : "自定义模型创建失败。"),
            });
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
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={pending}
                    submitLabel="确认"
                />
            }
            onClose={close}
            open
            title={editing ? "修改自定义 AI 模型" : "创建自定义 AI 模型"}
            width={720}
        >
            <Form
                form={form}
                initialData={{
                    displayName: model?.displayName ?? "",
                    extensions: modelParameters(model),
                    modelId: route?.upstreamModelKey ?? "",
                    modelType: defaultModelType,
                }}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit(form.getFieldsValue(true) as CustomModelForm);
                    }
                }}
            >
                <Form.FormItem
                    label="模型 ID"
                    name="modelId"
                    rules={[
                        { message: "请输入模型 ID。", required: true, whitespace: true },
                        { message: "模型 ID 不能超过 128 个字符。", max: 128 },
                    ]}
                >
                    <Input placeholder="请输入上游模型 ID，例如 deepseek-custom" />
                </Form.FormItem>
                <Form.FormItem label="模型展示名称" name="displayName">
                    <Input placeholder="用于模型列表的展示名称" />
                </Form.FormItem>
                <section className="mt-1">
                    <div className="mb-2 text-sm font-medium">最大上下文窗口</div>
                    <div className="grid items-center gap-4 md:grid-cols-[minmax(0,1fr)_240px]">
                        <div className="min-w-0 px-3">
                            <Slider
                                aria-label="最大上下文窗口"
                                label={false}
                                max={CONTEXT_PRESETS.length - 1}
                                min={0}
                                onChange={(value) =>
                                    setContextWindow(contextPresetAt(Array.isArray(value) ? value[0] : value))
                                }
                                step={1}
                                value={contextSliderIndex(contextWindow)}
                            />
                        </div>
                        <InputNumber
                            decimalPlaces={0}
                            max={CONTEXT_MAX}
                            min={0}
                            onChange={(value) => setContextWindow(Number(value) || 0)}
                            placeholder="请输入 Token 数量"
                            step={CONTEXT_STEP}
                            style={{ width: "100%" }}
                            suffix="tokens"
                            value={contextWindow}
                        />
                    </div>
                    <p className="mt-1 mb-0 text-xs text-[var(--td-text-color-placeholder)]">
                        拖动滑块快速选择，右侧可精确填写；当前 {formatTokenLimit(contextWindow)}，0 表示不声明。
                    </p>
                </section>
                <Form.FormItem label="扩展参数" name="extensions">
                    <Select
                        filterable
                        multiple
                        options={EXTENSION_OPTIONS}
                        placeholder="选择要启用的扩展参数"
                        valueType="value"
                    />
                </Form.FormItem>
                <p className="-mt-2 mb-4 text-xs text-[var(--td-text-color-placeholder)]">
                    仅记录模型支持的参数，配置错误可能导致请求失败。
                </p>

                <div className="flex flex-col gap-5">
                    {featureOptions.map((feature) => (
                        <div className="flex items-start justify-between gap-6" key={feature.code}>
                            <div className="min-w-0">
                                <div className="text-sm font-medium">{MODEL_FEATURE_LABELS[feature.code]}</div>
                                <p className="mt-1 mb-0 text-xs leading-5 text-[var(--td-text-color-placeholder)]">
                                    {feature.description}
                                </p>
                            </div>
                            <Switch
                                aria-label={MODEL_FEATURE_LABELS[feature.code]}
                                onChange={(value) => {
                                    if (typeof value === "boolean" && value !== features.includes(feature.code)) {
                                        toggleFeature(feature.code);
                                    }
                                }}
                                size="small"
                                value={features.includes(feature.code)}
                            />
                        </div>
                    ))}
                </div>

                <Form.FormItem
                    className="mt-5"
                    label="模型类型"
                    name="modelType"
                    rules={[{ message: "请选择模型类型。", required: true }]}
                >
                    <Select options={modelTypeOptions} placeholder="请选择模型类型" valueType="value" />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
