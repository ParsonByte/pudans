"use client";

import { Form, Input } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { PASSWORD_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import { useResetUserPassword } from "../composables/_user-actions";

type ResetPasswordFormValues = {
    confirmPassword: string;
    password: string;
};

type ResetUserPasswordModalProps = {
    displayName?: string;
    name: string;
    onClose: () => void;
    onSaved: () => void;
};

export function ResetUserPasswordModal({ displayName, name, onClose, onSaved }: ResetUserPasswordModalProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const resetPassword = useResetUserPassword();

    const submit = async () => {
        const values = form.getFieldsValue(true) as ResetPasswordFormValues;

        try {
            await resetPassword.mutateAsync({ name, password: values.password });
            prompt.success({ content: "密码已重置。" });
            onSaved();
        } catch {
            return;
        }
    };
    const close = () => {
        if (!resetPassword.isPending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={resetPassword.isPending}
                    submitLabel="重置密码"
                />
            }
            onClose={close}
            open
            title={`重置密码${displayName ? ` · ${displayName}` : ""}`}
            width={480}
        >
            <Form
                form={form}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
            >
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
