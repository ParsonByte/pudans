"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import { Form, Input, Skeleton, Typography } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { CopyableIdentifier } from "@/modules/_workspace/_console/components/copyable-identifier";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { IdentityDialogFooter } from "@/modules/_workspace/_console/modules/identity/components/identity-dialog-footer";

import { UC_TWO_FACTOR_QUERY_KEY } from "../../../composables/_queries";

type ConfigureTotpFormValues = {
    code: string;
    currentTotpCode?: string;
    password: string;
};

type ConfigureTotpDialogProps = {
    onClose: () => void;
    replaceExisting: boolean;
};

const TOTP_CODE_PATTERN = /^\d{6}$/;

export function ConfigureTotpDialog({ onClose, replaceExisting }: ConfigureTotpDialogProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const authLinkQuery = useQuery({
        queryKey: ["uc", "two-factor", "auth-link"],
        queryFn: async () => (await apiClient.twoFactorAuth.getTotpAuthLink()).data,
        gcTime: 0,
        staleTime: 0,
    });
    const configureTotp = useMutation({
        mutationFn: async (values: ConfigureTotpFormValues) => {
            const secret = authLinkQuery.data?.rawSecret;

            if (!secret) {
                throw new Error("动态口令密钥尚未准备好。");
            }

            return apiClient.twoFactorAuth.configurerTotp({
                totpRequest: {
                    code: values.code,
                    currentTotpCode: values.currentTotpCode?.trim() || undefined,
                    password: values.password,
                    secret,
                },
            });
        },
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error, "动态口令绑定失败，请检查验证码和密码。") });
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: UC_TWO_FACTOR_QUERY_KEY });
            prompt.success({ content: replaceExisting ? "动态口令已更换。" : "动态口令已绑定。" });
            onClose();
        },
    });

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={onClose}
                    onSubmit={() => form.submit()}
                    pending={configureTotp.isPending}
                    submitLabel={replaceExisting ? "更换验证器" : "绑定验证器"}
                />
            }
            onClose={() => {
                if (!configureTotp.isPending) {
                    onClose();
                }
            }}
            open
            title={replaceExisting ? "更换动态口令验证器" : "绑定动态口令验证器"}
            width={560}
        >
            {authLinkQuery.isPending ? <Skeleton theme="paragraph" /> : null}
            {authLinkQuery.error ? (
                <Typography.Text theme="error">{consoleErrorMessage(authLinkQuery.error)}</Typography.Text>
            ) : null}
            {authLinkQuery.data ? (
                <>
                    <div className="flex flex-col items-center gap-4 rounded-xl border border-[var(--td-component-border)] p-5">
                        <QRCodeSVG size={168} value={authLinkQuery.data.authLink} />
                        <div className="w-full text-center">
                            <Typography.Text className="text-xs" theme="secondary">
                                无法扫码时，可手动输入密钥
                            </Typography.Text>
                            <CopyableIdentifier
                                className="mt-2 justify-center"
                                display={
                                    <span className="font-mono text-xs break-all">{authLinkQuery.data.rawSecret}</span>
                                }
                                value={authLinkQuery.data.rawSecret}
                            />
                        </div>
                    </div>
                    <Form
                        className="mt-5"
                        form={form}
                        labelAlign="top"
                        onSubmit={({ validateResult }) => {
                            if (validateResult === true) {
                                void configureTotp.mutateAsync(form.getFieldsValue(true) as ConfigureTotpFormValues);
                            }
                        }}
                    >
                        {replaceExisting ? (
                            <Form.FormItem
                                label="当前动态口令"
                                name="currentTotpCode"
                                rules={[
                                    { message: "请输入当前验证器中的 6 位动态口令。", required: true },
                                    {
                                        message: "动态口令应为 6 位数字。",
                                        pattern: TOTP_CODE_PATTERN,
                                    },
                                ]}
                            >
                                <Input maxlength={6} placeholder="输入当前 6 位口令" type="tel" />
                            </Form.FormItem>
                        ) : null}
                        <Form.FormItem
                            label="新验证器口令"
                            name="code"
                            rules={[
                                { message: "请输入验证器中的 6 位动态口令。", required: true },
                                { message: "动态口令应为 6 位数字。", pattern: TOTP_CODE_PATTERN },
                            ]}
                        >
                            <Input maxlength={6} placeholder="输入 6 位动态口令" type="tel" />
                        </Form.FormItem>
                        <Form.FormItem
                            label="登录密码"
                            name="password"
                            rules={[{ message: "请输入当前登录密码。", required: true }]}
                        >
                            <Input autocomplete="current-password" type="password" />
                        </Form.FormItem>
                    </Form>
                </>
            ) : null}
        </GlobalDialog>
    );
}
