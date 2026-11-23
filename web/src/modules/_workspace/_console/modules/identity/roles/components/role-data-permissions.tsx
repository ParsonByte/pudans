"use client";

import { useMemo, useState } from "react";

import { ProCard } from "@ant-design/pro-components";
import { Button, Empty, Input, Table, type TableProps, Tag, Tooltip, theme } from "antd";
import { Search, SlidersHorizontal } from "lucide-react";

import {
    DIRECT_ROLE_RULE_SOURCE,
    type RoleCatalogIndex,
    type RoleRuleCollection,
    type RoleRuleRow,
    resourceLabel,
    verbColor,
    verbLabel,
} from "../composables/_role-permissions";

type RoleDataPermissionsProps = {
    collection: RoleRuleCollection;
    index: RoleCatalogIndex;
    onConfigureOperations?: () => void;
};

export function RoleDataPermissions({ collection, index, onConfigureOperations }: RoleDataPermissionsProps) {
    const { token } = theme.useToken();
    const [keyword, setKeyword] = useState("");
    const filteredRows = useMemo(() => {
        const query = keyword.trim().toLocaleLowerCase();
        if (!query) {
            return collection.rows;
        }
        return collection.rows.filter((row) => matchesRule(row, index, query));
    }, [collection.rows, index, keyword]);

    const columns: TableProps<RoleRuleRow>["columns"] = [
        {
            key: "resource",
            render: (_, row) => <RuleResourceCell index={index} row={row} />,
            title: "资源",
            width: 170,
        },
        {
            key: "verbs",
            render: (_, row) => <RuleVerbCell row={row} />,
            title: "允许动作",
            width: 130,
        },
        {
            key: "scope",
            render: (_, row) => <RuleScopeCell row={row} />,
            title: "范围",
            width: 120,
        },
        {
            key: "sources",
            render: (_, row) => <RuleSourceCell row={row} />,
            title: "来源",
        },
    ];

    return (
        <ProCard
            className="!rounded-2xl"
            headerBordered
            styles={{
                body: { padding: 20 },
                header: { paddingBlock: 14, paddingInline: 20 },
                title: {
                    display: "block",
                    overflow: "visible",
                    textOverflow: "clip",
                    whiteSpace: "normal",
                },
            }}
            extra={
                onConfigureOperations ? (
                    <Button icon={<SlidersHorizontal size={14} />} onClick={onConfigureOperations}>
                        调整操作权限
                    </Button>
                ) : null
            }
            title={
                <div>
                    <div className="text-base font-semibold">数据权限规则</div>
                    <div className="mt-1 text-xs font-normal" style={{ color: token.colorTextSecondary }}>
                        由菜单与操作权限自动派生，不能在此新增规则。
                    </div>
                </div>
            }
        >
            <div className="flex flex-col gap-4">
                <Input
                    allowClear
                    className="w-full sm:max-w-xs"
                    onChange={(event) => setKeyword(event.target.value)}
                    placeholder="搜索资源、动作或来源"
                    prefix={<Search className="text-neutral-400" size={14} />}
                    value={keyword}
                />

                <Table<RoleRuleRow>
                    columns={columns}
                    dataSource={filteredRows}
                    locale={{
                        emptyText: (
                            <Empty
                                description={keyword ? "没有匹配的数据权限" : "当前角色还没有数据权限规则"}
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                            />
                        ),
                    }}
                    pagination={false}
                    rowKey={(row) => row.key}
                    scroll={{ x: 520 }}
                />
            </div>
        </ProCard>
    );
}

function RuleResourceCell({ index, row }: { index: RoleCatalogIndex; row: RoleRuleRow }) {
    const { token } = theme.useToken();
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
                    <Tag className="!m-0" key={`${label}:${index}`}>
                        {label}
                    </Tag>
                ))}
                {row.direct ? (
                    <Tag className="!m-0" color="blue">
                        直接规则
                    </Tag>
                ) : null}
            </div>
            <Tooltip title={raw || undefined}>
                <div className="mt-1 truncate font-mono text-[10px]" style={{ color: token.colorTextTertiary }}>
                    {raw || "—"}
                </div>
            </Tooltip>
        </div>
    );
}

function RuleVerbCell({ row }: { row: RoleRuleRow }) {
    const { token } = theme.useToken();
    const verbs = row.rule.verbs ?? [];

    return (
        <div className="flex flex-wrap items-center gap-1">
            {verbs.length > 0 ? (
                verbs.map((verb) => (
                    <Tag className="!m-0" color={verbColor(verb)} key={verb}>
                        {verbLabel(verb)}
                    </Tag>
                ))
            ) : (
                <span className="text-xs" style={{ color: token.colorTextTertiary }}>
                    未指定
                </span>
            )}
        </div>
    );
}

function RuleScopeCell({ row }: { row: RoleRuleRow }) {
    const { token } = theme.useToken();
    const resourceNames = row.rule.resourceNames ?? [];
    const urls = row.rule.nonResourceURLs ?? [];
    const values = [...resourceNames, ...urls];

    if (values.length === 0) {
        return (
            <span className="text-xs" style={{ color: token.colorTextSecondary }}>
                全部资源名称
            </span>
        );
    }

    return (
        <div className="flex flex-wrap items-center gap-1">
            {values.map((value, index) => (
                <Tag className="!m-0 font-mono" key={`${value}:${index}`}>
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
        <Tooltip title={row.sources.join("、")}>
            <div className="flex flex-wrap items-center gap-1">
                {visibleSources.map((source) => (
                    <Tag className="!m-0" color={source === DIRECT_ROLE_RULE_SOURCE ? "blue" : undefined} key={source}>
                        {source}
                    </Tag>
                ))}
                {hiddenCount > 0 ? <Tag className="!m-0">+{hiddenCount}</Tag> : null}
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
