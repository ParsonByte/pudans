"use client";

import { useMemo } from "react";

const CJK_PATTERN = /[㐀-䶿一-鿿豈-﫿぀-ヿ가-힯]/g;

// 无本地分词器，按中日韩字符与非中日韩字符比例估算。
export function estimateTokenCount(text: string) {
    const cjkCount = text.match(CJK_PATTERN)?.length ?? 0;
    const otherCount = text.length - cjkCount;
    return Math.ceil(cjkCount + otherCount / 4);
}

export function useTokenCount(text: string) {
    return useMemo(() => estimateTokenCount(text), [text]);
}
