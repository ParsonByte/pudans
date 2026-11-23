"use client";

import { Space, Tag, Tooltip } from "tdesign-react";

import { EmptyValue } from "../../../../components/empty-value";
import { formatCount } from "../../../../composables/_format";

type SecretKeyTagsProps = {
    dataKeys?: string[];
    emptyText?: string;
    limit?: number;
    stringDataKeys?: string[];
};

type SecretTag = {
    key: string;
    kind: "data" | "string";
    name: string;
};

export function SecretKeyTags({ dataKeys, emptyText = "空密钥", limit = 6, stringDataKeys }: SecretKeyTagsProps) {
    const tags: SecretTag[] = [
        ...(stringDataKeys ?? []).map((name) => ({ key: `string:${name}`, kind: "string" as const, name })),
        ...(dataKeys ?? []).map((name) => ({ key: `data:${name}`, kind: "data" as const, name })),
    ];

    if (tags.length === 0) {
        return <EmptyValue text={emptyText} />;
    }

    const visible = tags.slice(0, limit);
    const hidden = tags.length - visible.length;

    return (
        <Space breakLine size={[4, 4]}>
            {visible.map((tag) => (
                <Tooltip content={`${tag.kind === "string" ? "文本键" : "二进制键"} · ${tag.name}`} key={tag.key}>
                    <Tag
                        className="max-w-40 truncate font-mono"
                        theme={tag.kind === "string" ? "primary" : "default"}
                        variant="light"
                    >
                        {tag.name}
                    </Tag>
                </Tooltip>
            ))}
            {hidden > 0 ? <Tag>+{formatCount(hidden)}</Tag> : null}
        </Space>
    );
}
