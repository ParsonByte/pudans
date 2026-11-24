"use client";

import type { PasswordRequest } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Form, Input } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import { UC_TWO_FACTOR_QUERY_KEY } from "../../../composables/_queries";

type TwoFactorToggleFormValues = {
    code: string;
    password: string;
};

type TwoFactorToggleDialogProps = {
    enabled: boolean;
    onClose: () => void;
};

type PasswordRequestWithTotp = PasswordRequest & { totpCode?: string };

const TOTP_CODE_PATTERN = /^\d{6}$/;

export function TwoFactorToggleDialog({ enabled, onClose }: TwoFactorToggleDialogProps) {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();
    const toggleTwoFactor = useMutation({
        mutationFn: async (values: TwoFactorToggleFormValues) => {
            const passwordRequest: PasswordRequestWithTotp = {
                password: values.password,
                totpCode: values.code,
            };

            return enabled
                ? apiClient.twoFactorAuth.disableTwoFactor({ passwordRequest })
                : apiClient.twoFactorAuth.enableTwoFactor({ passwordRequest });
        },
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error, "双重验证设置失败，请检查口令和密码。") });
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: UC_TWO_FACTOR_QUERY_KEY });
            prompt.success({ content: enabled ? "双重验证已停用。" : "双重验证已启用。" });
            onClose();
        },
    });

    return (
        <GlobalDialog
            confirmLoading={toggleTwoFactor.isPending}
            confirmText={enabled ? "停用双重验证" : "启用双重验证"}
            danger={enabled}
            onClose={() => {
                if (!toggleTwoFactor.isPending) {
                    onClose();
                }
            }}
            onConfirm={() => form.submit()}
            open
            title={enabled ? "停用双重验证" : "启用双重验证"}
            width={440}
        >
            <Form
                form={form}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void toggleTwoFactor.mutateAsync(form.getFieldsValue(true) as TwoFactorToggleFormValues);
                    }
                }}
            >
                <Form.FormItem
                    label="当前动态口令"
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
        </GlobalDialog>
    );
}
