"use client";

import { useState } from "react";

import type { SecretCatalogItem } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input } from "tdesign-react";
import type { FormRule } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";
import {
    DEFAULT_SECRET_TYPE,
    SECRET_GENERATED_KEY,
    SECRET_NAME_MAX_LENGTH,
    SECRET_NAME_PATTERN,
    SECRET_QUERY_ROOT,
    SECRET_TYPE_MAX_LENGTH,
    type SecretEditorEntry,
    type SecretEditorValues,
    type SecretEntryKind,
    buildCreateSecretPayload,
    buildSecretPatch,
    duplicateSecretKey,
    generateSecretValue,
} from "../composables/_secrets";
import { SecretKeySection } from "./secret-key-section";

type SecretEditorModalProps = {
    item?: SecretCatalogItem;
    mode: "create" | "edit";
    onClose: () => void;
};

type IssuedSecret = {
    key: string;
    name: string;
};

function nameRules(): FormRule[] {
    return [
        { message: "请输入名称。", required: true, whitespace: true },
        {
            message: "只能使用小写字母、数字、- 和 .，且必须以字母或数字开头结尾。",
            pattern: SECRET_NAME_PATTERN,
        },
        { message: `名称不能超过 ${SECRET_NAME_MAX_LENGTH} 个字符。`, max: SECRET_NAME_MAX_LENGTH },
    ];
}

function typeRules(): FormRule[] {
    return [
        { message: "请输入类型。", required: true, whitespace: true },
        { max: SECRET_TYPE_MAX_LENGTH, message: `类型不能超过 ${SECRET_TYPE_MAX_LENGTH} 个字符。` },
    ];
}

export function SecretEditorModal({ item, mode, onClose }: SecretEditorModalProps) {
    const [form] = Form.useForm();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [removedDataKeys, setRemovedDataKeys] = useState<Set<string>>(() => new Set());
    const [removedStringKeys, setRemovedStringKeys] = useState<Set<string>>(() => new Set());
    const [issuedSecret, setIssuedSecret] = useState<IssuedSecret>();
    const editing = mode === "edit";
    const initial = initialValues(item, editing);
    const saveSecret = useMutation({
        mutationFn: async (values: SecretEditorValues) => {
            if (editing) {
                const name = item?.name;
                if (!name) {
                    throw new Error("密钥名称缺失。");
                }
                const patch = buildSecretPatch({
                    dataEntries: values.dataEntries,
                    hasData: item?.hasData,
                    hasStringData: item?.hasStringData,
                    initialType: item?.type,
                    removedDataKeys,
                    removedStringKeys,
                    stringEntries: values.stringEntries,
                    type: values.type,
                });
                if (patch.length === 0) {
                    return { status: "unchanged" as const };
                }

                await apiClient.secret.patchSecret({ jsonPatchInner: patch, name });
                return { status: "saved" as const };
            }

            const key = generateSecretValue();
            await apiClient.secret.createSecret({
                secret: buildCreateSecretPayload({
                    name: values.name,
                    stringEntries: [{ key: SECRET_GENERATED_KEY, value: key }],
                }),
            });
            return { key, status: "created" as const };
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (result) => {
            if (result.status !== "unchanged") {
                void queryClient.invalidateQueries({ queryKey: SECRET_QUERY_ROOT });
            }
        },
    });

    const submit = async (values: SecretEditorValues) => {
        if (editing) {
            const duplicate = duplicateSecretKey(values.stringEntries, values.dataEntries);
            if (duplicate) {
                prompt.error({ content: `键名 ${duplicate} 重复，请保留一个。` });
                return;
            }
        }

        try {
            const result = await saveSecret.mutateAsync(values);
            if (result.status === "unchanged") {
                prompt.info({ content: "没有需要保存的更改。" });
                return;
            }
            if (result.status === "created") {
                setIssuedSecret({ key: result.key, name: values.name.trim() });
                return;
            }
            prompt.success({ content: "密钥已更新。" });
            onClose();
        } catch {
            return;
        }
    };

    const markRemoved = (kind: SecretEntryKind, originalKey?: string) => {
        if (!originalKey) {
            return;
        }
        const update = (current: Set<string>) => new Set(current).add(originalKey);
        if (kind === "data") {
            setRemovedDataKeys(update);
            return;
        }
        setRemovedStringKeys(update);
    };

    return (
        <GlobalDialog
            confirmLoading={saveSecret.isPending}
            footer={
                issuedSecret ? (
                    <div className="flex justify-end">
                        <Button onClick={onClose} theme="primary">
                            完成
                        </Button>
                    </div>
                ) : (
                    <IdentityDialogFooter
                        onCancel={onClose}
                        onSubmit={() => form.submit()}
                        pending={saveSecret.isPending}
                        submitLabel={editing ? "保存更改" : "创建密钥"}
                    />
                )
            }
            onClose={onClose}
            open
            title={issuedSecret ? "密钥已创建" : editing ? `编辑密钥 · ${item?.name ?? ""}` : "新建密钥"}
            width={editing ? 760 : 480}
        >
            {issuedSecret ? (
                <div className="flex min-w-0 flex-col gap-4">
                    <Alert message="密钥仅显示一次，请立即复制并妥善保存；关闭后将无法再次查看。" theme="warning" />
                    <div className="flex min-w-0 flex-col gap-4 rounded-xl border border-[var(--td-component-border)] px-4 py-4">
                        <div className="flex min-w-0 flex-col gap-1">
                            <span className="text-xs text-[var(--td-text-color-placeholder)]">名称</span>
                            <CopyableIdentifier mask value={issuedSecret.name} />
                        </div>
                        <div className="flex min-w-0 flex-col gap-1">
                            <span className="text-xs text-[var(--td-text-color-placeholder)]">密钥</span>
                            <CopyableIdentifier mask value={issuedSecret.key} />
                        </div>
                    </div>
                </div>
            ) : (
                <Form
                    form={form}
                    initialData={{ name: initial.name, type: initial.type }}
                    labelAlign="top"
                    layout="vertical"
                    onSubmit={({ validateResult }) => {
                        if (validateResult !== true) {
                            return;
                        }

                        void submit(form.getFieldsValue(true) as SecretEditorValues);
                    }}
                >
                    {editing ? (
                        <>
                            <div className="grid min-w-0 grid-cols-1 gap-x-3 sm:grid-cols-2">
                                <Form.FormItem
                                    help="名称创建后不可修改。"
                                    label="名称"
                                    name="name"
                                    requiredMark
                                    rules={nameRules()}
                                >
                                    <Input disabled placeholder="例如：smtp-credentials" />
                                </Form.FormItem>
                                <Form.FormItem
                                    help="用于标识用途，例如 Opaque。"
                                    label="类型"
                                    name="type"
                                    requiredMark
                                    rules={typeRules()}
                                >
                                    <Input placeholder={DEFAULT_SECRET_TYPE} />
                                </Form.FormItem>
                            </div>
                            <Alert
                                className="mt-4"
                                message="已有键的值只写不读：值留空表示保持原值，删除条目会移除对应键。"
                                theme="info"
                            />
                            <SecretKeySection
                                entries={initial.stringEntries ?? []}
                                fieldName="stringEntries"
                                form={form}
                                kind="stringData"
                                onRemove={(key) => markRemoved("stringData", key)}
                                otherFieldName="dataEntries"
                            />
                            <SecretKeySection
                                entries={initial.dataEntries ?? []}
                                fieldName="dataEntries"
                                form={form}
                                kind="data"
                                onRemove={(key) => markRemoved("data", key)}
                                otherFieldName="stringEntries"
                            />
                        </>
                    ) : (
                        <Form.FormItem
                            help="平台会自动生成密钥，创建后仅显示一次，请及时复制保存。"
                            label="名称"
                            name="name"
                            requiredMark
                            rules={nameRules()}
                        >
                            <Input placeholder="例如：smtp-credentials" />
                        </Form.FormItem>
                    )}
                </Form>
            )}
        </GlobalDialog>
    );
}

function initialValues(item: SecretCatalogItem | undefined, editing: boolean): SecretEditorValues {
    if (!editing || !item) {
        return { dataEntries: [], name: "", stringEntries: [], type: DEFAULT_SECRET_TYPE };
    }

    return {
        dataEntries: emptyEntries(item.dataKeys),
        name: item.name ?? "",
        stringEntries: emptyEntries(item.stringDataKeys),
        type: item.type ?? DEFAULT_SECRET_TYPE,
    };
}

function emptyEntries(keys?: string[]): SecretEditorEntry[] {
    return (keys ?? []).map((key) => ({
        key,
        originalKey: key,
        value: "",
    }));
}
