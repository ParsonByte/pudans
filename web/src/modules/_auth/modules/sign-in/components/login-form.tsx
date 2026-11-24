"use client";

import { useState } from "react";

import Link from "next/link";

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

function FeishuIcon({ size = 20 }: { size?: number }) {
    return (
        <svg aria-hidden="true" fill="none" height={size} viewBox="0 0 48 48" width={size}>
            <path
                d="M17 29C21 29 25 26.9339 28 23.4065C36 14 41.4242 16.8166 44 17.9998C38.5 20.9998 40.5 29.6233 33 35.9998C28.382 39.9259 23.4945 41.014 19 41C12.5231 40.9799 6.86226 37.7637 4 35.4063V16.9998"
                stroke="currentColor"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="4"
            />
            <path
                d="M5.64808 15.8669C5.02231 14.9567 3.77715 14.7261 2.86694 15.3519C1.95673 15.9777 1.72615 17.2228 2.35192 18.1331L5.64808 15.8669ZM36.0021 35.7309C36.958 35.1774 37.2843 33.9539 36.7309 32.9979C36.1774 32.042 34.9539 31.7157 33.9979 32.2691L36.0021 35.7309ZM2.35192 18.1331C5.2435 22.339 10.7992 28.144 16.8865 32.2239C19.9345 34.2667 23.217 35.946 26.449 36.7324C29.6946 37.522 33.0451 37.4428 36.0021 35.7309L33.9979 32.2691C32.2049 33.3072 29.9929 33.478 27.3947 32.8458C24.783 32.2103 21.9405 30.7958 19.1135 28.9011C13.4508 25.106 8.2565 19.661 5.64808 15.8669L2.35192 18.1331Z"
                fill="currentColor"
            />
            <path
                d="M33.5947 17C32.84 14.7027 30.8551 9.94054 27.5947 7H11.5947C15.2174 10.6757 23.0002 16 27.0002 24"
                stroke="currentColor"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="4"
            />
        </svg>
    );
}

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
                        icon={<FeishuIcon />}
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
