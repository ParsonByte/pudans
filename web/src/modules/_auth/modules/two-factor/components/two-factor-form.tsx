"use client";

import { useState } from "react";

import { ArrowLeftIcon, SecuredIcon } from "tdesign-icons-react";
import { Button, Form, Input } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import {
    completeLogin,
    isTwoFactorSessionExpired,
    twoFactorErrorMessage,
    verifyTwoFactorCode,
} from "../../../composables/_auth-login";

const { FormItem } = Form;

type TwoFactorFormValues = {
    code: string;
};

type TwoFactorFormProps = {
    redirectTo?: string;
    onBack: () => void;
};

export function TwoFactorForm({ onBack, redirectTo }: TwoFactorFormProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);

    const handleFinish = async (values: TwoFactorFormValues) => {
        setSubmitting(true);

        try {
            const session = await verifyTwoFactorCode(values.code.trim());
            completeLogin(session, redirectTo, () => prompt.error({ content: "当前账号无权访问控制台。" }));
        } catch (error) {
            if (isTwoFactorSessionExpired(error)) {
                prompt.error({ content: "验证已过期，请重新登录。" });
                onBack();
                return;
            }

            prompt.error({ content: twoFactorErrorMessage(error) });
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Form
            form={form}
            labelAlign="top"
            layout="vertical"
            onSubmit={({ validateResult }) => {
                if (validateResult !== true) {
                    return;
                }

                void handleFinish({ code: String(form.getFieldValue("code") ?? "").trim() });
            }}
            requiredMark={false}
        >
            <FormItem
                label="动态验证码"
                name="code"
                rules={[
                    { message: "请输入动态验证码。", required: true },
                    { message: "请输入 6 位数字验证码。", pattern: /^\d{6}$/ },
                ]}
            >
                <Input
                    autocomplete="one-time-code"
                    autofocus
                    maxlength={6}
                    placeholder="请输入 6 位验证码"
                    prefixIcon={<SecuredIcon />}
                    size="large"
                    type="tel"
                />
            </FormItem>
            <div className="mb-5 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-[var(--td-text-color-secondary)]">
                <div className="flex items-center gap-1.5">
                    <SecuredIcon className="text-[var(--td-brand-color)]" size={14} />
                    验证码每 30 秒更新一次
                </div>
                <div className="flex items-center gap-1.5">
                    <SecuredIcon className="text-[var(--td-brand-color)]" size={14} />
                    请勿向他人透露验证码
                </div>
            </div>
            <Button block loading={submitting} size="large" theme="primary" type="submit">
                验证并继续
            </Button>
            <Button block className="mt-2" icon={<ArrowLeftIcon />} onClick={onBack} type="button" variant="text">
                返回登录
            </Button>
        </Form>
    );
}
