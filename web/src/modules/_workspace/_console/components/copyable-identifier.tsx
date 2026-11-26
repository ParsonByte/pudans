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

// 长标识只显示首尾并保留复制入口，短标识显示全文；复制图标始终在值右侧。
export function CopyableIdentifier({ className, display, emptyText, mask, quiet, value }: CopyableIdentifierProps) {
    if (!value) {
        return <EmptyValue text={emptyText} />;
    }

    const inline = Boolean(mask) || Boolean(display);
    const content = mask ? maskIdentifier(value) : (display ?? value);
    const layoutClassName = inline
        ? ["inline-flex max-w-full items-center gap-1", mask ? "font-mono text-xs" : ""].join(" ")
        : "block max-w-full font-mono text-xs";

    // 复制图标紧跟值右侧，单元格按值宽度参与布局，不会被挤成几个字符。
    return (
        <Typography.Text
            className={[layoutClassName, quiet ? quietCopyClassName : "", className ?? ""].join(" ")}
            copyable={{ text: value, tooltipProps: quiet ? { content: "复制" } : undefined }}
            ellipsis={inline ? false : { row: 1, tooltipProps: { content: value } }}
        >
            {content}
        </Typography.Text>
    );
}
