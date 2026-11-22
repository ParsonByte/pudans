"use client";

import { useState } from "react";

import { Button, Form, Input } from "antd";
import { ArrowLeft, ShieldCheck } from "lucide-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import {
    completeLogin,
    isTwoFactorSessionExpired,
    twoFactorErrorMessage,
    verifyTwoFactorCode,
} from "../../../composables/_auth-login";

type TwoFactorFormValues = {
    code: string;
};

type TwoFactorFormProps = {
    redirectTo?: string;
    onBack: () => void;
};

export function TwoFactorForm({ onBack, redirectTo }: TwoFactorFormProps) {
    const prompt = useMessagePrompt();
    const [submitting, setSubmitting] = useState(false);

    const handleFinish = async (values: TwoFactorFormValues) => {
        setSubmitting(true);

        try {
            const session = await verifyTwoFactorCode(values.code.trim());
            completeLogin(session, redirectTo, () => prompt.error("当前账号无权访问控制台。"));
        } catch (error) {
            if (isTwoFactorSessionExpired(error)) {
                prompt.error("验证已过期，请重新登录。");
                onBack();
                return;
            }

            prompt.error(twoFactorErrorMessage(error));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Form<TwoFactorFormValues> layout="vertical" onFinish={handleFinish} requiredMark={false} size="large">
            <Form.Item
                label="动态验证码"
                name="code"
                rules={[
                    { message: "请输入动态验证码。", required: true },
                    { message: "请输入 6 位数字验证码。", pattern: /^\d{6}$/ },
                ]}
            >
                <Input
                    autoComplete="one-time-code"
                    autoFocus
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="请输入 6 位验证码"
                    prefix={<ShieldCheck className="size-4 text-neutral-400" />}
                />
            </Form.Item>
            <div className="mb-5 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-neutral-500 dark:text-neutral-400">
                <div className="flex items-center gap-1.5">
                    <ShieldCheck className="size-3.5 text-blue-500" />
                    验证码每 30 秒更新一次
                </div>
                <div className="flex items-center gap-1.5">
                    <ShieldCheck className="size-3.5 text-blue-500" />
                    请勿向他人透露验证码
                </div>
            </div>
            <Button block htmlType="submit" loading={submitting} type="primary">
                验证并继续
            </Button>
            <Button
                block
                className="mt-2"
                htmlType="button"
                icon={<ArrowLeft className="size-3.5" />}
                onClick={onBack}
                type="text"
            >
                返回登录
            </Button>
        </Form>
    );
}
