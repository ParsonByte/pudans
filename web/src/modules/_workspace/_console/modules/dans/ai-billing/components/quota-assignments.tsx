"use client";

import { useMemo, useState } from "react";

import type { UserGroup } from "@pudans-dev/api-client";
import { DeleteIcon, EditIcon, MoreIcon, SearchIcon, TimeIcon, UsergroupIcon } from "tdesign-icons-react";
import { Alert, Button, Card, Dropdown, Empty, Input, Select, Skeleton, Tag } from "tdesign-react";
import type { DropdownOption } from "tdesign-react";

import {
    userGroupDescription,
    userGroupDisplayName,
} from "@/modules/_workspace/_console/modules/identity/user-groups/composables/_user-group";
import { useUserGroupsQuery } from "@/modules/_workspace/_console/modules/identity/user-groups/composables/_user-group-queries";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatCount, formatDateTime } from "../../../../composables/_format";
import {
    STATUS_OPTIONS,
    assignmentStatusLabel,
    formatCredits,
    subjectTypeLabel,
} from "../../composables/_billing-meta";
import type { AiQuotaAssignment, AiQuotaPlan } from "../../composables/_dans";

type QuotaAssignmentsProps = {
    assignments: AiQuotaAssignment[];
    error?: unknown;
    loading: boolean;
    onCreate?: () => void;
    onDelete?: (assignment: AiQuotaAssignment) => void;
    onEdit?: (assignment: AiQuotaAssignment) => void;
    onRetry: () => void;
    onShowPlans: () => void;
    plans: AiQuotaPlan[];
};

export function QuotaAssignments({
    assignments,
    error,
    loading,
    onCreate,
    onDelete,
    onEdit,
    onRetry,
    onShowPlans,
    plans,
}: QuotaAssignmentsProps) {
    const [keyword, setKeyword] = useState("");
    const [planId, setPlanId] = useState("all");
    const [status, setStatus] = useState("all");
    const groupsQuery = useUserGroupsQuery();
    const plansById = useMemo(() => new Map(plans.map((plan) => [plan.id, plan])), [plans]);
    const groupsById = useMemo(
        () => new Map((groupsQuery.data ?? []).map((group) => [group.metadata.name, group])),
        [groupsQuery.data],
    );
    const planOptions = useMemo(
        () => [
            { label: "全部计划", value: "all" },
            ...plans
                .map((plan) => ({ label: plan.displayName ?? plan.name ?? plan.id, value: plan.id }))
                .sort((left, right) => left.label.localeCompare(right.label, "zh-CN")),
        ],
        [plans],
    );
    const normalizedKeyword = keyword.trim().toLowerCase();
    const hasFilters = Boolean(keyword) || planId !== "all" || status !== "all";
    const filteredAssignments = useMemo(
        () =>
            assignments
                .filter((assignment) => {
                    if (planId !== "all" && assignment.planId !== planId) {
                        return false;
                    }
                    if (status !== "all" && assignment.status !== status) {
                        return false;
                    }
                    if (!normalizedKeyword) {
                        return true;
                    }
                    const plan = assignment.planId ? plansById.get(assignment.planId) : undefined;
                    const group = assignment.subjectId ? groupsById.get(assignment.subjectId) : undefined;

                    return [
                        assignment.subjectId,
                        assignment.id,
                        subjectTypeLabel(assignment.subjectType),
                        group ? userGroupDisplayName(group) : undefined,
                        plan?.displayName,
                        plan?.name,
                        plan?.id,
                    ].some((value) => value?.toLowerCase().includes(normalizedKeyword));
                })
                .sort((left, right) => {
                    const statusOrder = Number(right.status === "active") - Number(left.status === "active");
                    const leftFrom = left.effectiveFrom ? Date.parse(left.effectiveFrom) : 0;
                    const rightFrom = right.effectiveFrom ? Date.parse(right.effectiveFrom) : 0;
                    const leftGroup = left.subjectId ? groupsById.get(left.subjectId) : undefined;
                    const rightGroup = right.subjectId ? groupsById.get(right.subjectId) : undefined;
                    const leftLabel = leftGroup ? userGroupDisplayName(leftGroup) : (left.subjectId ?? left.id);
                    const rightLabel = rightGroup ? userGroupDisplayName(rightGroup) : (right.subjectId ?? right.id);

                    return statusOrder || rightFrom - leftFrom || leftLabel.localeCompare(rightLabel, "zh-CN");
                }),
        [assignments, groupsById, normalizedKeyword, planId, plansById, status],
    );
    const resetFilters = () => {
        setKeyword("");
        setPlanId("all");
        setStatus("all");
    };
    if (error) {
        return (
            <Alert
                message={consoleErrorMessage(error)}
                operation={
                    <Button onClick={onRetry} size="small" variant="text">
                        重试
                    </Button>
                }
                theme="error"
            />
        );
    }

    if (loading) {
        return (
            <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-2">
                {Array.from({ length: 4 }, (_, index) => (
                    <Card bordered className="min-w-0" key={index}>
                        <Skeleton animation="gradient" rowCol={[1, 2, 1, 2]} />
                    </Card>
                ))}
            </div>
        );
    }

    if (assignments.length === 0) {
        return <EmptyAssignments onCreate={onCreate} onShowPlans={onShowPlans} plans={plans} />;
    }

    if (filteredAssignments.length === 0) {
        return (
            <Card bordered={false}>
                <Empty
                    action={
                        <Button onClick={resetFilters} variant="outline">
                            清除筛选
                        </Button>
                    }
                    description="没有匹配的配额分配。"
                />
            </Card>
        );
    }

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex min-w-0 flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <div className="w-full min-w-0 sm:w-[280px]">
                        <Input
                            clearable
                            onChange={(value) => setKeyword(value)}
                            placeholder="搜索主体或配额计划"
                            prefixIcon={<SearchIcon />}
                            value={keyword}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[210px]">
                        <Select
                            filterable
                            onChange={(value) => setPlanId(String(value))}
                            options={planOptions}
                            value={planId}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[140px]">
                        <Select
                            onChange={(value) => setStatus(String(value))}
                            options={STATUS_OPTIONS.map((option) => ({ ...option }))}
                            value={status}
                        />
                    </div>
                    {hasFilters ? (
                        <Button onClick={resetFilters} size="small" variant="text">
                            清除筛选
                        </Button>
                    ) : null}
                </div>
                <span className="shrink-0 text-xs text-[var(--td-text-color-secondary)]">
                    {filteredAssignments.length === assignments.length
                        ? `已分配 ${formatCount(assignments.length)} 个用户组`
                        : `筛选出 ${formatCount(filteredAssignments.length)} / ${formatCount(assignments.length)} 个用户组`}
                </span>
            </div>

            <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-2">
                {filteredAssignments.map((assignment) => (
                    <QuotaAssignmentCard
                        assignment={assignment}
                        group={assignment.subjectId ? groupsById.get(assignment.subjectId) : undefined}
                        key={assignment.id}
                        onDelete={onDelete ? () => onDelete(assignment) : undefined}
                        onEdit={
                            assignment.subjectType === "user_group" && onEdit ? () => onEdit(assignment) : undefined
                        }
                        plan={assignment.planId ? plansById.get(assignment.planId) : undefined}
                    />
                ))}
            </div>
        </div>
    );
}

function EmptyAssignments({
    onCreate,
    onShowPlans,
    plans,
}: {
    onCreate?: () => void;
    onShowPlans: () => void;
    plans: AiQuotaPlan[];
}) {
    const canAssign = Boolean(onCreate && plans.length > 0);

    return (
        <Card bordered={false} className="overflow-hidden">
            <div className="flex flex-col gap-6 px-2 py-3 sm:px-6 md:flex-row md:items-center md:justify-between">
                <div className="flex min-w-0 items-start gap-4">
                    <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]">
                        <UsergroupIcon size={24} />
                    </span>
                    <div className="min-w-0">
                        <h3 className="m-0 text-base font-semibold">还没有为用户组分配配额</h3>
                        <p className="m-0 mt-1 max-w-2xl text-sm leading-6 text-[var(--td-text-color-secondary)]">
                            选择一个用户组并关联配额计划，组内成员会自动共用同一份额度和限制。
                        </p>
                        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-[var(--td-text-color-placeholder)]">
                            <span>1. 创建配额计划</span>
                            <span>→</span>
                            <span>2. 选择用户组</span>
                            <span>→</span>
                            <span>3. 确认生效时间</span>
                        </div>
                    </div>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-2 md:justify-end">
                    {canAssign ? (
                        <Button onClick={onCreate} theme="primary">
                            新建配额分配
                        </Button>
                    ) : (
                        <Button onClick={onShowPlans} theme="primary">
                            先创建配额计划
                        </Button>
                    )}
                    {canAssign ? (
                        <Button onClick={onShowPlans} variant="outline">
                            查看配额计划
                        </Button>
                    ) : null}
                </div>
            </div>
        </Card>
    );
}

function QuotaAssignmentCard({
    assignment,
    group,
    onDelete,
    onEdit,
    plan,
}: {
    assignment: AiQuotaAssignment;
    group?: UserGroup;
    onDelete?: () => void;
    onEdit?: () => void;
    plan?: AiQuotaPlan;
}) {
    const userGroup = assignment.subjectType === "user_group";
    const groupName = group ? userGroupDisplayName(group) : (assignment.subjectId ?? "未命名用户组");
    const description = group ? userGroupDescription(group) : "";
    const planName = plan?.displayName ?? plan?.name ?? assignment.planId ?? "未关联配额计划";
    const actions: DropdownOption[] = [
        ...(onEdit ? [{ content: "编辑分配", prefixIcon: <EditIcon size={16} />, value: "edit" }] : []),
        ...(onDelete
            ? [{ content: "删除分配", prefixIcon: <DeleteIcon size={16} />, theme: "error" as const, value: "delete" }]
            : []),
    ];

    return (
        <Card bordered className="h-full min-w-0">
            <article className="flex h-full min-w-0 flex-col gap-4">
                <div className="flex min-w-0 items-start gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]">
                        <UsergroupIcon size={20} />
                    </span>
                    <div className="min-w-0 flex-1">
                        <div className="flex min-w-0 items-start justify-between gap-2">
                            <div className="min-w-0">
                                <div className="flex min-w-0 flex-wrap items-center gap-2">
                                    <h3 className="m-0 truncate text-base font-semibold" title={groupName}>
                                        {groupName}
                                    </h3>
                                    <Tag theme={assignment.status === "active" ? "success" : "default"} variant="light">
                                        {assignmentStatusLabel(assignment.status)}
                                    </Tag>
                                    {!userGroup ? (
                                        <Tag theme="warning" variant="light">
                                            历史{subjectTypeLabel(assignment.subjectType)}
                                        </Tag>
                                    ) : null}
                                </div>
                                <div className="mt-1 min-w-0">
                                    <CopyableIdentifier
                                        className="max-w-full"
                                        display={
                                            <span className="min-w-0 truncate" title={assignment.subjectId}>
                                                {assignment.subjectId ?? "未设置用户组"}
                                            </span>
                                        }
                                        quiet
                                        value={assignment.subjectId}
                                    />
                                </div>
                            </div>
                            {actions.length > 0 ? (
                                <Dropdown
                                    onClick={(item) => {
                                        if (item.value === "edit") {
                                            onEdit?.();
                                        } else if (item.value === "delete") {
                                            onDelete?.();
                                        }
                                    }}
                                    options={actions}
                                    placement="bottom-right"
                                    trigger="click"
                                >
                                    <Button
                                        aria-label={`${groupName} 的配额分配操作`}
                                        icon={<MoreIcon />}
                                        shape="square"
                                        size="small"
                                        variant="text"
                                    />
                                </Dropdown>
                            ) : null}
                        </div>
                        {description ? (
                            <p className="m-0 mt-2 line-clamp-2 text-xs leading-5 text-[var(--td-text-color-secondary)]">
                                {description}
                            </p>
                        ) : null}
                    </div>
                </div>

                <div className="flex min-w-0 flex-col gap-3 rounded-xl bg-[var(--td-bg-color-secondarycontainer)] px-4 py-3">
                    <div className="flex min-w-0 items-start justify-between gap-3">
                        <div className="min-w-0">
                            <span className="text-[11px] text-[var(--td-text-color-secondary)]">配额计划</span>
                            <div className="mt-0.5 min-w-0">
                                <div className="truncate font-medium" title={planName}>
                                    {planName}
                                </div>
                                {plan?.name ? (
                                    <CopyableIdentifier
                                        className="max-w-full text-[var(--td-text-color-secondary)]"
                                        display={
                                            <span className="min-w-0 truncate" title={plan.name}>
                                                {plan.name}
                                            </span>
                                        }
                                        quiet
                                        value={plan.name}
                                    />
                                ) : null}
                            </div>
                        </div>
                        {plan ? (
                            <Tag theme={plan.status === "active" ? "primary" : "default"} variant="light">
                                {plan.status === "active" ? "计划启用" : "计划停用"}
                            </Tag>
                        ) : (
                            <Tag theme="warning" variant="light">
                                计划不可用
                            </Tag>
                        )}
                    </div>

                    {plan ? (
                        <div className="grid min-w-0 grid-cols-2 gap-3 border-t border-[var(--td-border-level-1-color)] pt-3">
                            <QuotaWindow
                                credits={plan.fiveHourCredits}
                                label="每 5 小时"
                                mcpCalls={plan.fiveHourMcpCalls}
                                toolCalls={plan.fiveHourToolCalls}
                            />
                            <QuotaWindow
                                credits={plan.weeklyCredits}
                                label="每周"
                                mcpCalls={plan.weeklyMcpCalls}
                                toolCalls={plan.weeklyToolCalls}
                            />
                        </div>
                    ) : null}
                </div>

                <div className="flex min-w-0 flex-col gap-1.5 text-xs text-[var(--td-text-color-secondary)]">
                    <span className="flex min-w-0 items-center gap-1.5">
                        <TimeIcon className="shrink-0" size={14} />
                        <span className="min-w-0 truncate">
                            生效 {formatDateTime(assignment.effectiveFrom)}
                            <span className="mx-1.5 text-[var(--td-text-color-placeholder)]">·</span>
                            {assignment.effectiveTo ? `失效 ${formatDateTime(assignment.effectiveTo)}` : "长期有效"}
                        </span>
                    </span>
                    <span className="pl-5 text-[var(--td-text-color-placeholder)]">
                        每周额度从 {formatDateTime(assignment.weeklyAnchorAt)} 起算
                    </span>
                </div>

                <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-[var(--td-border-level-1-color)] pt-3 text-xs text-[var(--td-text-color-placeholder)]">
                    <span className="inline-flex items-center gap-1.5">
                        <UsergroupIcon size={14} />
                        组内成员自动继承
                    </span>
                    <span>更新于 {formatDateTime(assignment.updatedAt)}</span>
                </div>
            </article>
        </Card>
    );
}

function QuotaWindow({
    credits,
    label,
    mcpCalls,
    toolCalls,
}: {
    credits?: number;
    label: string;
    mcpCalls?: number;
    toolCalls?: number;
}) {
    return (
        <div className="flex min-w-0 flex-col gap-0.5">
            <span className="text-[11px] text-[var(--td-text-color-secondary)]">{label}</span>
            <span className="truncate font-medium tabular-nums" title={`${formatCredits(credits)} 积分`}>
                {formatCredits(credits)} 积分
            </span>
            <span className="truncate text-[11px] text-[var(--td-text-color-placeholder)]">
                {formatCount(toolCalls)} 次工具 · {formatCount(mcpCalls)} 次 MCP
            </span>
        </div>
    );
}
