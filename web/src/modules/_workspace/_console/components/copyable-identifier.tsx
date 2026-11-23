"use client";

import { Typography } from "antd";

import { EmptyValue } from "./empty-value";

type CopyableIdentifierProps = {
    className?: string;
    value?: null | string;
};

export function CopyableIdentifier({ className, value }: CopyableIdentifierProps) {
    if (!value) {
        return <EmptyValue />;
    }

    return (
        <Typography.Text
            className={`block max-w-full font-mono text-xs ${className ?? ""}`}
            copyable={{ text: value }}
            ellipsis={{ tooltip: value }}
        >
            {value}
        </Typography.Text>
    );
}
