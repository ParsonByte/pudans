"use client";

import { Tag } from "tdesign-react";

type ResultTagProps = {
    result?: string;
};

export const RESULT_LABELS: Record<string, string> = {
    DENIED: "拒绝",
    FAILED: "失败",
    FAILURE: "失败",
    SUCCESS: "成功",
};

export function ResultTag({ result }: ResultTagProps) {
    const theme =
        result === "SUCCESS"
            ? "success"
            : result === "FAILURE" || result === "FAILED"
              ? "danger"
              : result === "DENIED"
                ? "warning"
                : "default";

    return (
        <Tag theme={theme} variant="light-outline">
            {result ? (RESULT_LABELS[result] ?? result) : "—"}
        </Tag>
    );
}
