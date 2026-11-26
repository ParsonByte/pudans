"use client";

import type { PersonalAccessToken } from "@pudans-dev/api-client";
import { Tag } from "tdesign-react";

import { GlobalDialog, useDesignTokens } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { DescriptionGrid } from "../../../../components/description-grid";
import { EmptyValue } from "../../../../components/empty-value";
import { formatCount, formatDateTime } from "../../../../composables/_format";
import { patDescription, patDisplayName, patRoles } from "../composables/_pat";
import { PatRoleTags } from "./pat-role-tags";
import { PatStatusTag } from "./pat-status-tag";

type PatDetailModalProps = {
    onClose: () => void;
    pat?: PersonalAccessToken;
};

export function PatDetailModal({ onClose, pat }: PatDetailModalProps) {
    const token = useDesignTokens();
    const description = patDescription(pat);
    const roles = patRoles(pat);

    return (
        <GlobalDialog
            footer={false}
            onClose={onClose}
            open={Boolean(pat)}
            title={
                <span className="flex flex-wrap items-center gap-2">
                    个人访问令牌详情 · {patDisplayName(pat)}
                    <Tag theme="primary" variant="light">
                        仅创建时显示明文
                    </Tag>
                </span>
            }
            width={780}
        >
            {pat ? (
                <div className="flex min-w-0 flex-col gap-5">
                    <DescriptionGrid
                        columns={4}
                        items={[
                            { label: "名称", value: patDisplayName(pat) },
                            { label: "状态", value: <PatStatusTag pat={pat} /> },
                            { label: "资源名", value: <CopyableIdentifier mask value={pat.metadata?.name} /> },
                            { label: "所属用户", value: pat.spec?.username ?? "—" },
                            { label: "有效期至", value: formatDateTime(pat.spec?.expiresAt) },
                            {
                                label: "最后使用",
                                value: pat.spec?.lastUsed ? (
                                    formatDateTime(pat.spec.lastUsed)
                                ) : (
                                    <EmptyValue text="从未使用" />
                                ),
                            },
                            { label: "创建时间", value: formatDateTime(pat.metadata?.creationTimestamp) },
                            { label: "撤销时间", value: formatDateTime(pat.spec?.revokesAt) },
                        ]}
                    />
                    <div className="text-xs leading-5" style={{ color: token.colorTextTertiary }}>
                        明文令牌只在创建成功时返回一次，平台仅保存令牌摘要；之后只能查看元数据、撤销或删除。
                    </div>
                    <section
                        className="min-w-0 rounded-2xl border p-4"
                        style={{ background: token.colorFillQuaternary, borderColor: token.colorBorderSecondary }}
                    >
                        <div className="text-sm font-medium" style={{ color: token.colorText }}>
                            说明
                        </div>
                        <div className="mt-3 min-w-0">
                            {description ? (
                                <p className="text-sm break-words whitespace-pre-wrap">{description}</p>
                            ) : (
                                <EmptyValue text="未填写说明" />
                            )}
                        </div>
                    </section>
                    <section
                        className="min-w-0 rounded-2xl border p-4"
                        style={{ background: token.colorFillQuaternary, borderColor: token.colorBorderSecondary }}
                    >
                        <div className="flex items-center justify-between gap-3">
                            <span className="text-sm font-medium" style={{ color: token.colorText }}>
                                角色
                            </span>
                            <span className="text-xs tabular-nums" style={{ color: token.colorTextTertiary }}>
                                {formatCount(roles.length)}
                            </span>
                        </div>
                        <div className="mt-3 min-w-0">
                            <PatRoleTags roles={roles} />
                        </div>
                    </section>
                </div>
            ) : null}
        </GlobalDialog>
    );
}
