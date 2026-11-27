"use client";

import { useState } from "react";

import { Form, Input, InputNumber, Select } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { IdentityDialogFooter } from "@/modules/_workspace/_console/modules/identity/components/identity-dialog-footer";

import { BILLING_STATUS_OPTIONS } from "../../composables/_billing-meta";
import { type AiQuotaPlan, type AiQuotaPlanInput, createAiQuotaPlan, updateAiQuotaPlan } from "../../composables/_dans";

type QuotaPlanDialogProps = {
    onClose: () => void;
    onSaved: () => void;
    plan?: AiQuotaPlan;
};

type QuotaPlanForm = {
    displayName?: string;
    fiveHourCredits?: number;
    fiveHourMcpCalls?: number;
    fiveHourToolCalls?: number;
    name?: string;
    status?: string;
    weeklyCredits?: number;
    weeklyMcpCalls?: number;
    weeklyToolCalls?: number;
};

export function QuotaPlanDialog({ onClose, onSaved, plan }: QuotaPlanDialogProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [pending, setPending] = useState(false);
    const editing = Boolean(plan);

    const submit = async (values: QuotaPlanForm) => {
        const input: AiQuotaPlanInput = {
            displayName: values.displayName?.trim(),
            fiveHourCredits: values.fiveHourCredits ?? 0,
            fiveHourMcpCalls: values.fiveHourMcpCalls ?? 0,
            fiveHourToolCalls: values.fiveHourToolCalls ?? 0,
            name: values.name?.trim(),
            status: values.status ?? "active",
            weeklyCredits: values.weeklyCredits ?? 0,
            weeklyMcpCalls: values.weeklyMcpCalls ?? 0,
            weeklyToolCalls: values.weeklyToolCalls ?? 0,
        };

        setPending(true);
        try {
            if (plan) {
                await updateAiQuotaPlan(plan.id, input);
                prompt.success({ content: "配额计划已更新。" });
            } else {
                await createAiQuotaPlan(input);
                prompt.success({ content: "配额计划已创建。" });
            }
            onSaved();
            onClose();
        } catch (error) {
            prompt.error({
                content: consoleErrorMessage(error, plan ? "配额计划更新失败。" : "配额计划创建失败。"),
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
            title={editing ? "编辑配额计划" : "新建配额计划"}
            width={720}
        >
            <Form
                form={form}
                initialData={{
                    displayName: plan?.displayName ?? "",
                    fiveHourCredits: plan?.fiveHourCredits ?? 0,
                    fiveHourMcpCalls: plan?.fiveHourMcpCalls ?? 0,
                    fiveHourToolCalls: plan?.fiveHourToolCalls ?? 0,
                    name: plan?.name ?? "",
                    status: plan?.status ?? "active",
                    weeklyCredits: plan?.weeklyCredits ?? 0,
                    weeklyMcpCalls: plan?.weeklyMcpCalls ?? 0,
                    weeklyToolCalls: plan?.weeklyToolCalls ?? 0,
                }}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit(form.getFieldsValue(true) as QuotaPlanForm);
                    }
                }}
            >
                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem
                        label="计划标识"
                        name="name"
                        rules={[
                            { message: "请输入计划标识。", required: true, whitespace: true },
                            { message: "计划标识不能超过 128 个字符。", max: 128 },
                        ]}
                    >
                        <Input placeholder="例如 team-standard" />
                    </Form.FormItem>
                    <Form.FormItem
                        label="计划名称"
                        name="displayName"
                        rules={[{ message: "请输入计划名称。", required: true, whitespace: true }]}
                    >
                        <Input placeholder="用于界面展示的名称" />
                    </Form.FormItem>
                </div>

                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem label="5 小时积分" name="fiveHourCredits">
                        <InputNumber min={0} style={{ width: "100%" }} />
                    </Form.FormItem>
                    <Form.FormItem label="每周积分" name="weeklyCredits">
                        <InputNumber min={0} style={{ width: "100%" }} />
                    </Form.FormItem>
                </div>

                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem label="5 小时工具调用" name="fiveHourToolCalls">
                        <InputNumber decimalPlaces={0} min={0} style={{ width: "100%" }} />
                    </Form.FormItem>
                    <Form.FormItem label="每周工具调用" name="weeklyToolCalls">
                        <InputNumber decimalPlaces={0} min={0} style={{ width: "100%" }} />
                    </Form.FormItem>
                    <Form.FormItem label="5 小时 MCP 调用" name="fiveHourMcpCalls">
                        <InputNumber decimalPlaces={0} min={0} style={{ width: "100%" }} />
                    </Form.FormItem>
                    <Form.FormItem label="每周 MCP 调用" name="weeklyMcpCalls">
                        <InputNumber decimalPlaces={0} min={0} style={{ width: "100%" }} />
                    </Form.FormItem>
                </div>

                <Form.FormItem
                    help="5 小时窗口固定为 18000 秒，周窗口固定为 7 天，周窗口从分配生效时间起算。"
                    label="状态"
                    name="status"
                >
                    <Select options={[...BILLING_STATUS_OPTIONS]} />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
