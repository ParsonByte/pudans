"use client";

import type { ReactNode } from "react";

import { Typography } from "tdesign-react";

import { EmptyValue } from "./empty-value";

type CopyableIdentifierProps = {
    className?: string;
    display?: ReactNode;
    emptyText?: string;
    value?: null | string;
};

export function CopyableIdentifier({ className, display, emptyText, value }: CopyableIdentifierProps) {
    if (!value) {
        return <EmptyValue text={emptyText} />;
    }

    return (
        <Typography.Text
            className={`max-w-full ${display ? "inline-flex items-center" : "block font-mono text-xs"} ${className ?? ""}`}
            copyable={{ text: value }}
            ellipsis={display ? false : { row: 1, tooltipProps: { content: value } }}
        >
            {display ?? value}
        </Typography.Text>
    );
}
