"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation } from "@tanstack/react-query";
import { Form, Input } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { PASSWORD_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { IdentityDialogFooter } from "@/modules/_workspace/_console/modules/identity/components/identity-dialog-footer";

type ChangePasswordFormValues = {
    confirmPassword: string;
    oldPassword?: string;
    password: string;
};

type ChangePasswordDialogProps = {
    onClose: () => void;
};

export function ChangePasswordDialog({ onClose }: ChangePasswordDialogProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const changePassword = useMutation({
        mutationFn: async (values: ChangePasswordFormValues) =>
            apiClient.user.changeMySelfPassword({
                changeMyPasswordRequest: {
                    oldPassword: values.oldPassword?.trim() || undefined,
                    password: values.password,
                },
            }),
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: () => {
            prompt.success({ content: "登录密码已更新。" });
            onClose();
        },
    });

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={onClose}
                    onSubmit={() => form.submit()}
                    pending={changePassword.isPending}
                    submitLabel="更新密码"
                />
            }
            onClose={() => {
                if (!changePassword.isPending) {
                    onClose();
                }
            }}
            open
            title="修改登录密码"
            width={480}
        >
            <Form
                form={form}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void changePassword.mutateAsync(form.getFieldsValue(true) as ChangePasswordFormValues);
                    }
                }}
            >
                <Form.FormItem label="当前密码" name="oldPassword">
                    <Input autocomplete="current-password" placeholder="首次设置密码时可留空" type="password" />
                </Form.FormItem>
                <Form.FormItem
                    label="新密码"
                    name="password"
                    rules={[
                        { message: "请输入新密码。", required: true },
                        { message: "密码至少需要 8 个字符。", min: 8 },
                        { message: "密码不能超过 257 个字符。", max: 257 },
                        { message: "密码只能包含字母、数字和 !@#$%^&*.?。", pattern: PASSWORD_PATTERN },
                    ]}
                >
                    <Input autocomplete="new-password" placeholder="至少 8 位" type="password" />
                </Form.FormItem>
                <Form.FormItem
                    label="确认新密码"
                    name="confirmPassword"
                    rules={[
                        { message: "请再次输入新密码。", required: true },
                        {
                            validator: (value) => {
                                if (!value || form.getFieldValue("password") === value) {
                                    return true;
                                }

                                return { message: "两次输入的密码不一致。", result: false };
                            },
                        },
                    ]}
                >
                    <Input autocomplete="new-password" placeholder="再次输入新密码" type="password" />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
