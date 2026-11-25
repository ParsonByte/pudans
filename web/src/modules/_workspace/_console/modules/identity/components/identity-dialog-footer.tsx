"use client";

import type { ReactNode } from "react";

import { Button, Space } from "tdesign-react";

type IdentityDialogFooterProps = {
    cancelLabel?: string;
    disabled?: boolean;
    extra?: ReactNode;
    onCancel: () => void;
    onSubmit: () => void;
    pending?: boolean;
    submitLabel: string;
    submitTheme?: "danger" | "primary";
};

export function IdentityDialogFooter({
    cancelLabel = "取消",
    disabled = false,
    extra,
    onCancel,
    onSubmit,
    pending = false,
    submitLabel,
    submitTheme = "primary",
}: IdentityDialogFooterProps) {
    return (
        <Space size={8}>
            <Button disabled={pending} onClick={onCancel} variant="outline">
                {cancelLabel}
            </Button>
            {extra}
            <Button disabled={disabled} loading={pending} onClick={onSubmit} theme={submitTheme}>
                {submitLabel}
            </Button>
        </Space>
    );
}
