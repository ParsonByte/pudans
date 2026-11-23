"use client";

import { useMemo, useState } from "react";

import { SearchIcon, SettingIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Input, Table, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import {
    DIRECT_ROLE_RULE_SOURCE,
    type RoleCatalogIndex,
    type RoleRuleCollection,
    type RoleRuleRow,
    resourceLabel,
    verbLabel,
    verbTheme,
} from "../composables/_role-permissions";

type RoleDataPermissionsProps = {
    collection: RoleRuleCollection;
    index: RoleCatalogIndex;
    onConfigureOperations?: () => void;
};

export function RoleDataPermissions({ collection, index, onConfigureOperations }: RoleDataPermissionsProps) {
    const [keyword, setKeyword] = useState("");
    const filteredRows = useMemo(() => {
        const query = keyword.trim().toLocaleLowerCase();
        if (!query) {
            return collection.rows;
        }
        return collection.rows.filter((row) => matchesRule(row, index, query));
    }, [collection.rows, index, keyword]);

    const columns: NonNullable<TableProps<RoleRuleRow>["columns"]> = [
        {
            cell: ({ row }) => <RuleResourceCell index={index} row={row} />,
            colKey: "resource",
            title: "资源",
            width: 170,
        },
        {
            cell: ({ row }) => <RuleVerbCell row={row} />,
            colKey: "verbs",
            title: "允许动作",
            width: 130,
        },
        {
            cell: ({ row }) => <RuleScopeCell row={row} />,
            colKey: "scope",
            title: "范围",
            width: 120,
        },
        {
            cell: ({ row }) => <RuleSourceCell row={row} />,
            colKey: "sources",
            title: "来源",
        },
    ];

    return (
        <Card
            actions={
                onConfigureOperations ? (
                    <Button icon={<SettingIcon />} onClick={onConfigureOperations} size="small" variant="outline">
                        调整操作权限
                    </Button>
                ) : null
            }
            header={<div className="text-base font-semibold">数据权限规则</div>}
            headerBordered
        >
            <div className="flex flex-col gap-4">
                <Input
                    className="w-full sm:max-w-xs"
                    clearable
                    onChange={(value) => setKeyword(String(value))}
                    placeholder="搜索资源、动作或来源"
                    prefixIcon={<SearchIcon />}
                    value={keyword}
                />
                <Table<RoleRuleRow>
                    columns={columns}
                    data={filteredRows}
                    empty={<Empty description={keyword ? "没有匹配的数据权限" : "当前角色还没有数据权限规则"} />}
                    rowKey="key"
                />
            </div>
        </Card>
    );
}

function RuleResourceCell({ index, row }: { index: RoleCatalogIndex; row: RoleRuleRow }) {
    const resources = row.rule.resources ?? [];
    const urls = row.rule.nonResourceURLs ?? [];
    const labels =
        resources.length > 0
            ? resources.map((resource) => resourceLabel(index, resource))
            : urls.length > 0
              ? ["非资源 URL"]
              : ["未指定资源"];
    const raw = [...resources, ...urls].join(", ");

    return (
        <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-1">
                {labels.map((label, index) => (
                    <Tag key={`${label}:${index}`} variant="light">
                        {label}
                    </Tag>
                ))}
                {row.direct ? (
                    <Tag theme="primary" variant="light">
                        直接规则
                    </Tag>
                ) : null}
            </div>
            <Tooltip content={raw}>
                <div className="mt-1 truncate font-mono text-[10px] text-[var(--td-text-color-placeholder)]">
                    {raw || "未填写"}
                </div>
            </Tooltip>
        </div>
    );
}

function RuleVerbCell({ row }: { row: RoleRuleRow }) {
    const verbs = row.rule.verbs ?? [];

    return (
        <div className="flex flex-wrap items-center gap-1">
            {verbs.length > 0 ? (
                verbs.map((verb) => (
                    <Tag key={verb} theme={verbTheme(verb)} variant="light">
                        {verbLabel(verb)}
                    </Tag>
                ))
            ) : (
                <span className="text-xs text-[var(--td-text-color-placeholder)]">未填写</span>
            )}
        </div>
    );
}

function RuleScopeCell({ row }: { row: RoleRuleRow }) {
    const resourceNames = row.rule.resourceNames ?? [];
    const urls = row.rule.nonResourceURLs ?? [];
    const values = [...resourceNames, ...urls];

    if (values.length === 0) {
        return <span className="text-xs text-[var(--td-text-color-secondary)]">全部资源名称</span>;
    }

    return (
        <div className="flex flex-wrap items-center gap-1">
            {values.map((value, index) => (
                <Tag className="font-mono" key={`${value}:${index}`} variant="light">
                    {value}
                </Tag>
            ))}
        </div>
    );
}

function RuleSourceCell({ row }: { row: RoleRuleRow }) {
    const visibleSources = row.sources.slice(0, 2);
    const hiddenCount = Math.max(0, row.sources.length - visibleSources.length);

    return (
        <Tooltip content={row.sources.join("、")}>
            <div className="flex flex-wrap items-center gap-1">
                {visibleSources.map((source) => (
                    <Tag
                        key={source}
                        theme={source === DIRECT_ROLE_RULE_SOURCE ? "primary" : "default"}
                        variant="light"
                    >
                        {source}
                    </Tag>
                ))}
                {hiddenCount > 0 ? <Tag variant="light">+{hiddenCount}</Tag> : null}
            </div>
        </Tooltip>
    );
}

function matchesRule(row: RoleRuleRow, index: RoleCatalogIndex, query: string): boolean {
    const resources = row.rule.resources ?? [];
    const urls = row.rule.nonResourceURLs ?? [];
    const verbs = row.rule.verbs ?? [];

    return [
        row.rule.apiGroups,
        resources,
        resources.map((resource) => resourceLabel(index, resource)),
        urls,
        row.rule.resourceNames,
        verbs,
        verbs.map(verbLabel),
        row.sources,
    ]
        .flatMap((values) => values ?? [])
        .join(" ")
        .toLocaleLowerCase()
        .includes(query);
}
