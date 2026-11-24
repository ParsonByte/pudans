"use client";

import { useState } from "react";

import Link from "next/link";

import { NewLark } from "@icon-park/react";
import { useQuery } from "@tanstack/react-query";
import { FingerprintIcon, LinkIcon, LockOnIcon, LoginIcon, SecuredIcon, UserIcon } from "tdesign-icons-react";
import { Button, Divider, Form, Input } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import {
    type LoginCaptcha,
    completeLogin,
    getLoginProviders,
    getLoginStatus,
    providerWithUrl,
    signIn,
    signInErrorMessage,
    signInWithPasskey,
} from "../../../composables/_auth-login";
import { LoginCaptchaDialog } from "./login-captcha-dialog";

const { FormItem } = Form;

type LoginFormValues = {
    username: string;
    password: string;
};

type LoginFormProps = {
    redirectTo?: string;
    onTwoFactorRequired: (username?: string) => void;
};

export function LoginForm({ onTwoFactorRequired, redirectTo }: LoginFormProps) {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const [passkeyLoading, setPasskeyLoading] = useState(false);
    const [pendingCredentials, setPendingCredentials] = useState<LoginFormValues>();
    const [captchaOpen, setCaptchaOpen] = useState(false);
    const providersQuery = useQuery({
        queryKey: ["login-providers"],
        queryFn: getLoginProviders,
        retry: false,
        staleTime: 5 * 60 * 1000,
    });
    const loginStatusQuery = useQuery({
        queryKey: ["login-status"],
        queryFn: getLoginStatus,
        retry: false,
        staleTime: 0,
    });
    const providers = providersQuery.data ?? [];
    const loginCaptchaEnabled = loginStatusQuery.data?.loginCaptchaEnabled === true;
    const feishuUrl = providers.find((provider) => provider.name?.toLowerCase() === "feishu")?.authenticationUrl;
    const otherProviders = providers
        .filter((provider) => provider.name?.toLowerCase() !== "feishu")
        .filter(providerWithUrl);

    const submitLogin = async (values: LoginFormValues, captcha?: LoginCaptcha) => {
        const result = await signIn(values.username.trim(), values.password, captcha);

        if (result.kind === "two-factor") {
            onTwoFactorRequired(result.username);
            return;
        }

        completeLogin(result.session, redirectTo, () => prompt.error({ content: "当前账号无权访问控制台。" }));
    };

    const handleFinish = async (validateResult: boolean | object) => {
        if (validateResult !== true) {
            return;
        }

        if (submitting) {
            return;
        }

        const values = form.getFieldsValue(true) as unknown as LoginFormValues;

        if (loginCaptchaEnabled) {
            setPendingCredentials(values);
            setCaptchaOpen(true);
            return;
        }

        setSubmitting(true);

        try {
            await submitLogin(values);
        } catch (error) {
            const status = await loginStatusQuery.refetch();

            if (status.data?.loginCaptchaEnabled) {
                setPendingCredentials(values);
                setCaptchaOpen(true);
                return;
            }

            prompt.error({ content: signInErrorMessage(error) });
        } finally {
            setSubmitting(false);
        }
    };

    const handleCaptchaSubmit = async (captcha: LoginCaptcha) => {
        if (!pendingCredentials) {
            return;
        }

        await submitLogin(pendingCredentials, captcha);
        setCaptchaOpen(false);
        setPendingCredentials(undefined);
    };

    const closeCaptcha = () => {
        setCaptchaOpen(false);
        setPendingCredentials(undefined);
    };

    const handlePasskey = async () => {
        if (typeof window === "undefined" || !window.PublicKeyCredential) {
            prompt.error({ content: "当前浏览器不支持通行密钥登录。" });
            return;
        }

        setPasskeyLoading(true);

        try {
            const username = String(form.getFieldValue("username") ?? "").trim();
            const result = await signInWithPasskey(username || undefined);

            if (result.kind === "two-factor") {
                onTwoFactorRequired(result.username);
                return;
            }

            completeLogin(result.session, redirectTo, () => prompt.error({ content: "当前账号无权访问控制台。" }));
        } catch (error) {
            prompt.error({ content: signInErrorMessage(error) });
        } finally {
            setPasskeyLoading(false);
        }
    };

    return (
        <>
            <Form
                form={form}
                labelAlign="top"
                layout="vertical"
                onSubmit={({ validateResult }) => {
                    void handleFinish(validateResult);
                }}
                requiredMark={false}
            >
                <FormItem
                    label="用户名或邮箱"
                    name="username"
                    rules={[{ message: "请输入用户名或邮箱。", required: true, whitespace: true }]}
                    style={{ marginBottom: 8 }}
                >
                    <Input
                        aria-label="用户名或邮箱"
                        autocomplete="username"
                        placeholder="请输入用户名或邮箱"
                        prefixIcon={<UserIcon />}
                        size="large"
                        spellCheck={false}
                    />
                </FormItem>
                <FormItem
                    label="密码"
                    name="password"
                    rules={[{ message: "请输入密码。", required: true }]}
                    style={{ marginBottom: 4 }}
                >
                    <Input
                        aria-label="密码"
                        autocomplete="current-password"
                        placeholder="请输入密码"
                        prefixIcon={<LockOnIcon />}
                        size="large"
                        type="password"
                    />
                </FormItem>
                <div className="mb-4 flex justify-end">
                    <Link
                        className="text-xs font-medium text-[var(--td-text-color-link)] hover:opacity-80"
                        href="/sign-in?routes=forgot-password"
                    >
                        忘记密码？
                    </Link>
                </div>
                <div className="mb-5 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-[var(--td-text-color-secondary)]">
                    <div className="flex items-center gap-1.5">
                        <SecuredIcon className="text-[var(--td-brand-color)]" size={14} />
                        登录状态将安全保留
                    </div>
                    <div className="flex items-center gap-1.5">
                        <SecuredIcon className="text-[var(--td-brand-color)]" size={14} />
                        新设备登录会提醒你
                    </div>
                </div>
                <Button block icon={<LoginIcon />} loading={submitting} size="large" theme="primary" type="submit">
                    登录并继续
                </Button>

                <Divider className="my-5 text-xs">或使用其他方式</Divider>

                <div className="flex flex-wrap gap-2">
                    <Button
                        className="flex-1"
                        disabled={submitting}
                        icon={<FingerprintIcon />}
                        loading={passkeyLoading}
                        onClick={() => {
                            void handlePasskey();
                        }}
                        theme="default"
                        variant="outline"
                    >
                        通行密钥
                    </Button>
                    <Button
                        aria-label="飞书"
                        className="w-20 shrink-0"
                        icon={<NewLark size={20} />}
                        onClick={() => {
                            if (feishuUrl) {
                                window.location.assign(feishuUrl);
                                return;
                            }

                            prompt.error({ content: "飞书未配置" });
                        }}
                        theme="default"
                        title="飞书"
                        variant="outline"
                    />
                    {otherProviders.map((provider) => (
                        <Button
                            aria-label={provider.displayName ?? provider.name ?? "外部登录"}
                            className="w-20 shrink-0"
                            icon={<LinkIcon />}
                            key={`${provider.name}-${provider.authenticationUrl}`}
                            onClick={() => window.location.assign(provider.authenticationUrl)}
                            theme="default"
                            title={provider.displayName ?? provider.name ?? "外部登录"}
                            variant="outline"
                        />
                    ))}
                </div>
            </Form>
            {captchaOpen ? <LoginCaptchaDialog onCancel={closeCaptcha} onSubmit={handleCaptchaSubmit} /> : null}
        </>
    );
}
