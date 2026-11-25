"use client";

import { useMemo, useState } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Divider, Form, Input, InputNumber, Select, Switch, Textarea } from "tdesign-react";

import { GlobalDialog, ScrollArea } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import {
    type StoragePolicy,
    type StoragePolicyType,
    createStoragePolicy,
    listStoragePolicyTypes,
    validateStoragePolicy,
} from "../../../../composables/_storage";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";
import {
    STORAGE_POLICIES_QUERY_ROOT,
    STORAGE_POLICY_TYPES_QUERY_KEY,
    STORAGE_POLICY_TYPE_ORDER,
    isLocalStoragePolicy,
    isOneDriveStoragePolicy,
    newStoragePolicyName,
    parseStoragePolicyFileTypes,
    storagePolicyEndpointRequired,
    storagePolicyMegabytesToBytes,
    storagePolicyMinimumChunkMegabytes,
    storagePolicyRegionSupported,
    storagePolicyS3PathStyleSupported,
    storagePolicyTypeMeta,
} from "../composables/_storage-policies";

type StoragePolicyEditorModalProps = {
    onClose: () => void;
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

export function StoragePolicyEditorModal({ onClose }: StoragePolicyEditorModalProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const typesQuery = useQuery({
        queryFn: listStoragePolicyTypes,
        queryKey: STORAGE_POLICY_TYPES_QUERY_KEY,
        staleTime: Number.POSITIVE_INFINITY,
    });
    const selectedType = (Form.useWatch("type", form) as StoragePolicyType | undefined) ?? INITIAL_VALUES.type;
    const local = isLocalStoragePolicy(selectedType);
    const oneDrive = isOneDriveStoragePolicy(selectedType);
    const regionSupported = storagePolicyRegionSupported(selectedType);
    const pathStyleSupported = storagePolicyS3PathStyleSupported(selectedType);
    const typeOptions = useMemo(
        () =>
            STORAGE_POLICY_TYPE_ORDER.map((type) => {
                const descriptor = typesQuery.data?.find((item) => item.type === type);
                return {
                    label: descriptor?.displayName ?? storagePolicyTypeMeta(type).label,
                    value: type,
                };
            }),
        [typesQuery.data],
    );

    const submit = async (values: StoragePolicyFormValues) => {
        const policy = storagePolicyFromValues(values);
        const validationError = validateFormValues(values);

        if (validationError) {
            prompt.error({ content: validationError });
            return;
        }

        setSubmitting(true);
        try {
            const validation = await validateStoragePolicy(policy);

            if (!validation.valid) {
                prompt.error({ content: validation.errors.join(" ") });
                return;
            }

            await createStoragePolicy(policy);
            await queryClient.invalidateQueries({ queryKey: STORAGE_POLICIES_QUERY_ROOT });
            prompt.success({ content: "存储策略已创建。" });
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
                    submitLabel="创建存储策略"
                />
            }
            onClose={close}
            open
            title="添加存储策略"
            width={780}
        >
            <ScrollArea className="max-h-[68vh] pr-1">
                <Form
                    form={form}
                    initialData={INITIAL_VALUES}
                    labelAlign="top"
                    layout="vertical"
                    onSubmit={({ validateResult }) => {
                        if (validateResult === true) {
                            void submit(form.getFieldsValue(true) as StoragePolicyFormValues);
                        }
                    }}
                >
                    <Form.FormItem
                        label="存储类型"
                        name="type"
                        rules={[{ message: "请选择存储类型。", required: true }]}
                    >
                        <Select loading={typesQuery.isPending} options={typeOptions} valueType="value" />
                    </Form.FormItem>
                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem
                            label="显示名称"
                            name="displayName"
                            rules={[
                                { message: "请输入显示名称。", required: true, whitespace: true },
                                { max: 100, message: "显示名称不能超过 100 个字符。" },
                            ]}
                        >
                            <Input maxlength={100} />
                        </Form.FormItem>
                    </div>
                    <Form.FormItem label="描述" name="description">
                        <Textarea autosize={{ maxRows: 4, minRows: 2 }} maxlength={500} />
                    </Form.FormItem>
                    <div className="grid gap-x-4 sm:grid-cols-3">
                        <Form.FormItem label="默认策略" name="defaultPolicy">
                            <Switch />
                        </Form.FormItem>
                        <Form.FormItem label="下载中转" name="proxyDownload">
                            <Switch />
                        </Form.FormItem>
                        {!local && !oneDrive ? (
                            <Form.FormItem label="私有 Bucket" name="privateBucket">
                                <Switch />
                            </Form.FormItem>
                        ) : null}
                    </div>

                    {!local ? (
                        <>
                            <Divider align="left">连接与凭据</Divider>
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
                                        <Form.FormItem label="S3 Path-Style" name="s3PathStyle">
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
                                            rules={[
                                                {
                                                    message: "请输入客户端密钥。",
                                                    required: true,
                                                    whitespace: true,
                                                },
                                            ]}
                                        >
                                            <Input autocomplete="new-password" type="password" />
                                        </Form.FormItem>
                                        <Form.FormItem
                                            label="刷新令牌"
                                            name="token"
                                            rules={[{ message: "请输入刷新令牌。", required: true, whitespace: true }]}
                                        >
                                            <Input autocomplete="new-password" type="password" />
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
                                        rules={[
                                            {
                                                message:
                                                    selectedType === "upyun" ? "请输入操作员。" : "请输入 Access Key。",
                                                required: true,
                                                whitespace: true,
                                            },
                                        ]}
                                    >
                                        <Input autocomplete="off" />
                                    </Form.FormItem>
                                    <Form.FormItem
                                        label={selectedType === "upyun" ? "操作员密码" : "Secret Key"}
                                        name="secretKey"
                                        rules={[
                                            {
                                                message:
                                                    selectedType === "upyun"
                                                        ? "请输入操作员密码。"
                                                        : "请输入 Secret Key。",
                                                required: true,
                                                whitespace: true,
                                            },
                                        ]}
                                    >
                                        <Input autocomplete="new-password" type="password" />
                                    </Form.FormItem>
                                </div>
                            )}
                        </>
                    ) : null}

                    <Divider align="left">上传与目录</Divider>
                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem label="文件大小上限（MB）" name="maxSizeMb">
                            <InputNumber decimalPlaces={1} min={0} style={{ width: "100%" }} />
                        </Form.FormItem>
                        <Form.FormItem label="分片大小（MB）" name="chunkSizeMb">
                            <InputNumber decimalPlaces={1} min={0} style={{ width: "100%" }} />
                        </Form.FormItem>
                    </div>
                    <Form.FormItem
                        label="存储目录规则"
                        name="dirNameRule"
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
                        label="文件名称规则"
                        name="fileNameRule"
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
                        <Form.FormItem label="文件扩展名" name="fileTypes">
                            <Input />
                        </Form.FormItem>
                        <Form.FormItem label="扩展名拒绝名单" name="fileTypeDenyList">
                            <Switch />
                        </Form.FormItem>
                    </div>
                    <div className="grid gap-x-4 sm:grid-cols-2">
                        <Form.FormItem
                            label="文件名正则"
                            name="nameRegexp"
                            rules={[{ message: "文件名正则无效。", validator: validateRegularExpression }]}
                        >
                            <Input inputClass="font-mono" />
                        </Form.FormItem>
                        <Form.FormItem label="文件名拒绝名单" name="nameRegexpDenyList">
                            <Switch />
                        </Form.FormItem>
                    </div>
                    {!local ? (
                        <Form.FormItem
                            label="批量删除数量"
                            name="deleteBatchSize"
                            rules={[
                                { max: 1000, message: "批量删除数量不能超过 1000。", number: true },
                                { min: 1, message: "批量删除数量不能小于 1。", number: true },
                            ]}
                        >
                            <InputNumber max={1000} min={1} style={{ width: "100%" }} />
                        </Form.FormItem>
                    ) : null}
                </Form>
            </ScrollArea>
        </GlobalDialog>
    );
}

function storagePolicyFromValues(values: StoragePolicyFormValues): StoragePolicy {
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

    return {
        apiVersion: "pudans.tolim.cn/v1alpha1",
        kind: "StoragePolicy",
        metadata: { name: newStoragePolicyName() },
        spec: {
            bucketName: local ? undefined : values.bucketName?.trim(),
            chunkSize: storagePolicyMegabytesToBytes(values.chunkSizeMb),
            credentials: local
                ? {}
                : oneDrive
                  ? {
                        clientSecret: values.clientSecret?.trim(),
                        token: values.token?.trim(),
                    }
                  : {
                        accessKey: values.accessKey?.trim(),
                        secretKey: values.secretKey?.trim(),
                    },
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

function validateFormValues(values: StoragePolicyFormValues): string | undefined {
    if (!isLocalStoragePolicy(values.type)) {
        if (!values.bucketName?.trim()) {
            return values.type === "onedrive" ? "请输入客户端 ID。" : "请输入 Bucket 名称。";
        }
        if (storagePolicyEndpointRequired(values.type) && !values.server?.trim()) {
            return "请输入 EndPoint。";
        }
        if (isOneDriveStoragePolicy(values.type)) {
            if (!values.clientSecret?.trim() || !values.token?.trim()) {
                return "请输入 OneDrive 客户端密钥和刷新令牌。";
            }
        } else if (!values.accessKey?.trim() || !values.secretKey?.trim()) {
            return values.type === "upyun" ? "请输入又拍云操作员和密码。" : "请输入 Access Key 和 Secret Key。";
        }
    }
    if (values.chunkSizeMb && values.chunkSizeMb > 0) {
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
