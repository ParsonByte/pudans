"use client";

import { type ReactNode, useEffect, useMemo, useState } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { HelpCircleIcon } from "tdesign-icons-react";
import { Form, Input, InputNumber, Select, Switch, Tag, Textarea, Tooltip } from "tdesign-react";

import { GlobalDialog, ScrollArea } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useLocalStorage } from "@/hooks/utils/use-local-storage";

import { consoleErrorMessage } from "../../../../composables/_errors";
import {
    type StoragePolicy,
    type StoragePolicyType,
    createStoragePolicy,
    listStoragePolicyTypes,
    updateStoragePolicy,
    validateStoragePolicy,
} from "../../../../composables/_storage";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";
import {
    STORAGE_CAPABILITY_LABELS,
    STORAGE_POLICIES_QUERY_ROOT,
    STORAGE_POLICY_TYPES_QUERY_KEY,
    STORAGE_POLICY_TYPE_ORDER,
    isLocalStoragePolicy,
    isOneDriveStoragePolicy,
    newStoragePolicyName,
    parseStoragePolicyFileTypes,
    storagePolicyBytesToMegabytes,
    storagePolicyEndpointRequired,
    storagePolicyMegabytesToBytes,
    storagePolicyMinimumChunkMegabytes,
    storagePolicyRegionSupported,
    storagePolicyS3PathStyleSupported,
    storagePolicyTypeMeta,
} from "../composables/_storage-policies";

type StoragePolicyEditorModalProps = {
    onClose: () => void;
    policy?: StoragePolicy;
};

type StoragePolicyFormValues = {
    accessKey?: string;
    bucketName?: string;
    chunkSizeMb?: number;
    clientSecret?: string;
    defaultPolicy?: boolean;
    deleteBatchSize?: number;
    description?: string;
    dirNameRule?: string;
    displayName?: string;
    driveId?: string;
    fileNameRule?: string;
    fileTypeDenyList?: boolean;
    fileTypes?: string;
    maxSizeMb?: number;
    nameRegexp?: string;
    nameRegexpDenyList?: boolean;
    privateBucket?: boolean;
    proxyDownload?: boolean;
    region?: string;
    s3PathStyle?: boolean;
    secretKey?: string;
    server?: string;
    tenantId?: string;
    token?: string;
    type: StoragePolicyType;
};

const INITIAL_VALUES: StoragePolicyFormValues = {
    chunkSizeMb: 0,
    defaultPolicy: false,
    deleteBatchSize: 1000,
    dirNameRule: "uploads/{path}",
    displayName: "",
    fileNameRule: "{randomkey8}_{originname}",
    fileTypeDenyList: false,
    fileTypes: "",
    maxSizeMb: 0,
    nameRegexpDenyList: false,
    privateBucket: false,
    proxyDownload: true,
    s3PathStyle: false,
    tenantId: "common",
    type: "local",
};

const STORAGE_POLICY_DRAFT_KEY = "console.storage-policies.create-draft";
const REQUIRED_HINT = "必填项";
const TYPE_HINT = "存储类型决定可用的连接参数与能力，创建后不可更改。";
const MAX_SIZE_HINT = "0 表示不限制；上传超过上限的文件会被拒绝。";
const CHUNK_SIZE_HINT = "0 表示使用存储类型的默认值；自定义最小 1 MB（S3/KS3 为 5 MB），最大 5120 MB。";
const DIR_RULE_HINT =
    "支持 {randomkey8}、{uuid}、{uid}、{date} 等变量；目录与文件规则须至少包含一个唯一变量（如 {randomkey8}），避免文件覆盖。";
const FILE_RULE_HINT =
    "支持 {originname}、{ext}、{randomkey8} 等变量，不能使用 {path}；目录与文件规则须至少包含一个唯一变量，避免文件覆盖。";
const FILE_TYPES_HINT = "逗号分隔、不带点的小写扩展名；留空表示不限制。";
const NAME_REGEXP_HINT = "对原始文件名进行完整匹配；留空表示不启用。";
const PATH_STYLE_HINT = "使用 Path-Style 访问，适用于 MinIO 等兼容 S3 的服务。";
const DELETE_BATCH_HINT = "单次批量删除的文件数量，范围 1–1000。";
const DENY_FILE_TYPES_HINT = "开启后列表内的扩展名将被拒绝，而不是作为允许名单。";
const DENY_NAME_REGEXP_HINT = "开启后匹配正则的文件名将被拒绝，而不是作为允许规则。";

function FieldLabel({ children, hint, required }: { children: ReactNode; hint?: string; required?: boolean }) {
    return (
        <span className="inline-flex items-center gap-1">
            {required ? (
                <Tooltip content={REQUIRED_HINT} placement="top-left">
                    <span className="cursor-help text-[var(--td-error-color)]">*</span>
                </Tooltip>
            ) : null}
            {children}
            {hint ? (
                <Tooltip content={hint} placement="top-left">
                    <HelpCircleIcon />
                </Tooltip>
            ) : null}
        </span>
    );
}

export function StoragePolicyEditorModal({ onClose, policy }: StoragePolicyEditorModalProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const editing = Boolean(policy);
    const [draftValues, setDraftValues] = useLocalStorage<StoragePolicyFormValues>(
        STORAGE_POLICY_DRAFT_KEY,
        INITIAL_VALUES,
    );
    const initialValues = useMemo(
        () => storagePolicyFormValues(policy, policy ? undefined : draftValues),
        [draftValues, policy],
    );
    const typesQuery = useQuery({
        queryFn: listStoragePolicyTypes,
        queryKey: STORAGE_POLICY_TYPES_QUERY_KEY,
        staleTime: Number.POSITIVE_INFINITY,
    });
    const selectedType =
        (Form.useWatch("type", form) as StoragePolicyType | undefined) ?? initialValues.type ?? INITIAL_VALUES.type;
    const descriptor = typesQuery.data?.find((item) => item.type === selectedType);
    const capabilities = descriptor?.capabilities ?? [];
    const directOnly = capabilities.includes("DIRECT_UPLOAD") && !capabilities.includes("MULTIPART_UPLOAD");
    const proxyRequired = capabilities.includes("PROXY_REQUIRED");
    const local = isLocalStoragePolicy(selectedType);
    const oneDrive = isOneDriveStoragePolicy(selectedType);
    const regionSupported = storagePolicyRegionSupported(selectedType);
    const pathStyleSupported = storagePolicyS3PathStyleSupported(selectedType);
    const typeOptions = useMemo(
        () =>
            STORAGE_POLICY_TYPE_ORDER.map((type) => {
                const typeDescriptor = typesQuery.data?.find((item) => item.type === type);
                return {
                    label: typeDescriptor?.displayName ?? storagePolicyTypeMeta(type).label,
                    value: type,
                };
            }),
        [typesQuery.data],
    );

    useEffect(() => {
        if (proxyRequired) {
            form.setFieldsValue({ proxyDownload: true });
        }
    }, [form, proxyRequired]);

    const submit = async (values: StoragePolicyFormValues) => {
        const nextPolicy = storagePolicyFromValues(values, directOnly, policy);
        const validationError = validateFormValues(values, directOnly, editing);

        if (validationError) {
            prompt.error({ content: validationError });
            return;
        }

        setSubmitting(true);
        try {
            const validation = await validateStoragePolicy(nextPolicy);

            if (!validation.valid) {
                prompt.error({ content: validation.errors.join(" ") });
                return;
            }

            if (policy) {
                await updateStoragePolicy(policy.metadata.name, nextPolicy);
                prompt.success({ content: "存储策略已保存。" });
            } else {
                await createStoragePolicy(nextPolicy);
                prompt.success({ content: "存储策略已创建。" });
            }
            await queryClient.invalidateQueries({ queryKey: STORAGE_POLICIES_QUERY_ROOT });
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
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={submitting}
                    submitLabel={editing ? "保存更改" : "创建存储策略"}
                />
            }
            onClose={close}
            open
            title={editing ? "编辑存储策略" : "添加存储策略"}
            width={640}
        >
            <ScrollArea className="max-h-[68vh] pr-1">
                <Form
                    form={form}
                    initialData={initialValues}
                    labelAlign="top"
                    layout="vertical"
                    onSubmit={({ validateResult }) => {
                        const values = form.getFieldsValue(true) as StoragePolicyFormValues;

                        if (!policy) {
                            setDraftValues({ ...values });
                        }
                        if (validateResult === true) {
                            void submit(values);
                        }
                    }}
                >
                    <Form.FormItem
                        label={
                            <FieldLabel hint={TYPE_HINT} required>
                                存储类型
                            </FieldLabel>
                        }
                        name="type"
                        requiredMark={false}
                        rules={[{ message: "请选择存储类型。", required: true }]}
                    >
                        <Select
                            disabled={editing}
                            loading={typesQuery.isPending}
                            options={typeOptions}
                            valueType="value"
                        />
                    </Form.FormItem>
                    {capabilities.length > 0 ? (
                        <div className="-mt-2 mb-4 flex flex-wrap gap-1.5">
                            {capabilities.map((capability) => (
                                <Tag key={capability} theme="primary" variant="light">
                                    {STORAGE_CAPABILITY_LABELS[capability]}
                                </Tag>
                            ))}
                        </div>
                    ) : null}
                    <Form.FormItem
                        label={<FieldLabel required>显示名称</FieldLabel>}
                        name="displayName"
                        requiredMark={false}
                        rules={[
                            { message: "请输入显示名称。", required: true, whitespace: true },
                            { max: 100, message: "显示名称不能超过 100 个字符。" },
                        ]}
                    >
                        <Input maxlength={100} />
                    </Form.FormItem>
                    <Form.FormItem name="defaultPolicy">
                        <div className="flex items-center gap-2">
                            <Switch />
                            <span className="text-sm text-[var(--td-text-color-secondary)]">
                                设为默认策略，创建结果路由未指定策略时回退到该策略
                            </span>
                        </div>
                    </Form.FormItem>
                    <Form.FormItem name="proxyDownload">
                        <div className="flex items-center gap-2">
                            <Switch disabled={proxyRequired} />
                            <span className="text-sm text-[var(--td-text-color-secondary)]">
                                下载走中转代理，关闭后优先返回对象存储的预签名直链
                            </span>
                        </div>
                    </Form.FormItem>
                    {!local && !oneDrive ? (
                        <Form.FormItem name="privateBucket">
                            <div className="flex items-center gap-2">
                                <Switch />
                                <span className="text-sm text-[var(--td-text-color-secondary)]">
                                    私有 Bucket（仅七牛云生效），下载链接需要签名
                                </span>
                            </div>
                        </Form.FormItem>
                    ) : null}

                    {!local ? (
                        <>
                            <div className="grid gap-x-4 sm:grid-cols-2">
                                <Form.FormItem
                                    label={oneDrive ? "客户端 ID" : "Bucket / 容器"}
                                    name="bucketName"
                                    rules={[
                                        {
                                            message: oneDrive ? "请输入客户端 ID。" : "请输入 Bucket 名称。",
                                            required: true,
                                            whitespace: true,
                                        },
                                    ]}
                                >
                                    <Input />
                                </Form.FormItem>
                                <Form.FormItem
                                    label="EndPoint"
                                    name="server"
                                    rules={[
                                        {
                                            message: "请输入 EndPoint。",
                                            required: storagePolicyEndpointRequired(selectedType),
                                            whitespace: true,
                                        },
                                        { message: "请输入有效的 URL。", url: true },
                                    ]}
                                >
                                    <Input inputClass="font-mono" />
                                </Form.FormItem>
                            </div>
                            {regionSupported || pathStyleSupported ? (
                                <div className="grid gap-x-4 sm:grid-cols-2">
                                    {regionSupported ? (
                                        <Form.FormItem label="区域" name="region">
                                            <Input />
                                        </Form.FormItem>
                                    ) : null}
                                    {pathStyleSupported ? (
                                        <Form.FormItem
                                            label={<FieldLabel hint={PATH_STYLE_HINT}>S3 Path-Style</FieldLabel>}
                                            name="s3PathStyle"
                                        >
                                            <Switch />
                                        </Form.FormItem>
                                    ) : null}
                                </div>
                            ) : null}
                            {oneDrive ? (
                                <>
                                    <div className="grid gap-x-4 sm:grid-cols-2">
                                        <Form.FormItem
                                            label="客户端密钥"
                                            name="clientSecret"
                                            rules={
                                                editing
                                                    ? []
                                                    : [
                                                          {
                                                              message: "请输入客户端密钥。",
                                                              required: true,
                                                              whitespace: true,
                                                          },
                                                      ]
                                            }
                                        >
                                            <Input
                                                autocomplete="new-password"
                                                placeholder={editing ? "留空保持不变" : undefined}
                                                type="password"
                                            />
                                        </Form.FormItem>
                                        <Form.FormItem
                                            label="刷新令牌"
                                            name="token"
                                            rules={
                                                editing
                                                    ? []
                                                    : [
                                                          {
                                                              message: "请输入刷新令牌。",
                                                              required: true,
                                                              whitespace: true,
                                                          },
                                                      ]
                                            }
                                        >
                                            <Input
                                                autocomplete="new-password"
                                                placeholder={editing ? "留空保持不变" : undefined}
                                                type="password"
                                            />
                                        </Form.FormItem>
                                    </div>
                                    <div className="grid gap-x-4 sm:grid-cols-2">
                                        <Form.FormItem label="租户 ID" name="tenantId">
                                            <Input />
                                        </Form.FormItem>
                                        <Form.FormItem label="驱动器 ID" name="driveId">
                                            <Input />
                                        </Form.FormItem>
                                    </div>
                                </>
                            ) : (
                                <div className="grid gap-x-4 sm:grid-cols-2">
                                    <Form.FormItem
                                        label={selectedType === "upyun" ? "操作员" : "Access Key"}
                                        name="accessKey"
                                        rules={
                                            editing
                                                ? []
                                                : [
                                                      {
                                                          message:
                                                              selectedType === "upyun"
                                                                  ? "请输入操作员。"
                                                                  : "请输入 Access Key。",
                                                          required: true,
                                                          whitespace: true,
                                                      },
                                                  ]
                                        }
                                    >
                                        <Input autocomplete="off" placeholder={editing ? "留空保持不变" : undefined} />
                                    </Form.FormItem>
                                    <Form.FormItem
                                        label={selectedType === "upyun" ? "操作员密码" : "Secret Key"}
                                        name="secretKey"
                                        rules={
                                            editing
                                                ? []
                                                : [
                                                      {
                                                          message:
                                                              selectedType === "upyun"
                                                                  ? "请输入操作员密码。"
                                                                  : "请输入 Secret Key。",
                                                          required: true,
                                                          whitespace: true,
                                                      },
                                                  ]
                                        }
                                    >
                                        <Input
                                            autocomplete="new-password"
                                            placeholder={editing ? "留空保持不变" : undefined}
                                            type="password"
                                        />
                                    </Form.FormItem>
                                </div>
                            )}
                        </>
                    ) : null}

                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem
                            label={<FieldLabel hint={MAX_SIZE_HINT}>文件大小上限</FieldLabel>}
                            name="maxSizeMb"
                        >
                            <InputNumber decimalPlaces={1} min={0} style={{ width: "100%" }} suffix="MB" />
                        </Form.FormItem>
                        {directOnly ? null : (
                            <Form.FormItem
                                label={<FieldLabel hint={CHUNK_SIZE_HINT}>分片大小</FieldLabel>}
                                name="chunkSizeMb"
                            >
                                <InputNumber decimalPlaces={1} min={0} style={{ width: "100%" }} suffix="MB" />
                            </Form.FormItem>
                        )}
                    </div>
                    <Form.FormItem
                        label={
                            <FieldLabel hint={DIR_RULE_HINT} required>
                                存储目录规则
                            </FieldLabel>
                        }
                        name="dirNameRule"
                        requiredMark={false}
                        rules={[
                            { message: "请输入目录规则。", required: true, whitespace: true },
                            {
                                message: "目录和文件规则不能包含上级目录。",
                                validator: rejectParentDirectory,
                            },
                        ]}
                    >
                        <Input inputClass="font-mono" />
                    </Form.FormItem>
                    <Form.FormItem
                        label={
                            <FieldLabel hint={FILE_RULE_HINT} required>
                                文件名称规则
                            </FieldLabel>
                        }
                        name="fileNameRule"
                        requiredMark={false}
                        rules={[
                            { message: "请输入文件名称规则。", required: true, whitespace: true },
                            {
                                message: "目录和文件规则不能包含上级目录。",
                                validator: rejectParentDirectory,
                            },
                        ]}
                    >
                        <Input inputClass="font-mono" />
                    </Form.FormItem>
                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem
                            label={<FieldLabel hint={FILE_TYPES_HINT}>文件扩展名</FieldLabel>}
                            name="fileTypes"
                        >
                            <Input placeholder="例如 jpg,png" />
                        </Form.FormItem>
                        <Form.FormItem
                            label={<FieldLabel hint={DENY_FILE_TYPES_HINT}>扩展名拒绝名单</FieldLabel>}
                            name="fileTypeDenyList"
                        >
                            <Switch />
                        </Form.FormItem>
                    </div>
                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem
                            label={<FieldLabel hint={NAME_REGEXP_HINT}>文件名正则</FieldLabel>}
                            name="nameRegexp"
                            rules={[{ message: "文件名正则无效。", validator: validateRegularExpression }]}
                        >
                            <Input inputClass="font-mono" />
                        </Form.FormItem>
                        <Form.FormItem
                            label={<FieldLabel hint={DENY_NAME_REGEXP_HINT}>文件名拒绝名单</FieldLabel>}
                            name="nameRegexpDenyList"
                        >
                            <Switch />
                        </Form.FormItem>
                    </div>
                    {!local ? (
                        <div className="grid gap-x-4 sm:grid-cols-2">
                            <Form.FormItem
                                label={<FieldLabel hint={DELETE_BATCH_HINT}>批量删除数量</FieldLabel>}
                                name="deleteBatchSize"
                                rules={[
                                    { max: 1000, message: "批量删除数量不能超过 1000。", number: true },
                                    { min: 1, message: "批量删除数量不能小于 1。", number: true },
                                ]}
                            >
                                <InputNumber max={1000} min={1} style={{ width: "100%" }} suffix="条" />
                            </Form.FormItem>
                        </div>
                    ) : null}
                    <Form.FormItem label="描述" name="description">
                        <Textarea
                            autosize={{ maxRows: 4, minRows: 2 }}
                            maxlength={500}
                            placeholder="可选，说明存储策略的用途或限制"
                        />
                    </Form.FormItem>
                </Form>
            </ScrollArea>
        </GlobalDialog>
    );
}

function storagePolicyFormValues(
    policy?: StoragePolicy,
    remembered?: StoragePolicyFormValues,
): StoragePolicyFormValues {
    if (!policy) {
        return remembered ? { ...INITIAL_VALUES, ...remembered } : INITIAL_VALUES;
    }

    return {
        ...INITIAL_VALUES,
        bucketName: policy.spec.bucketName,
        chunkSizeMb: storagePolicyBytesToMegabytes(policy.spec.chunkSize),
        defaultPolicy: Boolean(policy.spec.defaultPolicy),
        deleteBatchSize: policy.spec.settings?.deleteBatchSize ?? INITIAL_VALUES.deleteBatchSize,
        description: policy.spec.description,
        dirNameRule: policy.spec.dirNameRule || INITIAL_VALUES.dirNameRule,
        displayName: policy.spec.displayName,
        driveId: policy.spec.settings?.driveId,
        fileNameRule: policy.spec.fileNameRule || INITIAL_VALUES.fileNameRule,
        fileTypeDenyList: Boolean(policy.spec.fileTypeDenyList),
        fileTypes: (policy.spec.fileTypes ?? []).join(", "),
        maxSizeMb: storagePolicyBytesToMegabytes(policy.spec.maxSize),
        nameRegexp: policy.spec.nameRegexp,
        nameRegexpDenyList: Boolean(policy.spec.nameRegexpDenyList),
        privateBucket: Boolean(policy.spec.privateBucket),
        proxyDownload: policy.spec.proxyDownload !== false,
        region: policy.spec.settings?.region,
        s3PathStyle: Boolean(policy.spec.settings?.s3PathStyle),
        tenantId: policy.spec.settings?.tenantId || "common",
        type: policy.spec.type ?? "local",
    };
}

function storagePolicyFromValues(
    values: StoragePolicyFormValues,
    directOnly: boolean,
    base?: StoragePolicy,
): StoragePolicy {
    const type = values.type;
    const local = isLocalStoragePolicy(type);
    const oneDrive = isOneDriveStoragePolicy(type);
    const settings: NonNullable<StoragePolicy["spec"]["settings"]> = {
        deleteBatchSize: values.deleteBatchSize ?? 1000,
    };

    if (storagePolicyRegionSupported(type) && values.region?.trim()) {
        settings.region = values.region.trim();
    }
    if (storagePolicyS3PathStyleSupported(type)) {
        settings.s3PathStyle = Boolean(values.s3PathStyle);
    }
    if (oneDrive) {
        settings.tenantId = values.tenantId?.trim() || "common";
        if (values.driveId?.trim()) {
            settings.driveId = values.driveId.trim();
        }
    }

    const credentials = local
        ? {}
        : oneDrive
          ? {
                ...base?.spec.credentials,
                clientSecret: preferredCredential(values.clientSecret, base?.spec.credentials?.clientSecret),
                token: preferredCredential(values.token, base?.spec.credentials?.token),
            }
          : {
                ...base?.spec.credentials,
                accessKey: preferredCredential(values.accessKey, base?.spec.credentials?.accessKey),
                secretKey: preferredCredential(values.secretKey, base?.spec.credentials?.secretKey),
            };

    return {
        apiVersion: base?.apiVersion ?? "pudans.tolim.cn/v1alpha1",
        kind: base?.kind ?? "StoragePolicy",
        metadata: base ? { ...base.metadata } : { name: newStoragePolicyName() },
        ...(base?.status ? { status: base.status } : {}),
        spec: {
            bucketName: local ? undefined : values.bucketName?.trim(),
            chunkSize: directOnly ? 0 : storagePolicyMegabytesToBytes(values.chunkSizeMb),
            credentials,
            defaultPolicy: Boolean(values.defaultPolicy),
            description: values.description?.trim() || undefined,
            dirNameRule: values.dirNameRule?.trim(),
            displayName: values.displayName?.trim(),
            fileNameRule: values.fileNameRule?.trim(),
            fileTypeDenyList: Boolean(values.fileTypeDenyList),
            fileTypes: parseStoragePolicyFileTypes(values.fileTypes),
            maxSize: storagePolicyMegabytesToBytes(values.maxSizeMb),
            nameRegexp: values.nameRegexp?.trim() || undefined,
            nameRegexpDenyList: Boolean(values.nameRegexpDenyList),
            privateBucket: !local && !oneDrive && Boolean(values.privateBucket),
            proxyDownload: values.proxyDownload !== false,
            server: local ? undefined : values.server?.trim() || undefined,
            settings,
            type,
        },
    };
}

function preferredCredential(value?: string, fallback?: string): string | undefined {
    return value?.trim() || fallback;
}

function validateFormValues(
    values: StoragePolicyFormValues,
    directOnly: boolean,
    editing: boolean,
): string | undefined {
    if (!isLocalStoragePolicy(values.type)) {
        if (!values.bucketName?.trim()) {
            return values.type === "onedrive" ? "请输入客户端 ID。" : "请输入 Bucket 名称。";
        }
        if (storagePolicyEndpointRequired(values.type) && !values.server?.trim()) {
            return "请输入 EndPoint。";
        }
        if (!editing) {
            if (isOneDriveStoragePolicy(values.type)) {
                if (!values.clientSecret?.trim() || !values.token?.trim()) {
                    return "请输入 OneDrive 客户端密钥和刷新令牌。";
                }
            } else if (!values.accessKey?.trim() || !values.secretKey?.trim()) {
                return values.type === "upyun" ? "请输入又拍云操作员和密码。" : "请输入 Access Key 和 Secret Key。";
            }
        }
    }
    if (!directOnly && values.chunkSizeMb && values.chunkSizeMb > 0) {
        const minimum = storagePolicyMinimumChunkMegabytes(values.type);
        if (values.chunkSizeMb < minimum) {
            return `分片大小不能小于 ${minimum} MB。`;
        }
    }
    return undefined;
}

function rejectParentDirectory(value: unknown) {
    return {
        message: "目录和文件规则不能包含上级目录。",
        result: !String(value ?? "").includes(".."),
    };
}

function validateRegularExpression(value: unknown) {
    const expression = String(value ?? "").trim();

    if (!expression) {
        return { message: "", result: true };
    }

    try {
        new RegExp(expression);
        return { message: "", result: true };
    } catch {
        return { message: "文件名正则无效。", result: false };
    }
}
