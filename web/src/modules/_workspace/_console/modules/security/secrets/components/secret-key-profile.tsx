"use client";

import type { SecretCatalogItem } from "@pudans-dev/api-client";

import { RankedBarList } from "../../../../components/ranked-bar-list";
import { formatCount } from "../../../../composables/_format";
import { secretEntryCount, secretTypeLabel } from "../composables/_secrets";

type SecretKeyProfileProps = {
    items?: SecretCatalogItem[];
};

export function SecretKeyProfile({ items }: SecretKeyProfileProps) {
    const ranked = [...(items ?? [])]
        .sort((left, right) => secretEntryCount(right) - secretEntryCount(left))
        .slice(0, 8)
        .map((item) => ({
            key: item.name ?? `${item.type}-${item.creationTimestamp}`,
            label: item.name ?? "未命名密钥",
            labelTitle: item.name,
            meta: (
                <>
                    <span>{secretTypeLabel(item.type)}</span>
                    <span>文本 {formatCount(item.stringDataKeys?.length)}</span>
                    <span>二进制 {formatCount(item.dataKeys?.length)}</span>
                </>
            ),
            value: secretEntryCount(item),
            valueLabel: "个键",
        }));

    return <RankedBarList emptyText="当前筛选结果中没有密钥" items={ranked} />;
}
