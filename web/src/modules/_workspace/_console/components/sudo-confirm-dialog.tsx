"use client";

import { useCallback, useState } from "react";

import { Alert, Button, Input, RadioGroup } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { consoleErrorMessage } from "../composables/_errors";
import {
    SUDO_METHOD_LABELS,
    type SudoMethod,
    confirmSudo,
    getSudoStatus,
    sendSudoCode,
    sudoMethodLabel,
} from "../composables/_sudo";
import { IdentityDialogFooter } from "../modules/identity/components/identity-dialog-footer";

type SudoConfirmDialogProps = {
    methods: SudoMethod[];
    onClose: (confirmed: boolean) => void;
    open: boolean;
};

function SudoConfirmDialog({ methods, onClose, open }: SudoConfirmDialogProps) {
    const prompt = useMessagePrompt();
    const [method, setMethod] = useState(() => methods[0]?.name ?? "");
    const [code, setCode] = useState("");
    const [sending, setSending] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const selected = methods.find((item) => item.name === method);
    const hints = selected ? SUDO_METHOD_LABELS[selected.name] : undefined;
    const options = methods.map((item) => ({
        label: item.maskedTarget ? `${sudoMethodLabel(item.name)} · ${item.maskedTarget}` : sudoMethodLabel(item.name),
        value: item.name,
    }));

    const requestCode = async () => {
        if (!method) {
            return;
        }

        setSending(true);
        try {
            await sendSudoCode(method);
            prompt.success({ content: "验证码已发送。" });
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "验证码发送失败，请稍后重试。") });
        } finally {
            setSending(false);
        }
    };

    const submit = async () => {
        const value = code.trim();
        if (!method || !value) {
            return;
        }

        setConfirming(true);
        try {
            await confirmSudo(method, value);
            prompt.success({ content: "安全确认已完成。" });
            onClose(true);
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "安全确认失败，请重试。") });
            setConfirming(false);
        }
    };

    return (
        <GlobalDialog
            footer={
                methods.length === 0 ? (
                    <div className="flex justify-end">
                        <Button onClick={() => onClose(false)} variant="outline">
                            关闭
                        </Button>
                    </div>
                ) : (
                    <IdentityDialogFooter
                        disabled={!method || !code.trim()}
                        onCancel={() => onClose(false)}
                        onSubmit={() => void submit()}
                        pending={confirming}
                        submitLabel="确认操作"
                    />
                )
            }
            onClose={confirming ? () => undefined : () => onClose(false)}
            open={open}
            title="安全确认"
            width={480}
        >
            {methods.length === 0 ? (
                <Alert
                    message="当前账户没有可用的安全确认方式，无法执行该操作。请先绑定动态口令或设置登录密码。"
                    theme="error"
                />
            ) : (
                <div className="flex min-w-0 flex-col gap-4">
                    <Alert message="该操作属于敏感操作，完成确认后 30 分钟内无需重复确认。" theme="info" />
                    {options.length > 1 ? (
                        <RadioGroup
                            className="w-fit"
                            onChange={(value) => {
                                setMethod(value as string);
                                setCode("");
                            }}
                            options={options}
                            theme="button"
                            value={method}
                            variant="default-filled"
                        />
                    ) : (
                        <div className="text-sm">
                            <span style={{ color: "var(--td-text-color-secondary)" }}>确认方式：</span>
                            {sudoMethodLabel(method)}
                            {selected?.maskedTarget ? (
                                <span style={{ color: "var(--td-text-color-placeholder)" }}>
                                    {" "}
                                    · {selected.maskedTarget}
                                </span>
                            ) : null}
                        </div>
                    )}
                    <Input
                        autocomplete="one-time-code"
                        onChange={(value) => setCode(value)}
                        onEnter={() => void submit()}
                        placeholder={hints?.placeholder ?? "输入验证信息"}
                        type={selected?.name === "password" ? "password" : "text"}
                        value={code}
                    />
                    <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-xs" style={{ color: "var(--td-text-color-placeholder)" }}>
                            {hints?.hint ?? "按服务端提示输入验证信息。"}
                        </span>
                        {selected?.canSendCode ? (
                            <Button loading={sending} onClick={() => void requestCode()} size="small" variant="outline">
                                发送验证码
                            </Button>
                        ) : null}
                    </div>
                </div>
            )}
        </GlobalDialog>
    );
}

type SudoRequest = {
    methods: SudoMethod[];
    resolve: (confirmed: boolean) => void;
};

// 安全确认对话框的受控入口，调用方在请求成功后自行关闭。
export function useSudoConfirmation() {
    const [request, setRequest] = useState<SudoRequest>();

    const confirm = useCallback(async (requiredMethods: string[]) => {
        const status = await getSudoStatus();
        if (status.active) {
            return true;
        }

        const available = status.methods ?? [];
        const methods =
            requiredMethods.length > 0
                ? available.filter((method) => requiredMethods.includes(method.name))
                : available;

        return new Promise<boolean>((resolve) => {
            setRequest({ methods, resolve });
        });
    }, []);

    const dialog = request ? (
        <SudoConfirmDialog
            methods={request.methods}
            onClose={(confirmed) => {
                request.resolve(confirmed);
                setRequest(undefined);
            }}
            open
        />
    ) : null;

    return { confirm, dialog };
}
