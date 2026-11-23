"use client";

import { Button, Space } from "tdesign-react";

type IdentityDialogFooterProps = {
    cancelLabel?: string;
    disabled?: boolean;
    onCancel: () => void;
    onSubmit: () => void;
    pending?: boolean;
    submitLabel: string;
};

export function IdentityDialogFooter({
    cancelLabel = "取消",
    disabled = false,
    onCancel,
    onSubmit,
    pending = false,
    submitLabel,
}: IdentityDialogFooterProps) {
    return (
        <Space size={8}>
            <Button disabled={pending} onClick={onCancel} variant="outline">
                {cancelLabel}
            </Button>
            <Button disabled={disabled} loading={pending} onClick={onSubmit} theme="primary">
                {submitLabel}
            </Button>
        </Space>
    );
}
