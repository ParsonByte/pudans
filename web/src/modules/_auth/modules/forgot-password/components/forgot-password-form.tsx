"use client";

import { useEffect, useState } from "react";

import Link from "next/link";

import type { CaptchaChallenge } from "@pudans-dev/api-client";
import { ArrowLeftIcon, CheckCircleIcon, MailIcon, SecuredIcon } from "tdesign-icons-react";
import { Button, Form, Input, Steps, Typography } from "tdesign-react";

import { PASSWORD_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { AuthFormCard } from "../../../components/auth-form-card";
import { CaptchaField } from "../../../components/captcha-field";
import { getCaptchaChallenge } from "../../../composables/_auth-captcha";
import {
    isRecoveryCaptchaError,
    prepareRecovery,
    recoveryErrorMessage,
    resetPassword,
    sendRecoveryCode,
} from "../../../composables/_auth-recovery";

const { FormItem } = Form;

type RecoveryValues = {
    email: string;
    code: string;
    newPassword: string;
    confirmPassword: string;
};

export function ForgotPasswordForm() {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [step, setStep] = useState(0);
    const [codeSent, setCodeSent] = useState(false);
    const [codeSending, setCodeSending] = useState(false);
    const [captcha, setCaptcha] = useState<CaptchaChallenge | null>(null);
    const [captchaAnswer, setCaptchaAnswer] = useState("");
    const [captchaLoading, setCaptchaLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        void prepareRecovery().catch(() => undefined);
    }, []);

    const loadCaptcha = async () => {
        setCaptchaLoading(true);

        try {
            const challenge = await getCaptchaChallenge();

            if (challenge.challengeId && challenge.image) {
                setCaptcha(challenge);
                setCaptchaAnswer("");
            } else {
                setCaptcha(null);
            }
        } catch {
            prompt.error({ content: "验证码加载失败，请稍后重试。" });
        } finally {
            setCaptchaLoading(false);
        }
    };

    const captchaPayload = () =>
        captcha ? { captchaChallengeId: captcha.challengeId, captchaAnswer: captchaAnswer.trim() } : {};

    const sendCode = async () => {
        try {
            const result = await form.validate({ fields: ["email"] });

            if (result !== true) {
                return;
            }
        } catch {
            return;
        }

        if (captcha && !captchaAnswer.trim()) {
            prompt.error({ content: "请输入图片验证码。" });
            return;
        }

        setCodeSending(true);

        try {
            await sendRecoveryCode(String(form.getFieldValue("email")).trim(), captchaPayload());
            setCodeSent(true);
            prompt.success({ content: "验证码已发送。" });

            if (captcha) {
                void loadCaptcha();
            }
        } catch (error) {
            if (isRecoveryCaptchaError(error)) {
                await loadCaptcha();
                prompt.error({ content: "请输入图片验证码后重试。" });
                return;
            }

            prompt.error({ content: recoveryErrorMessage(error) });
        } finally {
            setCodeSending(false);
        }
    };

    const handleFinish = async (validateResult: boolean | object) => {
        if (validateResult !== true) {
            return;
        }

        if (step === 0) {
            setStep(1);
            return;
        }

        if (captcha && !captchaAnswer.trim()) {
            prompt.error({ content: "请输入图片验证码。" });
            return;
        }

        setSubmitting(true);

        try {
            const values = form.getFieldsValue(true) as unknown as RecoveryValues;

            await resetPassword({
                code: values.code.trim(),
                email: values.email.trim(),
                newPassword: values.newPassword,
                ...captchaPayload(),
            });
            setStep(2);
            prompt.success({ content: "密码已重置。" });
        } catch (error) {
            if (isRecoveryCaptchaError(error)) {
                await loadCaptcha();
                prompt.error({ content: "请输入图片验证码后重试。" });
                return;
            }

            prompt.error({ content: recoveryErrorMessage(error) });
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <AuthFormCard
            description={
                step === 1
                    ? "设置一个新的登录密码，至少 8 个字符。"
                    : step === 2
                      ? "现在可以使用新密码登录。"
                      : "获取邮箱验证码，确认这是你的账号。"
            }
            eyebrow="找回账号"
            title="找回密码"
        >
            <Steps
                current={step === 2 ? 3 : step}
                options={[{ title: "验证邮箱" }, { title: "设置密码" }, { title: "完成" }]}
            />

            {step === 2 ? (
                <div className="flex flex-col items-center gap-3 py-6 text-center">
                    <CheckCircleIcon className="size-12 text-[var(--td-brand-color)]" />
                    <Typography.Title className="!mb-0" level="h4">
                        密码已重置
                    </Typography.Title>
                    <Typography.Paragraph className="!mb-0 !text-sm !text-[var(--td-text-color-secondary)]">
                        现在可以使用新密码登录。
                    </Typography.Paragraph>
                    <Button href="/sign-in" theme="primary">
                        返回登录
                    </Button>
                </div>
            ) : (
                <Form
                    form={form}
                    labelAlign="top"
                    layout="vertical"
                    onSubmit={({ validateResult }) => {
                        void handleFinish(validateResult);
                    }}
                    requiredMark={false}
                >
                    {step === 0 ? (
                        <>
                            <FormItem
                                label="绑定邮箱"
                                name="email"
                                rules={[
                                    { message: "请输入绑定邮箱。", required: true },
                                    { email: true, message: "请输入有效的邮箱地址。" },
                                ]}
                            >
                                <Input
                                    autocomplete="email"
                                    placeholder="请输入绑定邮箱"
                                    prefixIcon={<MailIcon />}
                                    size="large"
                                    spellCheck={false}
                                />
                            </FormItem>
                            <div className="mb-6">
                                <div className="flex flex-col items-start gap-2 sm:flex-row">
                                    <FormItem
                                        className="!mb-0 w-full flex-1"
                                        label="邮箱验证码"
                                        name="code"
                                        rules={[
                                            { message: "请输入 6 位邮箱验证码。", required: true },
                                            { message: "请输入 6 位邮箱验证码。", pattern: /^\d{6}$/ },
                                        ]}
                                    >
                                        <Input
                                            autocomplete="one-time-code"
                                            placeholder="请输入验证码"
                                            size="large"
                                            type="tel"
                                        />
                                    </FormItem>
                                    <Button
                                        className="w-full sm:mt-7 sm:w-auto"
                                        loading={codeSending}
                                        onClick={() => {
                                            void sendCode();
                                        }}
                                        size="large"
                                        type="button"
                                    >
                                        {codeSending ? "发送中…" : codeSent ? "重新获取" : "获取验证码"}
                                    </Button>
                                </div>
                            </div>
                        </>
                    ) : (
                        <>
                            <FormItem
                                label="新密码"
                                name="newPassword"
                                rules={[
                                    { message: "请输入新密码。", required: true },
                                    { message: "新密码至少需要 8 个字符。", min: 8 },
                                    { message: "密码包含不支持的字符。", pattern: PASSWORD_PATTERN },
                                ]}
                            >
                                <Input
                                    autocomplete="new-password"
                                    placeholder="至少 8 个字符"
                                    size="large"
                                    type="password"
                                />
                            </FormItem>
                            <FormItem
                                label="确认密码"
                                name="confirmPassword"
                                rules={[
                                    { message: "请再次输入新密码。", required: true },
                                    {
                                        message: "两次输入的密码不一致。",
                                        validator: (value) => !value || form.getFieldValue("newPassword") === value,
                                    },
                                ]}
                            >
                                <Input
                                    autocomplete="new-password"
                                    placeholder="请再次输入新密码"
                                    size="large"
                                    type="password"
                                />
                            </FormItem>
                        </>
                    )}

                    {captcha ? (
                        <CaptchaField
                            answer={captchaAnswer}
                            challenge={captcha}
                            onAnswerChange={setCaptchaAnswer}
                            onRefresh={() => {
                                void loadCaptcha();
                            }}
                            refreshing={captchaLoading}
                        />
                    ) : null}

                    <div className="mb-5 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-[var(--td-text-color-secondary)]">
                        <div className="flex items-center gap-1.5">
                            <SecuredIcon className="text-[var(--td-brand-color)]" size={14} />
                            {step === 1 ? "至少 8 个字符" : "验证码仅用于身份确认"}
                        </div>
                        <div className="flex items-center gap-1.5">
                            <SecuredIcon className="text-[var(--td-brand-color)]" size={14} />
                            {step === 1 ? "请勿与其他站点共用密码" : "不会公开邮箱状态"}
                        </div>
                    </div>

                    <Button block loading={submitting || captchaLoading} size="large" theme="primary" type="submit">
                        {step === 1 ? "重置密码" : "下一步"}
                    </Button>
                </Form>
            )}

            <Link
                className="inline-flex items-center justify-center gap-1 text-xs font-medium text-[var(--td-text-color-secondary)] transition-colors hover:text-[var(--td-text-color-primary)]"
                href="/sign-in"
            >
                <ArrowLeftIcon />
                返回登录
            </Link>
        </AuthFormCard>
    );
}
