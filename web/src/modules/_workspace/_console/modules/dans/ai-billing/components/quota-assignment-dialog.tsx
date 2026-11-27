"use client";

import { useMemo, useState } from "react";

import { ChevronDownIcon, ChevronUpIcon } from "tdesign-icons-react";
import { Alert, Button, DatePicker, Form, Select } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { IdentityDialogFooter } from "@/modules/_workspace/_console/modules/identity/components/identity-dialog-footer";
import {
    sortUserGroups,
    userGroupDisplayName,
} from "@/modules/_workspace/_console/modules/identity/user-groups/composables/_user-group";
import { useUserGroupsQuery } from "@/modules/_workspace/_console/modules/identity/user-groups/composables/_user-group-queries";

import { BILLING_STATUS_OPTIONS, dateTimeInputValue, instantValue } from "../../composables/_billing-meta";
import {
    type AiQuotaAssignment,
    type AiQuotaAssignmentInput,
    type AiQuotaPlan,
    createAiQuotaAssignment,
    updateAiQuotaAssignment,
} from "../../composables/_dans";

type QuotaAssignmentDialogProps = {
    assignment?: AiQuotaAssignment;
    onClose: () => void;
    onSaved: () => void;
    plans: AiQuotaPlan[];
};

type QuotaAssignmentForm = {
    effectiveFrom?: string;
    effectiveTo?: string;
    planId?: string;
    status?: string;
    subjectId?: string;
    weeklyAnchorAt?: string;
};

const SUBJECT_TYPE = "user_group";

function planOptionLabel(plan: AiQuotaPlan): string {
    const name = plan.displayName ?? plan.name ?? plan.id;

    return plan.name && plan.name !== name ? `${name}（${plan.name}）` : name;
}

function timeValue(value?: string | number | Date | null): number {
    if (!value) {
        return Number.NaN;
    }

    return value instanceof Date ? value.getTime() : new Date(value).getTime();
}

export function QuotaAssignmentDialog({ assignment, onClose, onSaved, plans }: QuotaAssignmentDialogProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [pending, setPending] = useState(false);
    const [showAdvanced, setShowAdvanced] = useState(
        Boolean(assignment?.weeklyAnchorAt && assignment.weeklyAnchorAt !== assignment.effectiveFrom),
    );
    const groupsQuery = useUserGroupsQuery();
    const editing = Boolean(assignment);
    const canSubmit = plans.length > 0 && !groupsQuery.isPending && !groupsQuery.error;
    const groupOptions = useMemo(
        () =>
            sortUserGroups(groupsQuery.data ?? []).map((group) => ({
                label: `${userGroupDisplayName(group)}（${group.metadata.name}）`,
                value: group.metadata.name,
            })),
        [groupsQuery.data],
    );
    const planOptions = useMemo(
        () =>
            plans
                .slice()
                .sort((left, right) => planOptionLabel(left).localeCompare(planOptionLabel(right), "zh-CN"))
                .map((plan) => ({ label: planOptionLabel(plan), value: plan.id })),
        [plans],
    );

    const submit = async (values: QuotaAssignmentForm) => {
        const subjectId = values.subjectId?.trim();
        if (!subjectId || !values.planId || !values.effectiveFrom) {
            return;
        }
        const input: AiQuotaAssignmentInput = {
            effectiveFrom: instantValue(values.effectiveFrom),
            effectiveTo: instantValue(values.effectiveTo),
            planId: values.planId,
            status: values.status ?? "active",
            subjectId,
            subjectType: SUBJECT_TYPE,
            weeklyAnchorAt: instantValue(values.weeklyAnchorAt),
        };

        setPending(true);
        try {
            if (assignment) {
                await updateAiQuotaAssignment(assignment.id, input);
                prompt.success({ content: "配额分配已更新。" });
            } else {
                await createAiQuotaAssignment(input);
                prompt.success({ content: "配额分配已创建。" });
            }
            onSaved();
            onClose();
        } catch (error) {
            prompt.error({
                content: consoleErrorMessage(error, assignment ? "配额分配更新失败。" : "配额分配创建失败。"),
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
                    disabled={!canSubmit}
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={pending}
                    submitLabel={editing ? "保存" : "创建"}
                />
            }
            onClose={close}
            open
            title={editing ? "编辑配额分配" : "新建配额分配"}
            width={720}
        >
            <Form
                form={form}
                initialData={{
                    effectiveFrom: dateTimeInputValue(assignment?.effectiveFrom) ?? dateTimeInputValue(new Date()),
                    effectiveTo: dateTimeInputValue(assignment?.effectiveTo),
                    planId: assignment?.planId,
                    status: assignment?.status ?? "active",
                    subjectId: assignment?.subjectType === SUBJECT_TYPE ? assignment.subjectId : undefined,
                    weeklyAnchorAt: dateTimeInputValue(assignment?.weeklyAnchorAt),
                }}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit(form.getFieldsValue(true) as QuotaAssignmentForm);
                    }
                }}
            >
                <div className="mb-5 rounded-xl bg-[var(--td-bg-color-secondarycontainer)] px-4 py-3 text-sm text-[var(--td-text-color-secondary)]">
                    配额分配统一按用户组设置，组内所有成员共用同一个配额计划。
                </div>

                {groupsQuery.error ? (
                    <Alert
                        message={consoleErrorMessage(groupsQuery.error, "用户组加载失败。")}
                        operation={
                            <Button onClick={() => void groupsQuery.refetch()} size="small" variant="text">
                                重试
                            </Button>
                        }
                        theme="error"
                    />
                ) : null}
                {plans.length === 0 ? (
                    <Alert message="还没有配额计划，请先在“配额计划”中创建计划。" theme="warning" />
                ) : null}

                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem
                        help="组内成员会自动使用这个配额计划。"
                        label="用户组"
                        name="subjectId"
                        rules={[{ message: "请选择用户组。", required: true }]}
                    >
                        <Select
                            filterable
                            loading={groupsQuery.isPending}
                            options={groupOptions}
                            placeholder="搜索并选择用户组"
                        />
                    </Form.FormItem>
                    <Form.FormItem
                        help="计划决定 5 小时和每周可用的积分与调用次数。"
                        label="配额计划"
                        name="planId"
                        rules={[{ message: "请选择配额计划。", required: true }]}
                    >
                        <Select filterable options={planOptions} placeholder="选择配额计划" />
                    </Form.FormItem>
                </div>

                <div className="grid gap-x-4 md:grid-cols-2">
                    <Form.FormItem
                        label="生效时间"
                        name="effectiveFrom"
                        rules={[{ message: "请选择生效时间。", required: true }]}
                    >
                        <DatePicker
                            enableTimePicker
                            format="YYYY-MM-DD HH:mm:ss"
                            style={{ width: "100%" }}
                            valueType="YYYY-MM-DD HH:mm:ss"
                        />
                    </Form.FormItem>
                    <Form.FormItem
                        label="失效时间"
                        name="effectiveTo"
                        rules={[
                            {
                                validator: (value) => {
                                    const effectiveFrom = timeValue(form.getFieldValue("effectiveFrom") as string);
                                    const effectiveTo = timeValue(value as string | undefined);

                                    if (
                                        !Number.isFinite(effectiveTo) ||
                                        !Number.isFinite(effectiveFrom) ||
                                        effectiveTo > effectiveFrom
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
                            placeholder="留空表示长期有效"
                            style={{ width: "100%" }}
                            valueType="YYYY-MM-DD HH:mm:ss"
                        />
                    </Form.FormItem>
                </div>

                <Form.FormItem help="停用后，组内成员不会再使用这个配额计划。" label="状态" name="status">
                    <Select options={[...BILLING_STATUS_OPTIONS]} />
                </Form.FormItem>

                <div className="my-4 flex items-center justify-between gap-3 border-t border-[var(--td-border-level-1-color)] pt-3">
                    <span className="text-xs text-[var(--td-text-color-placeholder)]">
                        默认从生效时间开始计算每周额度，通常无需调整。
                    </span>
                    <Button
                        icon={showAdvanced ? <ChevronUpIcon /> : <ChevronDownIcon />}
                        onClick={() => setShowAdvanced((current) => !current)}
                        size="small"
                        variant="text"
                    >
                        {showAdvanced ? "收起高级设置" : "高级设置"}
                    </Button>
                </div>

                {showAdvanced ? (
                    <div className="rounded-xl border border-[var(--td-border-level-1-color)] p-4">
                        <Form.FormItem
                            help="只影响每周额度的起算时间；留空时与生效时间一致。"
                            label="每周额度起算时间"
                            name="weeklyAnchorAt"
                            rules={[
                                {
                                    validator: (value) => {
                                        const anchor = timeValue(value as string | undefined);
                                        const effectiveFrom = timeValue(form.getFieldValue("effectiveFrom") as string);

                                        if (
                                            !Number.isFinite(anchor) ||
                                            !Number.isFinite(effectiveFrom) ||
                                            anchor >= effectiveFrom
                                        ) {
                                            return true;
                                        }

                                        return { message: "起算时间不能早于生效时间。", result: false };
                                    },
                                },
                            ]}
                        >
                            <DatePicker
                                enableTimePicker
                                format="YYYY-MM-DD HH:mm:ss"
                                placeholder="默认与生效时间一致"
                                style={{ width: "100%" }}
                                valueType="YYYY-MM-DD HH:mm:ss"
                            />
                        </Form.FormItem>
                    </div>
                ) : null}
            </Form>
        </GlobalDialog>
    );
}
