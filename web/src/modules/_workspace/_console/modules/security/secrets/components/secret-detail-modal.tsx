"use client";

import type { SecretCatalogItem } from "@pudans-dev/api-client";
import { Button, Tag } from "tdesign-react";

import { GlobalDialog, useDesignTokens } from "@/components/ui";

import { DescriptionGrid } from "../../../../components/description-grid";
import { EmptyValue } from "../../../../components/empty-value";
import { formatCount, formatDateTime } from "../../../../composables/_format";
import { secretEntryCount, secretTypeLabel } from "../composables/_secrets";

type SecretDetailModalProps = {
    item?: SecretCatalogItem;
    onClose: () => void;
};

export function SecretDetailModal({ item, onClose }: SecretDetailModalProps) {
    return (
        <GlobalDialog
            footer={
                <div className="flex justify-end">
                    <Button theme="primary" onClick={onClose}>
                        关闭
                    </Button>
                </div>
            }
            onClose={onClose}
            open={Boolean(item)}
            title={
                <span className="flex flex-wrap items-center gap-2">
                    密钥详情 · {item?.name ?? "未命名"}
                    <Tag theme="primary" variant="light">
                        只写不读
                    </Tag>
                </span>
            }
            width={780}
        >
            {item ? (
                <div className="flex min-w-0 flex-col gap-5">
                    <DescriptionGrid
                        columns={4}
                        items={[
                            { label: "名称", value: item.name ?? "—" },
                            { label: "类型", value: secretTypeLabel(item.type) },
                            {
                                label: "状态",
                                value:
                                    secretEntryCount(item) === 0 ? (
                                        <Tag theme="warning" variant="light">
                                            空密钥
                                        </Tag>
                                    ) : (
                                        <Tag theme="success" variant="light">
                                            已配置
                                        </Tag>
                                    ),
                            },
                            { label: "版本", value: `v${formatCount(item.version)}` },
                            { label: "键值条目", value: formatCount(secretEntryCount(item)) },
                            { label: "文本键", value: formatCount(item.stringDataKeys?.length) },
                            { label: "二进制键", value: formatCount(item.dataKeys?.length) },
                            { label: "创建时间", value: formatDateTime(item.creationTimestamp) },
                        ]}
                    />
                    <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2">
                        <KeyGroup
                            emptyText="没有文本键"
                            keys={item.stringDataKeys}
                            theme="primary"
                            title="文本键 stringData"
                        />
                        <KeyGroup emptyText="没有二进制键" keys={item.dataKeys} theme="default" title="二进制键 data" />
                    </div>
                    <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2">
                        <AttributeGroup attributes={item.labels} title="标签" />
                        <AttributeGroup attributes={item.annotations} title="注解" />
                    </div>
                </div>
            ) : null}
        </GlobalDialog>
    );
}

type KeyGroupProps = {
    emptyText: string;
    keys?: string[];
    theme: "default" | "primary";
    title: string;
};

function KeyGroup({ emptyText, keys, theme, title }: KeyGroupProps) {
    const token = useDesignTokens();

    return (
        <section
            className="min-w-0 rounded-2xl border p-4"
            style={{ background: token.colorFillQuaternary, borderColor: token.colorBorderSecondary }}
        >
            <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-medium" style={{ color: token.colorText }}>
                    {title}
                </span>
                <span className="text-xs tabular-nums" style={{ color: token.colorTextTertiary }}>
                    {formatCount(keys?.length)}
                </span>
            </div>
            <div className="mt-3 min-w-0">
                {keys?.length ? (
                    <div className="flex flex-wrap gap-1.5">
                        {keys.map((key) => (
                            <Tag className="max-w-full truncate font-mono" key={key} theme={theme} variant="light">
                                {key}
                            </Tag>
                        ))}
                    </div>
                ) : (
                    <EmptyValue text={emptyText} />
                )}
            </div>
        </section>
    );
}

type AttributeGroupProps = {
    attributes?: { [key: string]: string };
    title: string;
};

function AttributeGroup({ attributes, title }: AttributeGroupProps) {
    const token = useDesignTokens();
    const entries = Object.entries(attributes ?? {});

    return (
        <section
            className="min-w-0 rounded-2xl border p-4"
            style={{ background: token.colorFillQuaternary, borderColor: token.colorBorderSecondary }}
        >
            <div className="text-sm font-medium" style={{ color: token.colorText }}>
                {title}
            </div>
            <div className="mt-3 min-w-0">
                {entries.length > 0 ? (
                    <div className="flex flex-wrap gap-1.5">
                        {entries.map(([key, value]) => (
                            <Tag className="max-w-full whitespace-normal" key={key} variant="light">
                                <span className="font-mono">{key}</span>
                                {value ? `=${value}` : ""}
                            </Tag>
                        ))}
                    </div>
                ) : (
                    <EmptyValue text={`暂无${title}`} />
                )}
            </div>
        </section>
    );
}
