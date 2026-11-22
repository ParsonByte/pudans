"use client";

import { useEffect, useState } from "react";

import Link from "next/link";

import type { CaptchaChallenge } from "@pudans-dev/api-client";
import { App, Button, Flex, Form, Input, Steps, Typography } from "antd";
import { ArrowLeft, CheckCircle2, Mail, ShieldCheck } from "lucide-react";

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

type RecoveryValues = {
    email: string;
    code: string;
    newPassword: string;
    confirmPassword: string;
};

const PASSWORD_PATTERN = /^[A-Za-z0-9!@#$%^&*.?]+$/;

export function ForgotPasswordForm() {
    const { message } = App.useApp();
    const [form] = Form.useForm<RecoveryValues>();
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
            message.error("验证码加载失败，请稍后重试。");
        } finally {
            setCaptchaLoading(false);
        }
    };

    const captchaPayload = () =>
        captcha ? { captchaChallengeId: captcha.challengeId, captchaAnswer: captchaAnswer.trim() } : {};

    const sendCode = async () => {
        try {
            await form.validateFields(["email"]);
        } catch {
            return;
        }

        if (captcha && !captchaAnswer.trim()) {
            message.error("请输入图片验证码。");
            return;
        }

        setCodeSending(true);

        try {
            await sendRecoveryCode(String(form.getFieldValue("email")).trim(), captchaPayload());
            setCodeSent(true);
            message.success("验证码已发送。");

            if (captcha) {
                void loadCaptcha();
            }
        } catch (error) {
            if (isRecoveryCaptchaError(error)) {
                await loadCaptcha();
                message.error("请输入图片验证码后重试。");
                return;
            }

            message.error(recoveryErrorMessage(error));
        } finally {
            setCodeSending(false);
        }
    };

    const handleFinish = async (values: RecoveryValues) => {
        if (step === 0) {
            setStep(1);
            return;
        }

        if (captcha && !captchaAnswer.trim()) {
            message.error("请输入图片验证码。");
            return;
        }

        setSubmitting(true);

        try {
            await resetPassword({
                code: values.code.trim(),
                email: values.email.trim(),
                newPassword: values.newPassword,
                ...captchaPayload(),
            });
            setStep(2);
            message.success("密码已重置。");
        } catch (error) {
            if (isRecoveryCaptchaError(error)) {
                await loadCaptcha();
                message.error("请输入图片验证码后重试。");
                return;
            }

            message.error(recoveryErrorMessage(error));
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
                current={step}
                items={[{ title: "验证邮箱" }, { title: "设置密码" }, { title: "完成" }]}
                responsive={false}
                size="small"
            />

            {step === 2 ? (
                <Flex align="center" className="py-6 text-center" gap={12} vertical>
                    <CheckCircle2 className="size-12 text-blue-500" />
                    <Typography.Title className="!mb-0" level={4}>
                        密码已重置
                    </Typography.Title>
                    <Typography.Paragraph className="!mb-0" type="secondary">
                        现在可以使用新密码登录。
                    </Typography.Paragraph>
                    <Button href="/sign-in" type="primary">
                        返回登录
                    </Button>
                </Flex>
            ) : (
                <Form<RecoveryValues>
                    form={form}
                    layout="vertical"
                    onFinish={handleFinish}
                    requiredMark={false}
                    size="large"
                >
                    {step === 0 ? (
                        <>
                            <Form.Item
                                label="绑定邮箱"
                                name="email"
                                rules={[
                                    { message: "请输入绑定邮箱。", required: true },
                                    { message: "请输入有效的邮箱地址。", type: "email" },
                                ]}
                            >
                                <Input
                                    autoCapitalize="off"
                                    autoComplete="email"
                                    placeholder="请输入绑定邮箱"
                                    prefix={<Mail className="size-4 text-neutral-400" />}
                                    spellCheck={false}
                                />
                            </Form.Item>
                            <div className="mb-6">
                                <label className="mb-2 block text-sm" htmlFor="recovery-code">
                                    邮箱验证码
                                </label>
                                <Flex align="flex-start" gap={8}>
                                    <Form.Item
                                        className="!mb-0 flex-1"
                                        name="code"
                                        rules={[
                                            { message: "请输入 6 位邮箱验证码。", required: true },
                                            { message: "请输入 6 位邮箱验证码。", pattern: /^\d{6}$/ },
                                        ]}
                                    >
                                        <Input
                                            autoComplete="one-time-code"
                                            id="recovery-code"
                                            inputMode="numeric"
                                            placeholder="请输入验证码"
                                            size="large"
                                        />
                                    </Form.Item>
                                    <Button
                                        htmlType="button"
                                        loading={codeSending}
                                        onClick={() => {
                                            void sendCode();
                                        }}
                                        size="large"
                                        type="default"
                                    >
                                        {codeSending ? "发送中…" : codeSent ? "重新获取" : "获取验证码"}
                                    </Button>
                                </Flex>
                            </div>
                        </>
                    ) : (
                        <>
                            <Form.Item
                                label="新密码"
                                name="newPassword"
                                rules={[
                                    { message: "请输入新密码。", required: true },
                                    { message: "新密码至少需要 8 个字符。", min: 8 },
                                    { message: "密码包含不支持的字符。", pattern: PASSWORD_PATTERN },
                                ]}
                            >
                                <Input.Password
                                    autoCapitalize="off"
                                    autoComplete="new-password"
                                    placeholder="至少 8 个字符"
                                />
                            </Form.Item>
                            <Form.Item
                                dependencies={["newPassword"]}
                                label="确认密码"
                                name="confirmPassword"
                                rules={[
                                    { message: "请再次输入新密码。", required: true },
                                    ({ getFieldValue }) => ({
                                        validator(_, value) {
                                            return !value || getFieldValue("newPassword") === value
                                                ? Promise.resolve()
                                                : Promise.reject(new Error("两次输入的密码不一致。"));
                                        },
                                    }),
                                ]}
                            >
                                <Input.Password autoComplete="new-password" placeholder="请再次输入新密码" />
                            </Form.Item>
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

                    <div className="mb-5 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-neutral-500 dark:text-neutral-400">
                        <div className="flex items-center gap-1.5">
                            <ShieldCheck className="size-3.5 text-blue-500" />
                            {step === 1 ? "至少 8 个字符" : "验证码仅用于身份确认"}
                        </div>
                        <div className="flex items-center gap-1.5">
                            <ShieldCheck className="size-3.5 text-blue-500" />
                            {step === 1 ? "请勿与其他站点共用密码" : "不会公开邮箱状态"}
                        </div>
                    </div>

                    <Button block htmlType="submit" loading={submitting || captchaLoading} type="primary">
                        {step === 1 ? "重置密码" : "下一步"}
                    </Button>
                </Form>
            )}

            <Link
                className="inline-flex items-center justify-center gap-1 text-xs font-medium text-neutral-500 transition-colors hover:text-neutral-900 dark:text-neutral-400 dark:hover:text-neutral-100"
                href="/sign-in"
            >
                <ArrowLeft className="size-3.5" />
                返回登录
            </Link>
        </AuthFormCard>
    );
}
