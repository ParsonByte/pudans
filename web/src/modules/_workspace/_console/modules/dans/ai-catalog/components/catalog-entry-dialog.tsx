"use client";

import { useState } from "react";

import { DatePicker, Form, Input, InputNumber, Select, Switch, Textarea } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { IdentityDialogFooter } from "@/modules/_workspace/_console/modules/identity/components/identity-dialog-footer";

import { dateTimeInputValue, instantValue } from "../../composables/_billing-meta";
import {
    type CatalogEntry,
    type CatalogEntryInput,
    type CatalogEntryUpdateInput,
    createCatalogEntry,
    updateCatalogEntry,
} from "../../composables/_dans";
import { CATALOG_KIND_OPTIONS } from "../composables/_catalog";

type CatalogEntryDialogProps = {
    entry?: CatalogEntry;
    onClose: () => void;
    onSaved: () => void;
};

type CatalogEntryForm = {
    catalogKind?: string;
    description?: string;
    enabled?: boolean;
    entryKey?: string;
    label?: string;
    parentKey?: string;
    sortOrder?: number;
    validFrom?: string;
    validTo?: string;
};

const KIND_OPTIONS = CATALOG_KIND_OPTIONS.filter((option) => option.value !== "all");

function timeValue(value?: string | number | Date | null): number {
    if (!value) {
        return Number.NaN;
    }

    return value instanceof Date ? value.getTime() : new Date(value).getTime();
}

function EnabledField({ onChange, value }: { onChange?: (value: boolean) => void; value?: boolean }) {
    return (
        <div className="flex min-h-8 items-center gap-2">
            <Switch onChange={(next) => onChange?.(Boolean(next))} value={value ?? true} />
            <span className="text-sm text-[var(--td-text-color-secondary)]">启用后可在平台目录中使用</span>
        </div>
    );
}

export function CatalogEntryDialog({ entry, onClose, onSaved }: CatalogEntryDialogProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [pending, setPending] = useState(false);
    const editing = Boolean(entry);

    const submit = async (values: CatalogEntryForm) => {
        const common = {
            description: values.description?.trim() ?? "",
            enabled: values.enabled ?? true,
            label: values.label?.trim() ?? "",
            parentKey: values.parentKey?.trim() ?? "",
            sortOrder: values.sortOrder ?? 100,
            validFrom: instantValue(values.validFrom),
            validTo: instantValue(values.validTo),
        };
        const input: CatalogEntryInput = {
            ...common,
            catalogKind: values.catalogKind,
            entryKey: values.entryKey?.trim(),
        };
        const update: CatalogEntryUpdateInput = common;

        setPending(true);
        try {
            if (entry) {
                await updateCatalogEntry(entry.id, update);
                prompt.success({ content: "目录项已更新。" });
            } else {
                await createCatalogEntry(input);
                prompt.success({ content: "目录项已创建。" });
            }
            onSaved();
            onClose();
        } catch (error) {
            prompt.error({
                content: consoleErrorMessage(error, entry ? "目录项更新失败。" : "目录项创建失败。"),
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
                    submitLabel={editing ? "保存" : "创建"}
                />
            }
            onClose={close}
            open
            title={editing ? "编辑目录项" : "新建目录项"}
            width={720}
        >
            <Form
                form={form}
                initialData={{
                    catalogKind: entry?.catalogKind,
                    description: entry?.description ?? "",
                    enabled: entry?.enabled ?? true,
                    entryKey: entry?.entryKey ?? "",
                    label: entry?.label ?? "",
                    parentKey: entry?.parentKey ?? "",
                    sortOrder: entry?.sortOrder ?? 100,
                    validFrom: dateTimeInputValue(entry?.validFrom),
                    validTo: dateTimeInputValue(entry?.validTo),
                }}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit(form.getFieldsValue(true) as CatalogEntryForm);
                    }
                }}
            >
                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem
                        help="目录类型决定这个选项会出现在平台的哪个位置。"
                        label="目录类型"
                        name="catalogKind"
                        rules={[{ message: "请选择目录类型。", required: true }]}
                    >
                        <Select
                            disabled={editing}
                            filterable
                            options={KIND_OPTIONS.map((option) => ({ ...option }))}
                            placeholder="请选择目录类型"
                        />
                    </Form.FormItem>
                    <Form.FormItem
                        help="稳定标识，创建后不可修改。"
                        label="目录键"
                        name="entryKey"
                        rules={[
                            { message: "请输入目录键。", required: true, whitespace: true },
                            {
                                message: "只能使用小写字母、数字、点、下划线和连字符，且以字母或数字开头。",
                                pattern: /^[a-z0-9][a-z0-9._-]{0,127}$/,
                            },
                        ]}
                    >
                        <Input disabled={editing} maxlength={128} placeholder="例如 image 或 high" />
                    </Form.FormItem>
                </div>

                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem
                        label="显示名称"
                        name="label"
                        rules={[{ message: "请输入显示名称。", required: true, whitespace: true }]}
                    >
                        <Input maxlength={128} placeholder="展示给用户的中文名称" />
                    </Form.FormItem>
                    <Form.FormItem
                        help="没有上级目录时留空。"
                        label="上级目录键"
                        name="parentKey"
                        rules={[
                            {
                                message: "只能使用小写字母、数字、点、下划线和连字符，且以字母或数字开头。",
                                pattern: /^$|^[a-z0-9][a-z0-9._-]{0,127}$/,
                            },
                        ]}
                    >
                        <Input maxlength={128} placeholder="可选" />
                    </Form.FormItem>
                </div>

                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem help="数字越小越靠前。" label="排序" name="sortOrder">
                        <InputNumber min={0} style={{ width: "100%" }} />
                    </Form.FormItem>
                    <Form.FormItem label="状态" name="enabled">
                        <EnabledField />
                    </Form.FormItem>
                </div>

                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem
                        help="留空表示立即生效。"
                        label="生效时间"
                        name="validFrom"
                        rules={[
                            {
                                validator: (value) => {
                                    const validFrom = timeValue(value as string | undefined);
                                    const validTo = timeValue(form.getFieldValue("validTo") as string | undefined);

                                    if (
                                        !Number.isFinite(validFrom) ||
                                        !Number.isFinite(validTo) ||
                                        validTo > validFrom
                                    ) {
                                        return true;
                                    }

                                    return { message: "生效时间必须早于失效时间。", result: false };
                                },
                            },
                        ]}
                    >
                        <DatePicker
                            enableTimePicker
                            format="YYYY-MM-DD HH:mm:ss"
                            placeholder="立即生效"
                            style={{ width: "100%" }}
                            valueType="YYYY-MM-DD HH:mm:ss"
                        />
                    </Form.FormItem>
                    <Form.FormItem
                        help="留空表示长期有效。"
                        label="失效时间"
                        name="validTo"
                        rules={[
                            {
                                validator: (value) => {
                                    const validFrom = timeValue(form.getFieldValue("validFrom") as string | undefined);
                                    const validTo = timeValue(value as string | undefined);

                                    if (
                                        !Number.isFinite(validTo) ||
                                        !Number.isFinite(validFrom) ||
                                        validTo > validFrom
                                    ) {
                                        return true;
                                    }

                                    return { message: "失效时间必须晚于生效时间。", result: false };
                                },
                            },
                        ]}
                    >
                        <DatePicker
                            enableTimePicker
                            format="YYYY-MM-DD HH:mm:ss"
                            placeholder="长期有效"
                            style={{ width: "100%" }}
                            valueType="YYYY-MM-DD HH:mm:ss"
                        />
                    </Form.FormItem>
                </div>

                <Form.FormItem label="说明" name="description">
                    <Textarea autosize={{ maxRows: 4, minRows: 2 }} placeholder="可选，说明这个目录项的用途" />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
