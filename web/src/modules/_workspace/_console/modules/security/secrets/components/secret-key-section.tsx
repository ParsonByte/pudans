"use client";

import { AddIcon, DeleteIcon } from "tdesign-icons-react";
import { Button, Form, Input } from "tdesign-react";
import type { FormRule } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import {
    SECRET_KEY_MAX_LENGTH,
    SECRET_KEY_PATTERN,
    type SecretEditorEntry,
    type SecretEntryKind,
    duplicateSecretKey,
    isSecretBase64,
    normalizeSecretDataValue,
} from "../composables/_secrets";

type SecretFormInstance = ReturnType<typeof Form.useForm>[0];

const SECRET_KEY_SECTION_COPY = {
    data: {
        addLabel: "添加二进制键",
        emptyText: "尚未添加二进制键。",
        help: "值必须为 Base64 编码，适合证书、密钥文件等二进制内容；编辑时已有键留空表示保持原值。",
        keyPlaceholder: "例如：tls.crt",
        removeLabel: "移除二进制键",
        title: "二进制键 data",
        valuePlaceholder: "输入 Base64 编码值",
    },
    stringData: {
        addLabel: "添加文本键",
        emptyText: "尚未添加文本键。",
        help: "适合令牌、密码、连接串等文本内容；编辑时已有键留空表示保持原值。",
        keyPlaceholder: "例如：password",
        removeLabel: "移除文本键",
        title: "文本键 stringData",
        valuePlaceholder: "输入文本值",
    },
} as const;

type SecretKeySectionProps = {
    entries: SecretEditorEntry[];
    fieldName: "dataEntries" | "stringEntries";
    form: SecretFormInstance;
    kind: SecretEntryKind;
    onRemove: (originalKey?: string) => void;
    otherFieldName: "dataEntries" | "stringEntries";
};

export function SecretKeySection({ entries, fieldName, form, kind, onRemove, otherFieldName }: SecretKeySectionProps) {
    const token = useDesignTokens();
    const copy = SECRET_KEY_SECTION_COPY[kind];

    return (
        <section className="mt-5 min-w-0">
            <div className="text-sm font-medium" style={{ color: token.colorText }}>
                {copy.title}
            </div>
            <div className="mt-1 text-xs leading-5" style={{ color: token.colorTextTertiary }}>
                {copy.help}
            </div>
            <Form.FormList initialData={entries} name={fieldName}>
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
                                        {
                                            message: `键名不能超过 ${SECRET_KEY_MAX_LENGTH} 个字符。`,
                                            max: SECRET_KEY_MAX_LENGTH,
                                        },
                                        {
                                            validator: (value) => {
                                                const ownEntries = form.getFieldValue(fieldName) as
                                                    SecretEditorEntry[] | undefined;
                                                const otherEntries = form.getFieldValue(otherFieldName) as
                                                    SecretEditorEntry[] | undefined;
                                                const replaced = (ownEntries ?? []).map((entry, index) =>
                                                    index === field.name
                                                        ? { ...entry, key: String(value ?? "") }
                                                        : entry,
                                                );
                                                const duplicate = duplicateSecretKey(replaced, otherEntries);

                                                return duplicate
                                                    ? {
                                                          message: `键名 ${duplicate} 重复，请保留一个。`,
                                                          result: false,
                                                      }
                                                    : true;
                                            },
                                        },
                                    ]}
                                >
                                    <Input className="font-mono" placeholder={copy.keyPlaceholder} />
                                </Form.FormItem>
                                <Form.FormItem
                                    label="值"
                                    name={[field.name, "value"]}
                                    rules={valueRules(form, fieldName, field.name, kind)}
                                >
                                    <Input
                                        autocomplete="new-password"
                                        className="font-mono"
                                        placeholder={copy.valuePlaceholder}
                                        type="password"
                                    />
                                </Form.FormItem>
                                <Button
                                    aria-label={copy.removeLabel}
                                    className="mt-7"
                                    icon={<DeleteIcon />}
                                    onClick={() => {
                                        const current = form.getFieldValue(fieldName) as
                                            SecretEditorEntry[] | undefined;
                                        onRemove(current?.[field.name]?.originalKey);
                                        remove(field.name);
                                    }}
                                    theme="danger"
                                    variant="text"
                                />
                            </div>
                        ))}
                        {fields.length === 0 ? (
                            <span className="text-xs" style={{ color: token.colorTextTertiary }}>
                                {copy.emptyText}
                            </span>
                        ) : null}
                        <div>
                            <Button icon={<AddIcon />} onClick={() => add({ key: "", value: "" })} variant="dashed">
                                {copy.addLabel}
                            </Button>
                        </div>
                    </div>
                )}
            </Form.FormList>
        </section>
    );
}

function valueRules(
    form: SecretFormInstance,
    fieldName: "dataEntries" | "stringEntries",
    index: number,
    kind: SecretEntryKind,
): FormRule[] {
    return [
        {
            validator: (value) => {
                const entries = form.getFieldValue(fieldName) as SecretEditorEntry[] | undefined;
                const entry = entries?.[index];
                const unchanged = Boolean(entry?.originalKey && entry.originalKey.trim() === entry.key?.trim());
                const normalized =
                    kind === "data" ? normalizeSecretDataValue(String(value ?? "")) : String(value ?? "");

                if (unchanged && !normalized) {
                    return true;
                }
                if (!normalized) {
                    return { message: "新增或改名的键必须填写值。", result: false };
                }
                if (kind === "data" && !isSecretBase64(normalized)) {
                    return { message: "请输入标准 Base64 编码值。", result: false };
                }

                return true;
            },
        },
    ];
}
