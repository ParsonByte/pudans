"use client";

import { ModalForm, ProFormText } from "@ant-design/pro-components";

import { PASSWORD_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

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
    const resetPassword = useResetUserPassword();

    const submit = async (values: ResetPasswordFormValues) => {
        try {
            await resetPassword.mutateAsync({ name, password: values.password });
            prompt.success("密码已重置。");
            onSaved();
            return true;
        } catch {
            return false;
        }
    };

    return (
        <ModalForm<ResetPasswordFormValues>
            modalProps={{ destroyOnHidden: true, mask: { closable: !resetPassword.isPending } }}
            onFinish={submit}
            onOpenChange={(open) => {
                if (!open && !resetPassword.isPending) {
                    onClose();
                }
            }}
            open
            submitter={{
                searchConfig: { submitText: "重置密码" },
                submitButtonProps: { loading: resetPassword.isPending },
            }}
            title={`重置密码${displayName ? ` · ${displayName}` : ""}`}
            width={480}
        >
            <ProFormText.Password
                fieldProps={{ autoComplete: "new-password", placeholder: "至少 8 位" }}
                label="新密码"
                name="password"
                rules={[
                    { message: "请输入新密码。", required: true },
                    { message: "密码至少需要 8 个字符。", min: 8 },
                    { message: "密码不能超过 257 个字符。", max: 257 },
                    { message: "密码只能包含字母、数字和 !@#$%^&*.?。", pattern: PASSWORD_PATTERN },
                ]}
            />
            <ProFormText.Password
                dependencies={["password"]}
                fieldProps={{ autoComplete: "new-password", placeholder: "再次输入新密码" }}
                label="确认新密码"
                name="confirmPassword"
                rules={[
                    { message: "请再次输入新密码。", required: true },
                    ({ getFieldValue }) => ({
                        validator(_, value) {
                            if (!value || getFieldValue("password") === value) {
                                return Promise.resolve();
                            }

                            return Promise.reject(new Error("两次输入的密码不一致。"));
                        },
                    }),
                ]}
            />
        </ModalForm>
    );
}
