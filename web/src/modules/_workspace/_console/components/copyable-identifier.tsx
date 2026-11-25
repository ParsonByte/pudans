"use client";

import type { ReactNode } from "react";

import { Typography } from "tdesign-react";

import { maskIdentifier } from "../composables/_format";
import { EmptyValue } from "./empty-value";

type CopyableIdentifierProps = {
    className?: string;
    display?: ReactNode;
    emptyText?: string;
    mask?: boolean;
    quiet?: boolean;
    value?: null | string;
};

// quiet 模式把复制入口压成 20px 的图标，默认隐藏、悬停值区才出现，避免挤压窄卡片里的编号
const quietCopyClassName = [
    "[--td-comp-size-m:20px]",
    "[&_button]:shrink-0 [&_button]:opacity-0 [&_button]:transition-opacity",
    "hover:[&_button]:opacity-100 [&_button:focus-visible]:opacity-100 max-lg:[&_button]:opacity-100",
].join(" ");

// mask 打开后长标识只显示首尾，复制仍使用完整值。
export function CopyableIdentifier({ className, display, emptyText, mask, quiet, value }: CopyableIdentifierProps) {
    if (!value) {
        return <EmptyValue text={emptyText} />;
    }

    const inline = Boolean(mask) || Boolean(display);
    const content = mask ? maskIdentifier(value) : (display ?? value);

    // 复制入口固定排在值的右侧，长值在剩余宽度内截断。
    return (
        <span
            className={`inline-flex max-w-full min-w-0 items-center gap-1 align-middle ${mask || !inline ? "font-mono text-xs" : ""} ${quiet ? quietCopyClassName : ""} ${className ?? ""}`}
        >
            <Typography.Text
                className={`min-w-0 ${inline ? "inline-flex items-center" : ""}`}
                copyable={{ text: value, tooltipProps: quiet ? { content: "复制" } : undefined }}
                ellipsis={inline ? false : { row: 1, tooltipProps: { content: value } }}
            >
                {content}
            </Typography.Text>
        </span>
    );
}
