"use client";

import { useState } from "react";

import type { SecretCatalogItem } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AddIcon, DeleteIcon } from "tdesign-icons-react";
import { Button, Form, Input } from "tdesign-react";
import type { FormRule } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../../../../composables/_errors";
import {
    DEFAULT_SECRET_TYPE,
    SECRET_KEY_PATTERN,
    SECRET_NAME_PATTERN,
    SECRET_QUERY_ROOT,
    type SecretEditorEntry,
    type SecretEditorValues,
    buildCreateSecretPayload,
    buildSecretPatch,
    duplicateSecretKey,
} from "../composables/_secrets";

type SecretEditorModalProps = {
    item?: SecretCatalogItem;
    mode: "create" | "edit";
    onClose: () => void;
};

function nameRules(): FormRule[] {
    return [
        { message: "请输入名称。", required: true, whitespace: true },
        {
            message: "只能使用小写字母、数字、- 和 .，且必须以字母或数字开头结尾。",
            pattern: SECRET_NAME_PATTERN,
        },
        { message: "名称不能超过 253 个字符。", max: 253 },
    ];
}

function typeRules(): FormRule[] {
    return [
        { message: "请输入类型。", required: true, whitespace: true },
        { max: 128, message: "类型不能超过 128 个字符。" },
    ];
}

export function SecretEditorModal({ item, mode, onClose }: SecretEditorModalProps) {
    const [form] = Form.useForm();
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [removedKeys, setRemovedKeys] = useState<Set<string>>(() => new Set());
    const editing = mode === "edit";
    const binaryKeys = new Set(item?.dataKeys ?? []);
    const initial = initialValues(item, editing);
    const saveSecret = useMutation({
        mutationFn: async (values: SecretEditorValues) => {
            if (editing) {
                const name = item?.name;
                if (!name) {
                    throw new Error("密钥名称缺失。");
                }
                const patch = buildSecretPatch({
                    entries: values.entries,
                    hasStringData: item?.hasStringData,
                    initialType: item?.type,
                    removedKeys,
                    type: values.type,
                });
                if (patch.length === 0) {
                    return "unchanged" as const;
                }

                await apiClient.secret.patchSecret({ jsonPatchInner: patch, name });
                return "saved" as const;
            }

            await apiClient.secret.createSecret({ secret: buildCreateSecretPayload(values) });
            return "saved" as const;
        },
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (result) => {
            if (result === "saved") {
                void queryClient.invalidateQueries({ queryKey: SECRET_QUERY_ROOT });
            }
        },
    });

    const submit = async (values: SecretEditorValues) => {
        const duplicate = duplicateSecretKey(values.entries);
        if (duplicate) {
            prompt.error({ content: `文本键 ${duplicate} 重复，请保留一个。` });
            return;
        }

        try {
            const result = await saveSecret.mutateAsync(values);
            if (result === "unchanged") {
                prompt.info({ content: "没有需要保存的更改。" });
            } else {
                prompt.success({ content: editing ? "密钥已更新。" : "密钥已创建。" });
            }
            onClose();
        } catch {
            return;
        }
    };

    const removeEntry = (index: number) => {
        const entries = form.getFieldValue("entries") as SecretEditorEntry[] | undefined;
        const originalKey = entries?.[index]?.originalKey?.trim();
        if (originalKey) {
            setRemovedKeys((current) => {
                const next = new Set(current);
                next.add(originalKey);
                return next;
            });
        }
    };

    return (
        <GlobalDialog
            confirmLoading={saveSecret.isPending}
            confirmText={editing ? "保存更改" : "创建密钥"}
            onClose={onClose}
            onConfirm={() => form.submit()}
            open
            title={editing ? `编辑密钥 · ${item?.name ?? ""}` : "新建密钥"}
            width={760}
        >
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
                <div className="grid min-w-0 grid-cols-1 gap-x-3 sm:grid-cols-2">
                    <Form.FormItem label="名称" name="name" requiredMark rules={nameRules()}>
                        <Input disabled={editing} placeholder="例如：smtp-credentials" />
                    </Form.FormItem>
                    <Form.FormItem label="类型" name="type" requiredMark rules={typeRules()}>
                        <Input placeholder={DEFAULT_SECRET_TYPE} />
                    </Form.FormItem>
                </div>
                <div className="mt-4 text-sm font-medium" style={{ color: "var(--td-text-color-primary)" }}>
                    文本键值
                </div>
                <Form.FormList initialData={initial.entries} name="entries">
                    {(fields, { add, remove }) => (
                        <div className="mt-3 flex min-w-0 flex-col gap-3">
                            {fields.map((field) => (
                                <div
                                    className="grid min-w-0 grid-cols-1 items-start gap-x-3 sm:grid-cols-[1fr_1fr_auto]"
                                    key={field.key}
                                >
                                    <Form.FormItem name={[field.name, "originalKey"]} style={{ display: "none" }}>
                                        <Input />
                                    </Form.FormItem>
                                    <Form.FormItem
                                        label="键名"
                                        name={[field.name, "key"]}
                                        rules={[
                                            { message: "请输入键名。", required: true, whitespace: true },
                                            {
                                                message: "只能使用字母、数字、-、_ 和 .。",
                                                pattern: SECRET_KEY_PATTERN,
                                            },
                                            { max: 253, message: "键名不能超过 253 个字符。" },
                                            {
                                                message: "键名不能重复。",
                                                validator: (value) => {
                                                    if (editing && binaryKeys.has(String(value ?? "").trim())) {
                                                        return {
                                                            message: "键名与已有二进制键重复。",
                                                            result: false,
                                                        };
                                                    }
                                                    const entries = form.getFieldValue("entries") as
                                                        SecretEditorEntry[] | undefined;
                                                    const keys = (entries ?? [])
                                                        .map((entry, index) =>
                                                            index === field.name
                                                                ? String(value ?? "").trim()
                                                                : entry.key?.trim(),
                                                        )
                                                        .filter(Boolean);

                                                    return {
                                                        message: "键名不能重复。",
                                                        result: new Set(keys).size === keys.length,
                                                    };
                                                },
                                            },
                                        ]}
                                    >
                                        <Input className="font-mono" placeholder="例如：password" />
                                    </Form.FormItem>
                                    <Form.FormItem
                                        label="值"
                                        name={[field.name, "value"]}
                                        rules={[
                                            {
                                                message: "新增或改名的键必须填写值。",
                                                validator: (value) => {
                                                    const entries = form.getFieldValue("entries") as
                                                        SecretEditorEntry[] | undefined;
                                                    const entry = entries?.[field.name];
                                                    const key = entry?.key?.trim();
                                                    const originalKey = entry?.originalKey?.trim();

                                                    return {
                                                        message: "新增或改名的键必须填写值。",
                                                        result:
                                                            Boolean(originalKey && originalKey === key) ||
                                                            Boolean(value),
                                                    };
                                                },
                                            },
                                        ]}
                                    >
                                        <Input autocomplete="new-password" placeholder="输入密钥值" type="password" />
                                    </Form.FormItem>
                                    <Button
                                        aria-label="移除文本键"
                                        className="mt-7"
                                        icon={<DeleteIcon />}
                                        onClick={() => {
                                            removeEntry(field.name);
                                            remove(field.name);
                                        }}
                                        theme="danger"
                                        variant="text"
                                    />
                                </div>
                            ))}
                            <Button icon={<AddIcon />} onClick={() => add({ key: "", value: "" })} variant="dashed">
                                添加文本键
                            </Button>
                        </div>
                    )}
                </Form.FormList>
            </Form>
        </GlobalDialog>
    );
}

function initialValues(item: SecretCatalogItem | undefined, editing: boolean): SecretEditorValues {
    if (!editing || !item) {
        return { entries: [], name: "", type: DEFAULT_SECRET_TYPE };
    }

    return {
        entries: (item.stringDataKeys ?? []).map((key) => ({
            key,
            originalKey: key,
            value: "",
        })),
        name: item.name ?? "",
        type: item.type ?? DEFAULT_SECRET_TYPE,
    };
}
