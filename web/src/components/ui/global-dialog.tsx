"use client";

import type { CSSProperties, ReactNode } from "react";

import { CloseIcon } from "tdesign-icons-react";
import { Button, Dialog, DialogPlugin } from "tdesign-react";

const DIALOG_WIDTH = "min(640px, calc(100vw - 32px))";

type GlobalDialogProps = {
    cancelText?: ReactNode;
    children: ReactNode;
    className?: string;
    confirmLoading?: boolean;
    confirmText?: ReactNode;
    danger?: boolean;
    destroyOnClose?: boolean;
    footer?: ReactNode;
    onClose: () => void;
    onConfirm?: () => void;
    open: boolean;
    style?: CSSProperties;
    title: ReactNode;
    width?: number | string;
};

function DialogCloseButton({ disabled = false, onClick }: { disabled?: boolean; onClick?: () => void }) {
    return (
        <Button
            aria-label="关闭对话框"
            className="global-dialog__close"
            disabled={disabled}
            icon={<CloseIcon />}
            onClick={onClick}
            shape="square"
            type="button"
            variant="text"
        />
    );
}

export function GlobalDialog({
    cancelText = "取消",
    children,
    className,
    confirmLoading,
    confirmText = "确认",
    danger = false,
    destroyOnClose = true,
    footer,
    onClose,
    onConfirm,
    open,
    style,
    title,
    width = DIALOG_WIDTH,
}: GlobalDialogProps) {
    const resolvedFooter = footer === undefined && onConfirm ? undefined : footer === undefined ? false : footer;
    const headerless = title === false;

    return (
        <Dialog
            attach="body"
            cancelBtn={onConfirm ? cancelText : undefined}
            closeBtn={headerless ? null : <DialogCloseButton disabled={confirmLoading} />}
            closeOnEscKeydown={!confirmLoading}
            closeOnOverlayClick={false}
            confirmBtn={onConfirm ? confirmText : undefined}
            confirmLoading={confirmLoading}
            destroyOnClose={destroyOnClose}
            dialogClassName={className ? `global-dialog ${className}` : "global-dialog"}
            footer={resolvedFooter}
            header={headerless ? false : title}
            onClose={confirmLoading ? undefined : onClose}
            onConfirm={onConfirm}
            placement="center"
            style={style}
            theme={danger ? "danger" : "default"}
            visible={open}
            width={width}
        >
            {headerless ? (
                <div className="global-dialog__headerless">
                    <div className="global-dialog__floating-close">
                        <DialogCloseButton disabled={confirmLoading} onClick={confirmLoading ? undefined : onClose} />
                    </div>
                    {children}
                </div>
            ) : (
                children
            )}
        </Dialog>
    );
}

type ConfirmGlobalDialogOptions = {
    cancelText?: string;
    confirmText?: string;
    content: ReactNode;
    danger?: boolean;
    onCancel?: () => void;
    onConfirm: () => void;
    title: ReactNode;
};

type DialogInstance = ReturnType<typeof DialogPlugin.confirm>;

export function confirmGlobalDialog({
    cancelText = "取消",
    confirmText = "确认",
    content,
    danger = false,
    onCancel,
    onConfirm,
    title,
}: ConfirmGlobalDialogOptions) {
    const dialog: DialogInstance = DialogPlugin.confirm({
        attach: "body",
        cancelBtn: cancelText,
        closeBtn: <DialogCloseButton />,
        closeOnEscKeydown: true,
        closeOnOverlayClick: false,
        confirmBtn: confirmText,
        body: content,
        destroyOnClose: true,
        dialogClassName: "global-dialog",
        header: title,
        onClose: () => {
            dialog.hide();
        },
        placement: "center",
        theme: danger ? "danger" : "default",
        width: DIALOG_WIDTH,
        onCancel: () => {
            onCancel?.();
            dialog.hide();
        },
        onConfirm: () => {
            onConfirm();
            dialog.hide();
        },
    });

    return dialog;
}
